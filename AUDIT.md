# Gallery — Full Codebase Audit

- Date: 2026-11-21 · Revision: `3d42455` (main, clean) · Auditor: Hermes (senior Android/KMP review pass)
- Method: omh-code-review (bug-first, evidence-gated) + omh-tech-debt-audit (severity×effort ledger) + android-permissions-security / android-intent-security / jetpack-compose-performance lenses.
- Scope: every source file in `shared/`, `androidApp/`, `iosApp/`, all Gradle/manifest/resource config, all 10 test files, README + docs/ARCHITECTURE.md + docs/FEATURES.md. ~14,009 lines of Kotlin across 103 files.
- Executed evidence: `:shared:testAndroidHostTest` → **72 tests, 0 failures, 0 errors, 0 skipped** (BUILD SUCCESSFUL, 4m25s). `:androidApp:assembleDebug` → SUCCESS. `:androidApp:assembleRelease` (R8 minify + resource shrink) → SUCCESS, APK 1.76 MB. `:shared:compileKotlinIosArm64` → SUCCESS (iOS sources compile on Linux). No emulator/device run (host constraint); runtime behavior findings are static traces, labeled as such.

## 1. Orientation

Stack: AGP 9.1.1, Kotlin 2.4.21, Compose Multiplatform 1.12.2, Material3 1.12.0-alpha03, compileSdk/targetSdk 37, minSdk 24, Gradle 9.8.2 (daemon JVM criteria: Azul 21), coroutines 1.10.1, kotlinx-datetime 0.6.2, androidx.exifinterface. No DI framework (hand-rolled `AppContainer`), no image library (custom MediaStore loader), no network code, no analytics.

Architecture (verified against code, matches docs/ARCHITECTURE.md):
- UI: Compose screens + StateFlow ViewModels; hand-rolled tab-rooted back stack (`GalleryNavState`) where each pushed `NavEntry` owns a `ViewModelStore` and a `SaveableStateHolder` key; tab roots use the host store.
- Domain: `MediaItem`/`VaultItem` models, `MediaScope.arrange` (photos-then-videos ordering shared by grid and viewer), `GroupMediaByTimelineUseCase` (DST-aware period-range caching), `VaultTransferCoordinator` (encrypt→consent-delete→rollback).
- Data: one shared MediaStore subscription (`shareIn WhileSubscribed(5s)`), favorites/tags in SharedPreferences via `LocalMetadataStore`, envelope-encrypted vault (`DefaultVaultRepository`).
- Crypto: JCA AES-GCM on Android; portable PureAesGcm (FIPS-197 AES + Shoup-table GHASH) on iOS; common SHA-256/HMAC/PBKDF2/HKDF; segmented streaming AEAD with per-file HKDF subkeys.
- Platform: MediaStore consent broker, permission controller (full/partial/denied/permanent), lossless JPEG/PNG strippers + video remux, editor exporter, Keystore device key, FileProvider scoped to `cache/sanitized/`.

Churn (git log, most-touched): App.kt (9), LightboxViewerScreen (8), TimelineScreen (6), MediaThumbnailItem/PhotoEditorScreen/LocalMetadataStore (5). Largest files: GalleryIcons.kt 1128 (vector data), DefaultVaultRepository.kt 606, VaultScreen.kt 565, AndroidMediaStoreDataSource.kt 486, App.kt 467.
Test entry point: `./gradlew :shared:testAndroidHostTest` (72 tests). **No CI configuration exists** (.github/, GitLab, Jenkins all absent).

## 2. Ranked findings

Severity: P0 blocker · P1 high · P2 medium · P3 low/hardening. All paths relative to repo root. "Static" = complete code trace, no runtime reproduction.

### P1-1 · "Moved to trash" is a one-way door: no trash implementation — CONFIRMED (user-reported)
`App.kt:188` promises "Moved to trash"; `AndroidMediaStoreDataSource.kt:282` sends media to the system trash via `createTrashRequest(uris, true)`, and the default MediaStore view hides trashed items. But nothing in the app ever queries `QUERY_ARG_MATCH_TRASHED`, offers restore (`createTrashRequest(uris, false)`), or empties the trash. Deleted media is unreachable from the app despite the recoverability promise. **Fixed 2026-11-21** — see §10.

### P2-1 · Consent broker can hang permanently after non-finishing activity destroy — CONFIRMED (static)
`shared/src/androidMain/.../platform/AndroidSystemBridges.kt:44-52,56-69`
`onDestroy` completes the pending `CompletableDeferred` only `if (activity.isFinishing)`. A destroy without finishing (configuration change not in MainActivity's `configChanges` — locale, fontScale, mcc/mnc are not listed; or "don't keep activities") while a system consent dialog is pending leaves the deferred unresolved. `requestConsent` holds the broker `Mutex` across `deferred.await()`, and callers run in app-scoped coroutines (App.kt `performDelete`, vault transfers) that are not cancelled with the activity. Result: the awaiting coroutine leaks and **every subsequent delete/trash/write-consent/vault-move deletion blocks forever until process restart**. The re-attached activity gets a new launcher, but the old deferred is never completed.
Fix (S): always `pending?.complete(false); pending = null` in `onDestroy`, finishing or not.

### P2-2 · In-place metadata strip: process death mid-write corrupts the original photo — CONFIRMED (static)
`shared/src/androidMain/.../security/AndroidMetadataStripper.kt:218-226,268-272`
`stripInPlace` backs up the original to `cacheDir/sanitize_work/backup_*`, then `writeInto(uri, stripped)` opens `openOutputStream(uri, "wt")` — truncate-then-write. Exception paths roll back from the backup (inner catch), but **process death during the write leaves the original truncated/corrupted**. The backup survives in cache, yet nothing recovers it on next launch: `AndroidAppGraph.init` only runs `purgeOldShareFiles`, which deletes work files older than 24 h. Low probability (sub-second window), high impact (user's only copy of a photo; this feature is explicitly "the picture itself is not changed").
Fix (M): persist a journal (uri → backup path) before truncating; on startup, restore any journaled backup and verify size before purging. Alternatively write via a temp MediaStore item + swap where the platform allows.

### P2-3 · iOS vault: no device binding + backup-included storage → offline PIN brute force — CONFIRMED mechanism (static), documented as pending
`shared/src/iosMain/.../di/IosAppGraph.kt:31-36` → `AppContainer.demo(...)` inherits `deviceKeyProtector = PassthroughDeviceKeyProtector()` (`AppContainer.kt:82`); `ProtectedFileVaultStorage` uses `NSApplicationSupportDirectory/vault` with `NSFileProtectionComplete` but **no `NSURLIsExcludedFromBackupKey`** anywhere (grep-verified). The header (PBKDF2-150k-wrapped DEK) is therefore stored in plaintext form and included in iCloud/encrypted iTunes backups. An attacker with a backup can brute-force the 4–8 digit PIN offline (10⁴–10⁸ candidates; GPU-feasible at 150k iterations). `NSFileProtectionComplete` guards at-rest access while locked, not backups.
Mitigations present: docs/ARCHITECTURE.md §6 explicitly says "no device key yet (Keychain pending)"; iOS currently vaults only demo media. **Must be closed before real PhotoKit data lands**: Keychain-backed device key (`kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly`, non-migratable) + exclude the vault dir from backup.

### P2-4 · iOS: unlocked vault content can appear in the app-switcher snapshot — CONFIRMED gap (static)
`iosApp/iosApp/iOSApp.swift`, `ContentView.swift`, `shared/src/iosMain/.../MainViewController.kt`
Android blanks recents/screenshots via `FLAG_SECURE` (MainActivity.kt:47-53, driven by App.kt:228-230). iOS has no equivalent: no `scenePhase` privacy overlay, and `onSensitiveContentVisible` is not wired. The vault does lock on `ON_STOP`, but the system snapshot races the lock UI. For a privacy-first app this is the iOS counterpart of a solved Android problem.
Fix (S): overlay an opaque view when `scenePhase != .active` (SwiftUI) or hide the window in `sceneWillResignActive`.

### P2-5 · iOS image decode ignores maxDimension — CONFIRMED (static)
`shared/src/iosMain/.../ui/common/ImageDecoding.ios.kt:7-10`
`Image.makeFromEncoded(bytes)` decodes at full resolution; the `maxDimension` parameter is unused. Vault preview allows files up to 64 MB (`VaultViewModel.MAX_IN_APP_IMAGE_BYTES`); a 50 MP image decodes to ~200 MB → jetsam risk on phones. Also unverified whether Skia applies EXIF orientation here (Android paths do). Demo-scope today, but the same code path serves real vault content.
Fix (S): downsample via `SkCodec`/`Image.makeFromEncoded` + scale, or CGImageSource thumbnail APIs.

### P3-1 · Vault path containment uses string prefix without separator — hardening
`shared/src/androidMain/.../vault/AndroidVaultPlatform.kt:31-35`
`resolved.path.startsWith(root.canonicalPath)` would also match a sibling `vault-evil/`. Not exploitable today: ids are random hex (`secureRandomBytes(12).toHex()`), other paths are constants, and index-supplied ids require the DEK to forge. Fix (S): compare `Path` components or append `File.separator`.

### P3-2 · Lockout counter resets if the lockout file is deleted — hardening
`shared/src/commonMain/.../vault/data/DefaultVaultRepository.kt:385-396`
Tampered/unreadable lockout fails closed (good), but a **missing** file returns `(0,0)`. Deleting it (root/ADB on debuggable Android builds; sandbox escape on iOS) removes rate-limiting. On Android the header stays Keystore-sealed, so this only enables online guessing with file access already implied. Fix (S): bind attempt state into the device-sealed header or a sealed monotonic record.

### P3-3 · Vault header parse: unbounded iterations, unvalidated salt length — hardening
`DefaultVaultRepository.kt:569-583`
`iterations` only checked `>= 1` (a forged header with `Int.MAX_VALUE` hangs PBKDF2); `salt` accepted at any length 0..64. On Android the Keystore seal blocks forgery; on iOS (passthrough) a replaced header is possible. Fix (S): cap iterations (e.g. ≤ 10M), require salt == 16.

### P3-4 · FLAG_SECURE set via LaunchedEffect — one-frame race — hardening
`shared/src/commonMain/.../App.kt:230`
`LaunchedEffect(vaultContentVisible)` dispatches a coroutine; the first vault frame can draw before the flag is applied. `SideEffect` runs synchronously after recomposition and is the tighter primitive (compose-performance skill, effects category). Fix (S).

### P3-5 · ZoomableMediaBox recomposes on every pinch/animation frame — performance
`shared/src/commonMain/.../ui/viewer/ZoomableMediaBox.kt:66`
`val isZoomed = scale > ZOOMED_THRESHOLD` reads fast-changing `scale` in composition; each pinch event and each `animateZoomTo` frame invalidates the composable even though rendering is correctly deferred to `graphicsLayer`. Fix (S): `remember { derivedStateOf { scale > ZOOMED_THRESHOLD } }`.

### P3-6 · Selection-mode O(N) work in composition — performance
`TimelineScreen.kt:140`, `FolderDetailScreen.kt:128` filter the whole item list on every selection toggle; `MediaGrid.kt:127,157` passes the selection `Set` by value so all visible item lambdas re-run per toggle (leaf recomposition is still skipped by Boolean equality). Fix (S): hoist `selectedItems` into the ViewModel / `derivedStateOf`; pass `isSelected: () -> Boolean` providers.

### P3-7 · Missing contentType in secondary lazy layouts — performance (minor)
`VaultScreen.kt:403`, `AlbumsScreen.kt:171`, `TimelineScreen.kt:271` use `key` but no `contentType` (main MediaGrid does both correctly). Fix (S).

### P3-8 · Orphan recovery fully decrypts large files on unlock — performance edge
`DefaultVaultRepository.kt:487-494` — after an interrupted transfer, `recoverOrphan` streams the entire blob through `SniffingSink` (decrypt + discard) to sniff 16 bytes. Multi-GB video → long unlock. Fix (M): decrypt only the first segment for sniffing; keep full verification lazy.

### P3-9 · HEIC brand sniffing incomplete — correctness (minor)
`DefaultVaultRepository.kt:538` recognizes `heic/heix/mif1` but not `heim/heis/hevc/hevx` → recovered HEIC sequences classify as `video/mp4`. Fix (S).

### P3-10 · Video remux buffer sizing & editor export OOM — robustness (minor)
`AndroidMetadataStripper.kt:146-177`: buffer sized from `KEY_MAX_INPUT_SIZE`; an under-reported value can truncate the remuxed copy (unverified at runtime). `AndroidEditedImageExporter.kt` + `PhotoEditorViewModel.kt:176-182`: `catch (e: Exception)` misses `OutOfMemoryError` from a 6144² ARGB render (~150 MB) on low-RAM devices. Fix (S): grow buffer on demand; catch Throwable→Failure in save (rethrow CancellationException).

### P3-11 · GIF "no metadata" assumption — privacy edge
`AndroidMetadataStripper.kt:76-81` passes GIF through untouched; GIF89a application extensions can carry XMP (rare). Fix (S): strip non-graphic application extensions or document the limitation.

### P3-12 · Release build signed with debug key — release readiness
`androidApp/build.gradle.kts:43` — intentional and documented (README:120, sideload perf testing), but there is no release keystore config; **not Play-publishable as-is**. Also: no baseline profile generation, no CI.

### P3-13 · iOS release-readiness cluster
No `PrivacyInfo.xcprivacy` (NSUserDefaults is a required-reason API → App Store rejection); `GalleryBackHandler.ios.kt` is a no-op while README claims multiplatform back navigation (screens have explicit back buttons, edge-swipe does nothing); `Config.xcconfig` has empty `TEAM_ID` embedded in the bundle id; demo library renders placeholders (`NoOpImageLoader`). All consistent with "iOS shell/demo" status but must be tracked.

### P3-14 · Documentation drift (spec axis)
- README:10 / FEATURES.md:10 claim **sticky headers**; grid headers are ordinary full-span items that scroll away (LazyVerticalGrid has no sticky API). `MediaGrid.kt:117-123,147-153`.
- README:26 / FEATURES.md:41 claim **frosted-glass blur**; `FrostedGlass.kt:13-34` is a translucent tint + border, no blur anywhere (no RenderEffect/haze). Either implement backdrop blur or correct the copy.
- ARCHITECTURE.md:114 says **69 tests**; actual count is **72** (verified from JUnit XML).
- `ColorMatrixHelper.kt:17-56` comment order (contrast→saturation→warmth) is the reverse of the actual application order (warmth→saturation→contrast, since `multiply(current, next)` post-applies). Preview and export use the same function, so **no visual bug** — fix the comment.
- Dead model surface: `ColorAdjustments.highlights/shadows` never read (`EditorModels.kt:24-25`); `MediaType.BURST` never assigned (`MediaModels.kt:8`).

## 3. Spec-axis verdict

Claim sources: README.md, docs/ARCHITECTURE.md, docs/FEATURES.md (no dispatch spec supplied). **Substantially accurate** — the architecture doc is unusually faithful (layering, key hierarchy, transfer semantics, consent flows, iOS pending-status all match code). Drift items: P3-14 (sticky headers, blur, test count) and the iOS back-navigation claim. Android security claims (Keystore binding, no-backup storage, FLAG_SECURE, lockout persistence, lossless stripping) verified true in code.

## 4. Debt ledger — top fixes & quick wins

| Rank | ID | Severity | Effort | Fix |
|---|---|---|---|---|
| 1 | P2-1 broker hang | P2 | S | complete(false) on every onDestroy |
| 2 | P2-2 strip crash window | P2 | M | startup backup-recovery journal |
| 3 | P2-3 iOS device key + backup exclusion | P2 | M | Keychain protector (before real iOS data) |
| 4 | P2-4 iOS snapshot overlay | P2 | S | scenePhase privacy cover |
| 5 | P2-5 iOS downsampling | P2 | S | honor maxDimension |
| 6 | P3-4 FLAG_SECURE SideEffect | P3 | S | one-line change |
| 7 | P3-5 zoom derivedStateOf | P3 | S | one-line change |
| 8 | P3-2/P3-3 vault parse hardening | P3 | S | caps + sealed lockout |
| 9 | P3-14 doc corrections | P3 | S | copy fixes |
| 10 | P3-12 CI + release keystore + baseline profile | P3 | M | release pipeline |

Quick wins (payoff/effort): P2-1, P3-4, P3-5, P3-9, P3-14 — all ≤ a few lines each.

## 5. Looks bad but is actually fine

- **Hand-rolled crypto**: normally an automatic red flag; here it is FIPS-197/NIST/RFC-vector-tested, differentially cross-checked against JCA (300 random rounds), GHASH cross-checked against a bit-serial reference, and Android uses JCA anyway. The portable engine exists because CryptoKit is Swift-only and CommonCrypto GCM is private SPI — a legitimate KMP constraint, documented. T-table AES is cache-timing-sensitive but confined to iOS at-rest use with the threat model stated (Aes.kt:10-12).
- **No Navigation-Compose**: the custom stack looks like reinvention, but it correctly solves per-entry ViewModelStore disposal, tab-root state retention and saveable-state keys, with tests (UiStateTest). Migrating would be churn, not improvement.
- **`!!` in 5 places**: each is guarded by a preceding check or a smart-cast limitation; none is reachable with null in normal flow.
- **Full-library in-memory list**: bounded by device media count; first-page streaming (600 items) gives fast first paint; grouping runs off-main with mapLatest cancellation. Fine for a gallery; revisit only for 100k+ item libraries.
- **Vault reset without PIN**: deliberate forgot-PIN path behind a destructive confirmation; availability-only risk, standard for local vaults.
- **PureAesGcm singleton on iOS**: stateless per call (engine + GHash created per seal/open) — thread-safe.

## 6. Checked and clean

- Crypto correctness: AES key expansion/S-box, GCM J0/CTR/GHASH/tag, verify-before-decrypt, constant-time tag compare, HKDF/PBKDF2/HMAC — vector-verified, 72/72 tests pass.
- Streaming AEAD: per-file HKDF subkey bound to entry id, nonce = prefix‖index‖isLast defeats truncation/reorder/append (tests cover each); constant memory; key zeroization in finally paths.
- Vault lifecycle: envelope hierarchy, atomic fsync writes, index + .bak, orphan adoption, pruning, lockout persistence/escalation/fail-closed, change-PIN rewrap only, lock wipes keys, bulk ops on private key copies.
- Transfers: encrypt→verify→consent-delete→rollback; NonCancellable around consent bookkeeping; restore publishes only after full authentication; failed restore keeps vault copy (tests cover all five scenarios).
- Android security surface: only launcher activity exported; no services/receivers/custom providers; FileProvider non-exported with scoped `cache/sanitized/` path; no nested-intent handling (no redirection surface); share grants via ClipData + FLAG_GRANT_READ; vault in noBackupFilesDir; backup rules exclude file/database domains; permissions API-level-correct incl. READ_MEDIA_VISUAL_USER_SELECTED partial access, ACCESS_MEDIA_LOCATION gating with setRequireOriginal, permanent-denial → Settings; no permission-state caching bugs (refresh on ON_START).
- Metadata strippers: fail-closed sharing; JPEG APPn/COM/trailer removal with ICC/JFIF/Adobe kept and orientation preserved; progressive-scan marker filtering; PNG critical+render-ancillary whitelist; defensive TIFF parser (depth/visited/count caps); idempotent (tested).
- Compose performance fundamentals: lazy grids keyed + contentType (main grid), cell-size decode buckets, bounded 4-worker decode parallelism with CancellationSignal, prepareToDraw off-frame, LRU capped at heap/4, draw-phase animations, no side effects in composition bodies, no runBlocking/Thread.sleep/GlobalScope anywhere.
- Editor: preview/export operation parity (crop→flip→rotate→matrix→vignette) verified line-by-line; undo coalescing; non-destructive save-as-copy with IS_PENDING import.
- Hygiene: no secrets in repo or logs (logs carry ids/names only), local.properties untracked, no TODO/FIXME debt, R8 release builds clean.

## 7. Could not assess / verification gaps

- Runtime behavior on a device/emulator (host constraint): consent-dialog flows, remux output integrity on real videos, Skia EXIF orientation on iOS, MediaMetadataRetriever video-location redaction without ACCESS_MEDIA_LOCATION (AndroidMediaStoreDataSource.kt:245-259 relies on framework redaction — unverified), thumbnail quality under fast scroll.
- iOS framework linking and app behavior (Linux host; klib compile verified only).
- Release APK runtime (built, not installed/run).
- Independent verification: a separate reader (subagent) was dispatched for the five security/data-loss candidates; verdicts recorded in §8.

## 8. Independent verification record

A separate reader (verifier subagent, static traces only, no runtime reproduction) inspected the five security/data-loss candidates with instructions to try to disprove each. Full verdicts returned:

- broker-hang: **confirmed** — traced onDestroy/isFinishing guard, mutex held across `deferred.await()` with no timeout anywhere in shared/src, locale/fontScale absent from configChanges, app-scoped callers survive destroy, and androidx.activity 1.13.0 registry semantics drop the pending result on ON_DESTROY. "Don't keep activities" makes it deterministic; disproof attempts failed.
- strip-inplace-crash-window: **confirmed** — "wt" truncate-write, exception-only rollback, no startup recovery, 24h purge deletes the orphaned backup; ARCHITECTURE.md's "restores on any failure" claim holds only for exceptions.
- ios-vault-offline-bruteforce: **confirmed** — passthrough protector + backup-included Application Support traced end to end; computed feasibility: ~33k–100k PBKDF2-150k candidates/s on a 10–30 GH/s GPU → 4-digit PIN sub-second, full 8-digit space ~0.3–0.8 h. Mitigation at audit time: documented-pending + demo-only media.
- vault-path-prefix: **rejected as exploitable** — the prefix weakness is a code fact, but every path source is a constant, random-hex id, or authenticated-index id; no untrusted input reaches it without already holding the DEK. Retained as defense-in-depth (fixed + regression-tested anyway). iOS `ProtectedFileVaultStorage`'s ".." substring check noted as equally coarse and equally unreachable.
- lockout-delete-reset: **confirmed** with prerequisites — root/run-as (Android) or jailbreak (iOS); strips online rate-limiting only; Android header stays Keystore-sealed; moot on iOS where file access already exposes the header. Recommended fix matches the implemented fail-closed behavior.

All four confirmed findings were fixed in the same session (§10); the rejected candidate was hardened regardless.

## 9. Verdict

**At audit time:** Android personal/sideload use: ship-with-fixes (P1-1 trash gap, P2-1 consent hang, P2-2 strip crash window open). Play Store / iOS public release: no_ship until release signing, CI and the iOS cluster were addressed.

**After the fix pass (§10):** P1-1, all five P2 findings and the code-level P3 items are implemented and regression-tested where host-testable; no open P0/P1/P2 code findings remain. Still open before a public release: P3-14 doc corrections, P3-12 CI + a real release keystore (user-held), and runtime verification on a physical Android device and a Mac/iOS device — the audit's static + host-test evidence does not substitute for those.
Confidence: high for static findings (full code trace + executed builds/tests); runtime gaps named in §7.

## 10. Fix implementation ledger (2026-11-21, same session)

TDD where host-testable (RED observed before GREEN); platform glue verified by compilation + static trace, runtime gaps noted.

| Finding | Status | Change | Verification |
|---|---|---|---|
| P1-1 trash | **Implemented** | `hasTrash` capability; `observeTrashed()` (MediaStore `QUERY_ARG_MATCH_TRASHED=MATCH_ONLY`, R+); `restoreFromTrash()` via `createTrashRequest(false)` + consent + re-query verification; `DATE_EXPIRES` surfaced per item; repository `trashed` flow + unhide-on-restore; `TrashViewModel`/`TrashScreen` (browse, restore, permanent delete, empty trash, confirmations, expiry subtitle); `GalleryDestination.Trash`; Trash chip in the filter row (follow-up moved it from the header); "Moved to trash → View" snackbar action; fake source trash support | 9 new tests RED→GREEN (TrashTest); full suite green |
| P2-1 broker hang | **Implemented** | `onDestroy` always completes pending(false) | compile; static trace; verifier-confirmed root cause |
| P2-2 strip crash window | **Implemented** | fsync'd journal (uri+backup) written before truncation; `recoverInterruptedStrips()` on first activity attach re-requests write consent, restores + verifies backup; TTL purge skips journals/backups | compile; runtime recovery path not device-tested |
| P2-3 iOS device key | **Implemented** | `KeychainDeviceKeyProtector` (SecItem keychain, `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly`, AES-GCM via PureAesGcm); wired into IosAppGraph; vault dir excluded from backup via `NSURLIsExcludedFromBackupKey` | `compileKotlinIosArm64` green; device behavior untested (no Mac). Note: pre-existing iOS demo vaults become Unavailable (header format changed) — erase/recreate |
| P2-4 iOS snapshot | **Implemented** | SwiftUI `scenePhase` privacy overlay in ContentView.swift | static only (Swift not compilable on Linux) |
| P2-5 iOS decode | **Implemented** | `decodeImageBitmap` downsamples to maxDimension via Skia canvas; transient full-res decode remains (documented) | iosArm64 compile green |
| P3-1 path prefix | **Implemented** | separator-aware containment in FileVaultStorage.file() | 2 host tests RED→GREEN (FileVaultStorageTest) |
| P3-2 lockout delete | **Implemented** | missing lockout + existing header → fail-closed | test RED→GREEN |
| P3-3 header caps | **Implemented** | iterations ≤ 10M, salt must be 16 bytes | 2 tests RED→GREEN |
| P3-4 FLAG_SECURE | **Implemented** | SideEffect instead of LaunchedEffect | compile |
| P3-5 zoom recomposition | **Implemented** | derivedStateOf for isZoomed | compile |
| P3-6 selection O(N) | **Implemented** | derivedStateOf for selectedItems in Timeline/FolderDetail | compile |
| P3-7 contentType | **Implemented** | vault grid, albums grid, quick-folder row | compile |
| P3-8 orphan sniff | **Implemented** | `StreamingAead.sniff()` decrypts only first segment, derives full size from authenticated framing; recoverOrphan uses it | 2 tests RED→GREEN incl. tamper case |
| P3-9 HEIC brands | **Implemented** | heim/heis/hevc/hevx/msf1 added | test RED→GREEN |
| P3-10 remux/OOM | **Implemented** | remux buffer grows on exact-fill samples; editor save catches Throwable; exporter catches OutOfMemoryError | 2 tests RED→GREEN (EditorSaveTest); remux guard compile-only |
| P3-11 GIF metadata | **Implemented** | `GifMetadataStripper` drops comment/plain-text/non-NETSCAPE app extensions, keeps GCE+looping+pixels; wired into share path | 4 tests RED→GREEN (parser bug found via hex trace: missing 0x0B app-header size byte) |
| P3-13 privacy manifest | **Implemented** | PrivacyInfo.xcprivacy (UserDefaults CA92.1); Xcode 16 synchronized group auto-includes it | static |
| P3-14 docs/dead code | **Implemented** | sticky-header/blur claims corrected in README/FEATURES; ARCHITECTURE updated (trash, GIF sanitizing, crash journal, iOS Keychain status, 95 tests); color-matrix order comment fixed; dead `highlights`/`shadows`/`BURST` removed; README/FEATURES gained Trash sections | docs-only + compile re-verified |
| P3-12 CI / signing | **CI implemented** | `.github/workflows/ci.yml`: host tests + debug + release (R8) + iOS klib compile, test-results/APK artifacts. Release keystore still requires the user's key | workflow YAML linted; first run happens on push |

Final gate (after all fixes incl. GIF parser fix and dead-code removal): `:shared:testAndroidHostTest` + `:androidApp:assembleDebug` + `:androidApp:assembleRelease` + `:shared:compileKotlinIosArm64` → BUILD SUCCESSFUL, GRADLE_EXIT=0, **95 tests / 0 failures / 0 errors** (counted from TEST-*.xml), `minifyReleaseWithR8` observed, debug APK 13.5 MB + release APK 1.77 MB present in outputs. Test count: 72 → 95 (23 new).

Follow-up iteration (same session, after device install): user-reported wallpaper bug — the OEM crop-and-set activity on the test device (nubia/ZTE Z2572N) discarded the aligned crop position for wide images; `dumpsys wallpaper` showed the 4500×2250 source applied with `mCropHint=Rect(0, 0 - 1266, 2250)` (leftmost slice). Fixed by replacing the system crop intent with an in-app cropper (`WallpaperCropScreen` + pure `WallpaperCropMath`, 7 regression tests RED→GREEN using the real device numbers) applying the exact region via `WallpaperManager.setBitmap` (null crop hint, `SET_WALLPAPER` permission). Trash entry moved from the header icon to a chip beside the media filters. Gate re-run: **102 tests / 0 failures**, debug + release + iOS compile green; pushed and installed as a same-key upgrade (data preserved), launch verified on device.