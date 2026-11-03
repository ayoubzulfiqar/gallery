# Gallery Architecture

Compose Multiplatform gallery for Android and iOS built on Clean Architecture with unidirectional data flow (Kotlin Coroutines + StateFlow).

---

## 1. Layers

```
┌──────────────────────────────────────────────────────────────────────┐
│ UI (commonMain/ui)                                                   │
│  Screens · ViewModels · GalleryNavState (per-entry ViewModelStores)  │
│  AsyncMediaImage / EncryptedImage (common LRU cache, error states)   │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ StateFlow / intents
┌───────────────────────────────▼──────────────────────────────────────┐
│ Domain (commonMain/domain)                                           │
│  MediaItem · VaultItem · MediaScope · PinPolicy                      │
│  GroupMediaByTimelineUseCase · VaultTransferCoordinator              │
│  MediaRepository · VaultRepository                                   │
└───────────────────────────────┬──────────────────────────────────────┘
┌───────────────────────────────▼──────────────────────────────────────┐
│ Data / Security (commonMain)                                         │
│  DefaultMediaRepository (single shared library stream)               │
│  LocalMetadataStore (persistent via KeyValueStore)                   │
│  DefaultVaultRepository (envelope encryption, crash-safe index)      │
│  core/crypto (AES-GCM, streaming AEAD, PBKDF2, HKDF, SHA-256)        │
│  security/ (lossless JPEG/PNG metadata strippers)                    │
└───────────────────────────────┬──────────────────────────────────────┘
┌───────────────────────────────▼──────────────────────────────────────┐
│ Platform (androidMain / iosMain)                                     │
│  Android: MediaStore data source, consent broker, permissions,       │
│           image loader, sanitizer (+video remux), editor exporter,   │
│           Keystore device key, no-backup vault storage               │
│  iOS:     NSUserDefaults prefs, file-protected vault storage,        │
│           SecRandom, Skia decoding (demo library, see §6)            │
└──────────────────────────────────────────────────────────────────────┘
```

The object graph is process-scoped (`AppContainer`, created by `AndroidAppGraph` in `GalleryApplication` / `IosAppGraph`), so repositories, caches and an unlocked vault session survive Activity recreation.

---

## 2. Navigation & lifecycle

* `GalleryNavState` is a tab-rooted back stack. Switching tabs replaces the stack (it never grows), Back from Albums/Vault returns to Photos, and Back from Photos exits.
* Every pushed screen is a `NavEntry` that owns a `ViewModelStore`; popping, replacing or switching tabs clears it, so screen ViewModels never leak collectors. Tab ViewModels live in the host store. The stack itself lives in a host-scoped `NavigationHolderViewModel`.
* Each entry has a stable key for `SaveableStateHolder`, so grid scroll positions survive opening and closing the viewer.
* The shell registers its back handler before any screen, so screen-level handlers (clear selection, close sheet, cancel PIN change) take precedence.
* Lifecycle: `ON_START` re-reads permissions and refreshes the library (this picks up partial-access changes); `ON_STOP` locks the vault (deferred until a running transfer finishes).

---

## 3. Media library

* `AndroidMediaStoreDataSource` queries the Files collection on `Dispatchers.IO` with an API-level-correct projection (`datetaken`, `duration`, `orientation`, `bucket_*`, `RELATIVE_PATH` / `_data`). The timestamp falls back from DATE_TAKEN to DATE_MODIFIED to DATE_ADDED; items without any timestamp go into an "Unknown date" group.
* A `ContentObserver` posts to a conflated channel, and queries are debounced by 400 ms. The first 600 rows are emitted early for a fast first paint, then the full library (no 5,000-item cap).
* Ordering is a domain rule (`MediaScope.arrange`): the main **All** timeline lists photos first and videos after them, and both the grid and the viewer use it, so paging always matches the grid. The timeline groups the photo and video partitions concurrently on the compute pool, and `mapLatest` cancels stale grouping when a newer library snapshot arrives. The grouper caches the current day/month/year instant range, so it only converts dates when an item crosses a period boundary.
* `DefaultMediaRepository` owns the only subscription (`shareIn`, `WhileSubscribed(5s)`), applies favorites and tags, hides items during vault transfers, and keeps deleted items hidden until MediaStore confirms they are gone.
* Deletion: Android 11+ uses `createTrashRequest` (normal delete, recoverable) or `createDeleteRequest` (vault moves, so no plaintext copy stays in the trash) through `MediaStoreConsentBroker`. Android 10 uses per-item `RecoverableSecurityException` consent; Android 9 and below need `WRITE_EXTERNAL_STORAGE`. Results report exactly which IDs were deleted.
* Imports (vault restore, edited copies) insert with `IS_PENDING=1`, stream the bytes, and publish only on success; failures delete the pending row.

---

## 4. Vault security

**Key hierarchy**

| Layer | Purpose |
|---|---|
| Data key (DEK) | Random 256-bit key encrypting every item, thumbnail and the index |
| PIN key (KEK) | PBKDF2-HMAC-SHA256 (150,000 iterations, 16-byte random salt; parameters stored per vault) wraps the DEK with AES-GCM |
| Device key | The whole header is sealed by a non-exportable Android Keystore AES-256-GCM key (hardware-backed when available) |

* Offline PIN brute force is infeasible because the header is useless without the device key. Online guessing is rate-limited: after 5 wrong PINs, back-off runs from 30 s up to 1 h. The counter is persisted (device-sealed) and survives restarts, and an unreadable counter never grants extra attempts.
* PINs are 4–8 digits; repeated or sequential PINs are rejected. Changing the PIN re-wraps the DEK only.
* **Streaming AEAD** (`core/crypto/StreamingAead.kt`): files are split into 256 KiB segments, each sealed with AES-GCM using a per-file HKDF subkey bound to the entry ID. The nonce is `prefix ‖ segmentIndex ‖ isLast`, which detects truncation, reordering and appending. Memory stays constant for multi-GB videos.
* Storage lives in `noBackupFilesDir/vault` (mode 0600, excluded from backup). Writes are atomic with fsync; the index keeps a `.bak` copy. On unlock the vault reconciles: entries whose files are missing are pruned, and orphaned encrypted files from an interrupted transfer are adopted (type-sniffed) instead of being silently dropped.
* `lock()` hides content immediately and wipes key material. Bulk encryption runs on a private key copy, so locking never waits for a large transfer.
* Android sets `FLAG_SECURE` while vault content is visible.

**Transfers** (`VaultTransferCoordinator`)

* Move: encrypt and commit, then delete the original with consent. If the user declines, the vault copy is rolled back. Once deletion has started, an ambiguous failure keeps the vault copy (a duplicate is recoverable; a loss is not).
* Restore: decrypt into a pending MediaStore item, publish only after every segment has authenticated, then remove the item from the vault.

**Crypto implementations:** Android uses JCA (Conscrypt/BoringSSL). iOS uses the portable `PureAesGcm` (CryptoKit is Swift-only and CommonCrypto's GCM is private SPI). The portable engine is verified against FIPS-197, the NIST GCM vectors and RFC 4231/6070/5869, plus 300 random differential rounds against JCA.

---

## 5. Privacy tools

* **Share without metadata:** JPEG and PNG are stripped losslessly (APPn/COM/XMP/IPTC/MPF/trailers, and tEXt/iTXt/eXIf/tIME), keeping ICC profiles and a minimal EXIF orientation tag so pictures stay upright. HEIC/WebP are re-encoded with orientation applied; GIF passes through untouched. Video is remuxed with `MediaMuxer`, so streams are copied untouched while `udta`/location atoms are dropped and the rotation hint is preserved. Temporary copies expire after 1 hour.
* **Remove metadata from original:** JPEG/PNG only. Requires write consent (`createWriteRequest`), backs up the original, writes the stripped file, verifies it, and restores the backup on any failure.
* **Info sheet:** real EXIF via `androidx.exifinterface` (unredacted GPS through `ACCESS_MEDIA_LOCATION` + `setRequireOriginal`); video location comes from `MediaMetadataRetriever`. Missing fields are shown as missing, never invented.

---

## 6. Platform status

| Capability | Android | iOS |
|---|---|---|
| Device photo library | MediaStore | Demo library (PhotoKit integration pending) |
| Vault crypto & storage | ✅ Keystore-bound | ✅ File protection; no device key yet (Keychain pending) |
| Share / Lens / wallpaper / sanitize / editor export | ✅ | Hidden (capability-gated) |
| Preferences | SharedPreferences | NSUserDefaults |

The UI never shows an action the platform cannot perform: features are gated by `MediaCapabilities` and nullable services (`metadataStripper`, `imageExporter`).

---

## 7. Testing

`./gradlew :shared:testAndroidHostTest` runs 69 tests: crypto vectors and JCA cross-checks, streaming AEAD tamper cases, vault lifecycle/lockout/recovery, the metadata strippers, transfer rollback, repository behaviour, the nav back stack and ViewModel clearing, viewer re-anchoring, timeline selection, vault PIN flows and editor undo/crop math. `./gradlew :shared:compileKotlinIosSimulatorArm64` type-checks the iOS sources (it works on Linux hosts).
