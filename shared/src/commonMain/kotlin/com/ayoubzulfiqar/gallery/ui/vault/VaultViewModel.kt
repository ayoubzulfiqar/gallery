package com.ayoubzulfiqar.gallery.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.di.UserMessenger
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.PinPolicy
import com.ayoubzulfiqar.gallery.domain.model.UnlockResult
import com.ayoubzulfiqar.gallery.domain.model.VaultError
import com.ayoubzulfiqar.gallery.domain.model.VaultItem
import com.ayoubzulfiqar.gallery.domain.model.VaultState
import com.ayoubzulfiqar.gallery.domain.repository.VaultRepository
import com.ayoubzulfiqar.gallery.domain.usecase.TransferReport
import com.ayoubzulfiqar.gallery.domain.usecase.VaultTransferCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.ayoubzulfiqar.gallery.core.time.currentTimeMillis

enum class PinMode(val title: String, val requiresSubmit: Boolean) {
    UNLOCK("Enter vault PIN", requiresSubmit = false),
    SETUP_ENTER("Create a vault PIN", requiresSubmit = true),
    SETUP_CONFIRM("Confirm your PIN", requiresSubmit = true),
    CHANGE_CURRENT("Enter current PIN", requiresSubmit = true),
    CHANGE_NEW("Enter new PIN", requiresSubmit = true),
    CHANGE_CONFIRM("Confirm new PIN", requiresSubmit = true)
}

data class TransferStatus(val label: String, val completed: Int, val total: Int)

data class VaultUiState(
    val vaultState: VaultState = VaultState.Loading,
    val items: List<VaultItem> = emptyList(),
    val pinMode: PinMode = PinMode.UNLOCK,
    val pinLength: Int = 0,
    /** Digits required before auto-submitting (unlock), or null when the user confirms explicitly. */
    val expectedPinLength: Int? = null,
    val pinMessage: String? = null,
    val isPinError: Boolean = false,
    val lockoutSecondsRemaining: Long = 0,
    val isBusy: Boolean = false,
    val transfer: TransferStatus? = null,
    val pendingMoveCount: Int = 0,
    val selectedIds: Set<String> = emptySet(),
    val isDeviceBound: Boolean = false,
    /** Item open in the full-screen preview, if any (cleared on lock or when the item goes away). */
    val previewItemId: String? = null
) {
    val previewItem: VaultItem? get() = previewItemId?.let { id -> items.firstOrNull { it.id == id } }

    val showsPinPad: Boolean
        get() = vaultState is VaultState.Locked || vaultState is VaultState.NotSetUp ||
            pinMode == PinMode.CHANGE_CURRENT || pinMode == PinMode.CHANGE_NEW || pinMode == PinMode.CHANGE_CONFIRM
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

class VaultViewModel(
    private val vault: VaultRepository,
    private val transfers: VaultTransferCoordinator,
    private val messenger: UserMessenger,
    private val clock: () -> Long = ::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState(isDeviceBound = vault.isDeviceBound))
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var pin = StringBuilder()
    private var firstEntry: String? = null
    private var currentPinForChange: String? = null
    private var pendingMove: List<MediaItem> = emptyList()
    private var transferJob: Job? = null
    private var lockAfterTransfer = false
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch { vault.initialize() }
        viewModelScope.launch {
            combine(vault.state, vault.items) { state, items -> state to items }.collect { (state, items) ->
                val current = _uiState.value
                val mode = when {
                    state is VaultState.NotSetUp && current.pinMode != PinMode.SETUP_ENTER && current.pinMode != PinMode.SETUP_CONFIRM -> PinMode.SETUP_ENTER
                    state is VaultState.Locked && current.pinMode != PinMode.UNLOCK -> PinMode.UNLOCK
                    state is VaultState.Unlocked && (current.pinMode == PinMode.SETUP_ENTER || current.pinMode == PinMode.SETUP_CONFIRM) -> PinMode.UNLOCK
                    else -> current.pinMode
                }
                val modeChanged = mode != current.pinMode
                if (modeChanged) {
                    pin.clear()
                    firstEntry = null
                    currentPinForChange = null
                }
                val itemIds = items.mapTo(HashSet()) { it.id }
                _uiState.update {
                    it.copy(
                        vaultState = state,
                        items = items,
                        pinMode = mode,
                        pinLength = if (modeChanged) 0 else it.pinLength,
                        pinMessage = if (modeChanged) null else it.pinMessage,
                        expectedPinLength = (state as? VaultState.Locked)?.pinLength?.takeIf { len -> mode == PinMode.UNLOCK && len > 0 },
                        selectedIds = it.selectedIds.filterTo(HashSet()) { id -> id in itemIds },
                        previewItemId = it.previewItemId?.takeIf { id -> state is VaultState.Unlocked && id in itemIds }
                    )
                }
                if (state is VaultState.Locked) startLockoutCountdown(state.lockedUntilMillis)
                if (state is VaultState.Unlocked) runPendingMove()
            }
        }
    }

    // region PIN pad

    fun onDigit(digit: Char) {
        val state = _uiState.value
        if (state.isBusy || state.lockoutSecondsRemaining > 0 || digit !in '0'..'9') return
        if (pin.length >= PinPolicy.MAX_LENGTH) return
        pin.append(digit)
        _uiState.update { it.copy(pinLength = pin.length, pinMessage = null, isPinError = false) }
        val expected = state.expectedPinLength
        if (!state.pinMode.requiresSubmit && expected != null && pin.length == expected) onSubmit()
    }

    fun onBackspace() {
        if (pin.isEmpty() || _uiState.value.isBusy) return
        pin.setLength(pin.length - 1)
        _uiState.update { it.copy(pinLength = pin.length, pinMessage = null, isPinError = false) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isBusy) return
        val entered = pin.toString()
        when (state.pinMode) {
            PinMode.UNLOCK -> unlock(entered)
            PinMode.SETUP_ENTER -> acceptNewPin(entered, next = PinMode.SETUP_CONFIRM)
            PinMode.SETUP_CONFIRM -> confirmSetup(entered)
            PinMode.CHANGE_CURRENT -> {
                if (!PinPolicy.isWellFormed(entered)) return showPinError("Enter your current PIN")
                currentPinForChange = entered
                moveTo(PinMode.CHANGE_NEW)
            }
            PinMode.CHANGE_NEW -> acceptNewPin(entered, next = PinMode.CHANGE_CONFIRM)
            PinMode.CHANGE_CONFIRM -> confirmChange(entered)
        }
    }

    fun startChangePin() {
        if (_uiState.value.vaultState !is VaultState.Unlocked) return
        moveTo(PinMode.CHANGE_CURRENT)
    }

    fun cancelPinFlow() {
        currentPinForChange = null
        firstEntry = null
        val state = _uiState.value.vaultState
        moveTo(if (state is VaultState.NotSetUp) PinMode.SETUP_ENTER else PinMode.UNLOCK)
    }

    private fun unlock(entered: String) = runBusy {
        when (val result = vault.unlock(entered)) {
            UnlockResult.Success -> resetEntry()
            is UnlockResult.WrongPin -> showPinError(
                if (result.attemptsBeforeLockout <= 2) {
                    "Incorrect PIN. ${result.attemptsBeforeLockout} attempt${if (result.attemptsBeforeLockout == 1) "" else "s"} left before a timeout."
                } else {
                    "Incorrect PIN. Try again."
                }
            )
            is UnlockResult.LockedOut -> showPinError("Too many attempts. Try again later.")
            is UnlockResult.Failure -> showPinError(result.message)
        }
    }

    private fun acceptNewPin(entered: String, next: PinMode) {
        when (val validation = PinPolicy.validateNewPin(entered)) {
            is PinPolicy.Validation.Invalid -> showPinError(validation.reason)
            PinPolicy.Validation.Valid -> {
                firstEntry = entered
                moveTo(next)
            }
        }
    }

    private fun confirmSetup(entered: String) {
        if (entered != firstEntry) {
            firstEntry = null
            moveTo(PinMode.SETUP_ENTER)
            return showPinError("PINs didn't match. Start again.")
        }
        runBusy {
            try {
                vault.setUp(entered)
                firstEntry = null
                resetEntry()
                messenger.show("Vault created. Remember your PIN — it can't be recovered.")
            } catch (e: VaultError) {
                moveTo(PinMode.SETUP_ENTER)
                showPinError(e.message ?: "Could not create the vault")
            }
        }
    }

    private fun confirmChange(entered: String) {
        val current = currentPinForChange
        if (entered != firstEntry || current == null) {
            firstEntry = null
            moveTo(PinMode.CHANGE_NEW)
            return showPinError("PINs didn't match. Enter the new PIN again.")
        }
        runBusy {
            val result = try {
                vault.changePin(current, entered)
            } catch (e: VaultError) {
                UnlockResult.Failure(e.message ?: "Could not change the PIN")
            }
            when (result) {
                UnlockResult.Success -> {
                    currentPinForChange = null
                    firstEntry = null
                    moveTo(PinMode.UNLOCK)
                    messenger.show("Vault PIN changed")
                }
                is UnlockResult.WrongPin, is UnlockResult.LockedOut -> {
                    currentPinForChange = null
                    firstEntry = null
                    moveTo(PinMode.CHANGE_CURRENT)
                    showPinError(if (result is UnlockResult.LockedOut) "Too many attempts. Try again later." else "Current PIN is incorrect")
                }
                is UnlockResult.Failure -> {
                    moveTo(PinMode.UNLOCK)
                    messenger.show(result.message)
                }
            }
        }
    }

    private fun moveTo(mode: PinMode) {
        resetEntry()
        _uiState.update { it.copy(pinMode = mode, pinLength = 0, pinMessage = null, isPinError = false) }
    }

    private fun resetEntry() {
        pin.clear()
        _uiState.update { it.copy(pinLength = 0) }
    }

    private fun showPinError(message: String) {
        resetEntry()
        _uiState.update { it.copy(pinMessage = message, isPinError = true) }
    }

    private fun startLockoutCountdown(until: Long) {
        countdownJob?.cancel()
        if (until <= clock()) {
            _uiState.update { it.copy(lockoutSecondsRemaining = 0) }
            return
        }
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val remaining = ((until - clock()) + 999) / 1000
                _uiState.update {
                    it.copy(
                        lockoutSecondsRemaining = remaining.coerceAtLeast(0),
                        pinMessage = if (remaining > 0) "Too many attempts. Try again in ${formatCountdown(remaining)}." else null,
                        isPinError = remaining > 0
                    )
                }
                if (remaining <= 0) break
                delay(1_000)
            }
        }
    }

    // endregion

    // region vault session & items

    fun lock() {
        viewModelScope.launch {
            clearSelection()
            closePreview()
            vault.lock()
        }
    }

    /** App moved to the background: lock now, or right after a running transfer finishes. */
    fun onAppBackgrounded() {
        if (transferJob?.isActive == true) lockAfterTransfer = true else lock()
    }

    fun queueMove(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (transferJob?.isActive == true) {
            messenger.show("Please wait for the current transfer to finish")
            return
        }
        pendingMove = items
        _uiState.update { it.copy(pendingMoveCount = items.size) }
        if (vault.state.value is VaultState.Unlocked) runPendingMove()
    }

    fun cancelPendingMove() {
        pendingMove = emptyList()
        _uiState.update { it.copy(pendingMoveCount = 0) }
    }

    private fun runPendingMove() {
        val items = pendingMove
        if (items.isEmpty() || transferJob?.isActive == true) return
        pendingMove = emptyList()
        _uiState.update { it.copy(pendingMoveCount = 0) }
        runTransfer("Encrypting") { onProgress -> transfers.moveToVault(items, onProgress) }
    }

    fun restore(ids: Collection<String>) {
        val items = _uiState.value.items.filter { it.id in ids }
        if (items.isEmpty()) return
        clearSelection()
        closePreview()
        runTransfer("Restoring") { onProgress -> transfers.restore(items, onProgress) }
    }

    fun delete(ids: Collection<String>) {
        if (ids.isEmpty()) return
        clearSelection()
        if (_uiState.value.previewItemId in ids) closePreview()
        viewModelScope.launch {
            try {
                vault.remove(ids)
                messenger.show(if (ids.size == 1) "Item permanently deleted" else "${ids.size} items permanently deleted")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.show(e.message ?: "Could not delete")
            }
        }
    }

    fun resetVault() {
        runBusy {
            pendingMove = emptyList()
            vault.reset()
            moveTo(PinMode.SETUP_ENTER)
            messenger.show("Vault erased")
        }
    }

    fun toggleSelection(id: String) = _uiState.update {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
    }

    fun selectAll() = _uiState.update { state -> state.copy(selectedIds = state.items.mapTo(HashSet()) { it.id }) }

    fun clearSelection() = _uiState.update { it.copy(selectedIds = emptySet()) }

    fun openPreview(id: String) = _uiState.update { it.copy(previewItemId = id) }

    fun closePreview() = _uiState.update { it.copy(previewItemId = null) }

    suspend fun readThumbnail(id: String): ByteArray? = vault.readThumbnail(id)

    suspend fun readImage(id: String): ByteArray? = try {
        vault.readContent(id, MAX_IN_APP_IMAGE_BYTES)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun runTransfer(label: String, block: suspend (onProgress: (com.ayoubzulfiqar.gallery.domain.usecase.TransferProgress) -> Unit) -> TransferReport) {
        transferJob = viewModelScope.launch {
            _uiState.update { it.copy(transfer = TransferStatus(label, 0, 0)) }
            try {
                val report = block { progress ->
                    _uiState.update { it.copy(transfer = TransferStatus(label, progress.completed, progress.total)) }
                }
                messenger.show(describe(label, report))
            } catch (e: CancellationException) {
                throw e
            } catch (e: VaultError.VaultLocked) {
                messenger.show("The vault was locked; nothing was moved")
            } catch (e: Exception) {
                messenger.show(e.message ?: "Transfer failed")
            } finally {
                _uiState.update { it.copy(transfer = null) }
                if (lockAfterTransfer) {
                    lockAfterTransfer = false
                    vault.lock()
                }
            }
        }
    }

    private fun describe(label: String, report: TransferReport): String {
        val moving = label == "Encrypting"
        val parts = mutableListOf<String>()
        if (report.succeeded > 0) {
            val noun = if (report.succeeded == 1) "item" else "items"
            parts += if (moving) "Moved ${report.succeeded} $noun to the vault" else "Restored ${report.succeeded} $noun to your gallery"
        }
        if (report.keptInGallery > 0) {
            parts += if (report.cancelled) {
                "Deletion was declined, so ${report.keptInGallery} stayed in your gallery"
            } else {
                "${report.keptInGallery} couldn't be removed from your gallery and stayed there"
            }
        }
        if (report.failed > 0) parts += "${report.failed} failed" + (report.firstError?.let { " ($it)" } ?: "")
        return parts.joinToString(". ").ifEmpty { "Nothing was transferred" }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    // endregion

    private fun formatCountdown(seconds: Long): String =
        if (seconds >= 60) "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "${seconds}s"

    override fun onCleared() {
        pin.clear()
        firstEntry = null
        currentPinForChange = null
    }

    companion object {
        /** Larger images are viewable after restoring; avoids decrypting huge files into memory. */
        const val MAX_IN_APP_IMAGE_BYTES = 64L * 1024 * 1024
    }
}
