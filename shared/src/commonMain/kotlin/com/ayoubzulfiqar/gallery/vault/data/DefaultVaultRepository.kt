package com.ayoubzulfiqar.gallery.vault.data

import com.ayoubzulfiqar.gallery.core.crypto.Aead
import com.ayoubzulfiqar.gallery.core.crypto.AeadException
import com.ayoubzulfiqar.gallery.core.crypto.AesGcmCipher
import com.ayoubzulfiqar.gallery.core.crypto.Pbkdf2
import com.ayoubzulfiqar.gallery.core.crypto.StreamingAead
import com.ayoubzulfiqar.gallery.core.crypto.platformAead
import com.ayoubzulfiqar.gallery.core.crypto.secureRandomBytes
import com.ayoubzulfiqar.gallery.core.crypto.toHex
import com.ayoubzulfiqar.gallery.core.io.BinaryReader
import com.ayoubzulfiqar.gallery.core.io.BinaryWriter
import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.core.io.ContentTooLargeException
import com.ayoubzulfiqar.gallery.core.io.MalformedDataException
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.domain.model.PinPolicy
import com.ayoubzulfiqar.gallery.domain.model.UnlockResult
import com.ayoubzulfiqar.gallery.domain.model.VaultError
import com.ayoubzulfiqar.gallery.domain.model.VaultItem
import com.ayoubzulfiqar.gallery.domain.model.VaultState
import com.ayoubzulfiqar.gallery.domain.repository.VaultRepository
import com.ayoubzulfiqar.gallery.vault.storage.DeviceKeyProtector
import com.ayoubzulfiqar.gallery.vault.storage.PassthroughDeviceKeyProtector
import com.ayoubzulfiqar.gallery.vault.storage.VaultStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.ayoubzulfiqar.gallery.core.time.currentTimeMillis

data class VaultConfig(
    /** PBKDF2-HMAC-SHA256 work factor for new/changed PINs (stored per vault, so it can be raised later). */
    val pbkdf2Iterations: Int = 150_000,
    /** Wrong PINs tolerated before time-based lockout kicks in. */
    val freeAttempts: Int = 5,
    val segmentSize: Int = StreamingAead.DEFAULT_SEGMENT_SIZE
)

/**
 * Persistent, envelope-encrypted vault.
 *
 * Key hierarchy:
 * - A random 256-bit data encryption key (DEK) encrypts every item (streaming AES-GCM, per-file
 *   HKDF subkeys), every thumbnail and the item index.
 * - The DEK is stored wrapped by a key-encryption key derived from the PIN with PBKDF2 and a
 *   random salt. Changing the PIN only re-wraps the DEK.
 * - The whole header is additionally sealed by [deviceKeyProtector] (Android Keystore), so the
 *   files are useless off-device and an attacker cannot brute-force the PIN offline.
 * - Failed attempts are persisted and enforce exponential back-off across restarts.
 *
 * Concurrency: all session mutations happen under [mutex]; bulk encryption runs outside it on a
 * private copy of the key so that locking is never blocked by a large transfer.
 */
class DefaultVaultRepository(
    private val storage: VaultStorage,
    private val deviceKeyProtector: DeviceKeyProtector = PassthroughDeviceKeyProtector(),
    private val aead: Aead = platformAead(),
    private val config: VaultConfig = VaultConfig(),
    private val clock: () -> Long = ::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : VaultRepository {

    private class Session(val dek: ByteArray, var entries: List<VaultItem>)

    private val mutex = Mutex()
    private val cipher = AesGcmCipher(aead)
    private val streaming = StreamingAead(aead, config.segmentSize)
    private var session: Session? = null

    private val _state = MutableStateFlow<VaultState>(VaultState.Loading)
    override val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    override val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    override val isDeviceBound: Boolean get() = deviceKeyProtector.isHardwareBacked

    override suspend fun initialize() = withContext(dispatcher) {
        mutex.withLock { refreshLockedState() }
    }

    override suspend fun setUp(pin: String) = withContext(dispatcher) {
        val validation = PinPolicy.validateNewPin(pin)
        if (validation is PinPolicy.Validation.Invalid) throw VaultError.InvalidPin(validation.reason)
        mutex.withLock {
            if (storage.exists(HEADER)) throw VaultError.StorageFailure("A vault already exists")
            val dek = secureRandomBytes(Aead.KEY_SIZE)
            try {
                writeHeader(pin, dek)
                saveLockout(LockoutState(0, 0))
                persistIndex(dek, emptyList())
            } catch (e: Throwable) {
                dek.fill(0)
                storage.deleteAll()
                throw if (e is VaultError || e is CancellationException) e else VaultError.StorageFailure("Could not create the vault", e)
            }
            session = Session(dek, emptyList())
            _items.value = emptyList()
            _state.value = VaultState.Unlocked
        }
    }

    override suspend fun unlock(pin: String): UnlockResult = withContext(dispatcher) {
        mutex.withLock {
            if (session != null) return@withLock UnlockResult.Success
            val header = when (val loaded = loadHeaderOrError()) {
                is HeaderLoad.Missing -> return@withLock UnlockResult.Failure("The vault has not been set up")
                is HeaderLoad.Error -> return@withLock UnlockResult.Failure(loaded.reason)
                is HeaderLoad.Loaded -> loaded.header
            }
            val dek = when (val verification = verifyPin(header, pin)) {
                is PinVerification.Verified -> verification.dek
                is PinVerification.Rejected -> return@withLock verification.result
            }
            val entries = try {
                loadAndReconcileIndex(dek)
            } catch (e: VaultError) {
                dek.fill(0)
                return@withLock UnlockResult.Failure(e.message ?: "The vault index is damaged")
            }
            session = Session(dek, entries)
            _items.value = entries
            _state.value = VaultState.Unlocked
            UnlockResult.Success
        }
    }

    override suspend fun lock() {
        // Hide content immediately, then wipe keys once no index write is in flight.
        _items.value = emptyList()
        mutex.withLock {
            session?.dek?.fill(0)
            session = null
            refreshLockedState()
        }
    }

    override suspend fun changePin(currentPin: String, newPin: String): UnlockResult = withContext(dispatcher) {
        val validation = PinPolicy.validateNewPin(newPin)
        if (validation is PinPolicy.Validation.Invalid) throw VaultError.InvalidPin(validation.reason)
        mutex.withLock {
            val header = when (val loaded = loadHeaderOrError()) {
                is HeaderLoad.Missing -> throw VaultError.NotSetUp()
                is HeaderLoad.Error -> return@withLock UnlockResult.Failure(loaded.reason)
                is HeaderLoad.Loaded -> loaded.header
            }
            val dek = when (val verification = verifyPin(header, currentPin)) {
                is PinVerification.Verified -> verification.dek
                is PinVerification.Rejected -> return@withLock verification.result
            }
            try {
                writeHeader(newPin, dek)
            } finally {
                dek.fill(0) // always a fresh unwrap, never the live session key
            }
            if (session == null) refreshLockedState()
            UnlockResult.Success
        }
    }

    override suspend fun reset() = withContext(dispatcher) {
        mutex.withLock {
            session?.dek?.fill(0)
            session = null
            storage.deleteAll()
            runCatching { deviceKeyProtector.destroy() }
            _items.value = emptyList()
            _state.value = VaultState.NotSetUp
        }
    }

    override suspend fun add(item: MediaItem, content: ByteSource, thumbnail: ByteArray?): VaultItem = withContext(dispatcher) {
        val key = mutex.withLock { requireSession().dek.copyOf() }
        val id = secureRandomBytes(ID_BYTES).toHex()
        val tempBlob = "$BLOBS/$id$TEMP_SUFFIX"
        try {
            val plainSize = storage.openSink(tempBlob).use { sink ->
                streaming.encrypt(key, blobAad(id), content, sink)
            }
            storage.rename(tempBlob, blobPath(id))
            val hasThumbnail = thumbnail != null && thumbnail.isNotEmpty()
            if (hasThumbnail) storage.writeAtomically(thumbPath(id), cipher.encrypt(thumbnail!!, key, thumbAad(id)))
            val vaultItem = VaultItem(
                id = id,
                originalId = item.id,
                displayName = item.displayName,
                mimeType = item.mimeType,
                mediaType = item.mediaType,
                dateTakenMillis = item.dateTakenMillis,
                width = item.width,
                height = item.height,
                orientation = item.orientation,
                sizeBytes = plainSize,
                durationMs = item.durationMs,
                bucketName = item.bucketName,
                relativePath = item.relativePath,
                vaultedAtMillis = clock(),
                hasThumbnail = hasThumbnail
            )
            mutex.withLock {
                val current = requireSession()
                if (!current.dek.contentEquals(key)) throw VaultError.VaultLocked()
                val updated = current.entries + vaultItem
                persistIndex(current.dek, updated)
                current.entries = updated
                _items.value = updated
            }
            vaultItem
        } catch (e: Throwable) {
            deleteQuietly(tempBlob, blobPath(id), thumbPath(id))
            throw when (e) {
                is VaultError, is CancellationException -> e
                else -> VaultError.StorageFailure("Could not encrypt ${item.displayName}", e)
            }
        } finally {
            key.fill(0)
        }
    }

    override suspend fun remove(ids: Collection<String>) = withContext(dispatcher) {
        if (ids.isEmpty()) return@withContext
        val targets = ids.toSet()
        mutex.withLock {
            val current = requireSession()
            // Files first: a crash afterwards leaves index entries without blobs, which are pruned
            // on the next unlock, instead of resurrecting deleted items.
            current.entries.filter { it.id in targets }.forEach { deleteQuietly(blobPath(it.id), thumbPath(it.id)) }
            val updated = current.entries.filterNot { it.id in targets }
            persistIndex(current.dek, updated)
            current.entries = updated
            _items.value = updated
        }
    }

    override suspend fun export(id: String, sink: ByteSink) = withContext(dispatcher) {
        val key = mutex.withLock {
            val current = requireSession()
            if (current.entries.none { it.id == id }) throw VaultError.ItemNotFound(id)
            current.dek.copyOf()
        }
        try {
            val source = try {
                storage.openSource(blobPath(id))
            } catch (e: Exception) {
                throw VaultError.StorageFailure("Encrypted file for this item is missing", e)
            }
            source.use { streaming.decrypt(key, blobAad(id), it, sink) }
            Unit
        } catch (e: AeadException) {
            throw VaultError.IntegrityCompromised(cause = e)
        } finally {
            key.fill(0)
        }
    }

    override suspend fun readThumbnail(id: String): ByteArray? = withContext(dispatcher) {
        val key = mutex.withLock { session?.dek?.copyOf() } ?: return@withContext null
        try {
            val data = storage.read(thumbPath(id)) ?: return@withContext null
            cipher.decrypt(data, key, thumbAad(id))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            key.fill(0)
        }
    }

    override suspend fun readContent(id: String, maxBytes: Long): ByteArray {
        val entry = items.value.firstOrNull { it.id == id } ?: throw VaultError.ItemNotFound(id)
        if (entry.sizeBytes > maxBytes) throw ContentTooLargeException(maxBytes)
        val sink = ByteArraySink(entry.sizeBytes.toInt().coerceAtLeast(16))
        export(id, sink)
        return sink.toByteArray()
    }

    // region header & PIN

    private sealed interface HeaderLoad {
        data object Missing : HeaderLoad
        data class Error(val reason: String) : HeaderLoad
        data class Loaded(val header: VaultHeader) : HeaderLoad
    }

    private sealed interface PinVerification {
        class Verified(val dek: ByteArray) : PinVerification
        class Rejected(val result: UnlockResult) : PinVerification
    }

    private fun loadHeaderOrError(): HeaderLoad {
        val protectedBytes = storage.read(HEADER) ?: return HeaderLoad.Missing
        val plain = try {
            deviceKeyProtector.unprotect(protectedBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return HeaderLoad.Error("This vault was created on another device or its device key was reset")
        }
        return try {
            HeaderLoad.Loaded(VaultHeader.decode(plain))
        } catch (_: MalformedDataException) {
            HeaderLoad.Error("The vault header is damaged")
        }
    }

    private fun verifyPin(header: VaultHeader, pin: String): PinVerification {
        val lockout = loadLockout()
        val now = clock()
        if (lockout.lockedUntilMillis > now) {
            return PinVerification.Rejected(UnlockResult.LockedOut(lockout.lockedUntilMillis))
        }
        val dek = if (PinPolicy.isWellFormed(pin)) unwrapDek(header, pin) else null
        if (dek == null) {
            val attempts = lockout.failedAttempts + 1
            val until = if (attempts >= config.freeAttempts) now + backoffMillis(attempts - config.freeAttempts) else 0L
            saveLockout(LockoutState(attempts, until))
            if (session == null) _state.value = VaultState.Locked(header.pinLength, attempts, until)
            val result = if (until > 0) {
                UnlockResult.LockedOut(until)
            } else {
                UnlockResult.WrongPin(config.freeAttempts - attempts)
            }
            return PinVerification.Rejected(result)
        }
        if (lockout.failedAttempts != 0 || lockout.lockedUntilMillis != 0L) saveLockout(LockoutState(0, 0))
        return PinVerification.Verified(dek)
    }

    private fun unwrapDek(header: VaultHeader, pin: String): ByteArray? {
        val kek = Pbkdf2.hmacSha256(pin.encodeToByteArray(), header.salt, header.iterations, Aead.KEY_SIZE)
        return try {
            aead.open(kek, header.dekNonce, header.wrappedDek, AAD_DEK)
        } catch (_: AeadException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    private fun writeHeader(pin: String, dek: ByteArray) {
        val salt = secureRandomBytes(SALT_BYTES)
        val kek = Pbkdf2.hmacSha256(pin.encodeToByteArray(), salt, config.pbkdf2Iterations, Aead.KEY_SIZE)
        try {
            val nonce = secureRandomBytes(Aead.NONCE_SIZE)
            val wrapped = aead.seal(kek, nonce, dek, AAD_DEK)
            val header = VaultHeader(config.pbkdf2Iterations, pin.length, salt, nonce, wrapped)
            storage.writeAtomically(HEADER, deviceKeyProtector.protect(header.encode()))
        } finally {
            kek.fill(0)
        }
    }

    private fun refreshLockedState() {
        if (session != null) {
            _state.value = VaultState.Unlocked
            return
        }
        _state.value = when (val loaded = loadHeaderOrError()) {
            is HeaderLoad.Missing -> VaultState.NotSetUp
            is HeaderLoad.Error -> VaultState.Unavailable(loaded.reason)
            is HeaderLoad.Loaded -> {
                val lockout = loadLockout()
                VaultState.Locked(loaded.header.pinLength, lockout.failedAttempts, lockout.lockedUntilMillis)
            }
        }
    }

    private fun backoffMillis(excessAttempts: Int): Long {
        val schedule = longArrayOf(30_000, 60_000, 120_000, 300_000, 900_000, 1_800_000, 3_600_000)
        return schedule[excessAttempts.coerceIn(0, schedule.lastIndex)]
    }

    private data class LockoutState(val failedAttempts: Int, val lockedUntilMillis: Long)

    private fun loadLockout(): LockoutState {
        val raw = storage.read(LOCKOUT) ?: return LockoutState(0, 0)
        return try {
            val reader = BinaryReader(deviceKeyProtector.unprotect(raw))
            LockoutState(reader.int(), reader.long())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A tampered or unreadable lockout record must never grant extra attempts.
            LockoutState(config.freeAttempts, clock() + backoffMillis(0))
        }
    }

    private fun saveLockout(state: LockoutState) {
        val bytes = BinaryWriter().int(state.failedAttempts).long(state.lockedUntilMillis).toByteArray()
        storage.writeAtomically(LOCKOUT, deviceKeyProtector.protect(bytes))
    }

    // endregion

    // region index

    private fun requireSession(): Session = session ?: run {
        if (_state.value is VaultState.NotSetUp) throw VaultError.NotSetUp()
        throw VaultError.VaultLocked()
    }

    private fun persistIndex(dek: ByteArray, entries: List<VaultItem>) {
        val writer = BinaryWriter(entries.size * 160 + 16).int(INDEX_VERSION).int(entries.size)
        for (e in entries) {
            writer.string(e.id).string(e.originalId).string(e.displayName).string(e.mimeType)
                .string(e.mediaType.name).long(e.dateTakenMillis).int(e.width).int(e.height)
                .int(e.orientation).long(e.sizeBytes).long(e.durationMs ?: -1L)
                .nullableString(e.bucketName).nullableString(e.relativePath)
                .long(e.vaultedAtMillis).boolean(e.hasThumbnail)
        }
        val encrypted = cipher.encrypt(writer.toByteArray(), dek, AAD_INDEX)
        if (storage.exists(INDEX)) storage.rename(INDEX, INDEX_BACKUP)
        storage.writeAtomically(INDEX, encrypted)
    }

    private fun decodeIndex(dek: ByteArray, data: ByteArray): List<VaultItem> {
        val reader = BinaryReader(cipher.decrypt(data, dek, AAD_INDEX))
        val version = reader.int()
        if (version != INDEX_VERSION) throw MalformedDataException("Unsupported index version $version")
        val count = reader.int()
        if (count < 0 || count > 1_000_000) throw MalformedDataException("Invalid entry count")
        return List(count) {
            VaultItem(
                id = reader.string(),
                originalId = reader.string(),
                displayName = reader.string(),
                mimeType = reader.string(),
                mediaType = reader.string().let { name -> MediaType.entries.firstOrNull { it.name == name } ?: MediaType.IMAGE },
                dateTakenMillis = reader.long(),
                width = reader.int(),
                height = reader.int(),
                orientation = reader.int(),
                sizeBytes = reader.long(),
                durationMs = reader.long().takeIf { it >= 0 },
                bucketName = reader.nullableString(),
                relativePath = reader.nullableString(),
                vaultedAtMillis = reader.long(),
                hasThumbnail = reader.boolean()
            )
        }
    }

    /**
     * Loads the index (falling back to the backup copy), prunes entries whose encrypted file is
     * gone and adopts orphaned encrypted files left behind by an interrupted transfer, so user data
     * is never silently dropped.
     */
    private fun loadAndReconcileIndex(dek: ByteArray): List<VaultItem> {
        var loaded: List<VaultItem>? = null
        for (path in listOf(INDEX, INDEX_BACKUP)) {
            val data = storage.read(path) ?: continue
            loaded = try {
                decodeIndex(dek, data)
            } catch (_: AeadException) {
                null
            } catch (_: MalformedDataException) {
                null
            }
            if (loaded != null) break
        }
        // When both index copies are unreadable the encrypted files are still adopted below, so a
        // damaged index degrades to generic file names instead of losing content.
        val entries = loaded ?: emptyList()

        val blobFiles = storage.list(BLOBS)
        blobFiles.filter { it.endsWith(TEMP_SUFFIX) }.forEach { deleteQuietly("$BLOBS/$it") }
        val presentIds = blobFiles.filterNot { it.endsWith(TEMP_SUFFIX) }.toSet()
        val kept = entries.filter { it.id in presentIds }
        val knownIds = kept.mapTo(HashSet()) { it.id }
        val recovered = presentIds.filter { it !in knownIds }.mapNotNull { recoverOrphan(dek, it) }
        val reconciled = kept + recovered
        if (reconciled != entries || !storage.exists(INDEX)) persistIndex(dek, reconciled)
        storage.list(THUMBS).filter { it !in presentIds }.forEach { deleteQuietly("$THUMBS/$it") }
        return reconciled
    }

    private fun recoverOrphan(dek: ByteArray, id: String): VaultItem? {
        val sniffer = SniffingSink()
        try {
            storage.openSource(blobPath(id)).use { streaming.decrypt(dek, blobAad(id), it, sniffer) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null // Not ours or corrupted beyond use; leave the file untouched for forensics.
        }
        val (mime, extension) = sniffer.detectType()
        return VaultItem(
            id = id,
            originalId = "",
            displayName = "Recovered_${id.take(8)}.$extension",
            mimeType = mime,
            mediaType = MediaType.fromMimeType(mime),
            dateTakenMillis = clock(),
            width = 0,
            height = 0,
            orientation = 0,
            sizeBytes = sniffer.total,
            durationMs = null,
            bucketName = null,
            relativePath = null,
            vaultedAtMillis = clock(),
            hasThumbnail = storage.exists(thumbPath(id))
        )
    }

    private class SniffingSink : ByteSink {
        val head = ByteArray(16)
        var total = 0L

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (total < head.size) {
                val count = minOf(length, head.size - total.toInt())
                buffer.copyInto(head, total.toInt(), offset, offset + count)
            }
            total += length
        }

        override fun close() {}

        fun detectType(): Pair<String, String> {
            fun at(i: Int) = head[i].toInt() and 0xFF
            fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
            return when {
                at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg" to "jpg"
                at(0) == 0x89 && ascii(1, "PNG") -> "image/png" to "png"
                ascii(0, "GIF8") -> "image/gif" to "gif"
                ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp" to "webp"
                ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1")) -> "image/heic" to "heic"
                ascii(4, "ftyp") -> "video/mp4" to "mp4"
                at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3 -> "video/webm" to "webm"
                else -> "application/octet-stream" to "bin"
            }
        }
    }

    // endregion

    private fun deleteQuietly(vararg paths: String) {
        for (path in paths) runCatching { storage.delete(path) }
    }

    internal class VaultHeader(
        val iterations: Int,
        val pinLength: Int,
        val salt: ByteArray,
        val dekNonce: ByteArray,
        val wrappedDek: ByteArray
    ) {
        fun encode(): ByteArray = BinaryWriter(128)
            .raw(MAGIC).byte(HEADER_VERSION).byte(KDF_PBKDF2_SHA256)
            .int(iterations).byte(pinLength).bytes(salt).bytes(dekNonce).bytes(wrappedDek)
            .toByteArray()

        companion object {
            private val MAGIC = "GVH1".encodeToByteArray()
            private const val HEADER_VERSION = 1
            private const val KDF_PBKDF2_SHA256 = 1

            fun decode(bytes: ByteArray): VaultHeader {
                val reader = BinaryReader(bytes)
                if (!reader.raw(4).contentEquals(MAGIC)) throw MalformedDataException("Bad header magic")
                if (reader.byte() != HEADER_VERSION) throw MalformedDataException("Unsupported header version")
                if (reader.byte() != KDF_PBKDF2_SHA256) throw MalformedDataException("Unsupported KDF")
                val iterations = reader.int()
                val pinLength = reader.byte()
                val salt = reader.bytes(64)
                val nonce = reader.bytes(64)
                val wrapped = reader.bytes(256)
                if (iterations < 1 || nonce.size != Aead.NONCE_SIZE || wrapped.size != Aead.KEY_SIZE + Aead.TAG_SIZE) {
                    throw MalformedDataException("Invalid header parameters")
                }
                return VaultHeader(iterations, pinLength, salt, nonce, wrapped)
            }
        }
    }

    companion object {
        const val HEADER = "header"
        const val LOCKOUT = "lockout"
        const val INDEX = "index"
        const val INDEX_BACKUP = "index.bak"
        const val BLOBS = "blobs"
        const val THUMBS = "thumbs"
        private const val TEMP_SUFFIX = ".tmp"
        private const val INDEX_VERSION = 1
        private const val ID_BYTES = 12
        private const val SALT_BYTES = 16
        private val AAD_DEK = "GalleryVault/dek/v1".encodeToByteArray()
        private val AAD_INDEX = "GalleryVault/index/v1".encodeToByteArray()

        fun blobPath(id: String) = "$BLOBS/$id"
        fun thumbPath(id: String) = "$THUMBS/$id"
        private fun blobAad(id: String) = "blob:$id".encodeToByteArray()
        private fun thumbAad(id: String) = "thumb:$id".encodeToByteArray()
    }
}
