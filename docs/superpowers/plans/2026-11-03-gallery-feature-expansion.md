# Modern KMP Gallery Feature Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement dynamic light/dark theming, Google Lens visual search, native wallpaper setting, zero-trace metadata stripping (images & videos), a viewer contextual actions sheet, timeline media category filters, and a luxury adaptive app icon.

**Architecture:** A cross-platform UI layer in Compose Multiplatform backed by reactive Kotlin `Flow`s from `LocalMetadataStore` and `TimelineViewModel`. Platform-specific capabilities (Google Lens intents, WallpaperManager, and media file stream sanitization) are abstracted via clean interfaces (`PlatformActionHandler`, `MetadataStripper`) and implemented natively for Android.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, AndroidX ExifInterface, Android WallpaperManager, Kotlin Coroutines & Flow.

**Spec:** [`docs/superpowers/specs/2026-11-03-modern-kmp-gallery-feature-expansion.md`](file:///home/sensei/Projects/multiplatform/Gallery/docs/superpowers/specs/2026-11-03-modern-kmp-gallery-feature-expansion.md)

## Global Constraints

- Must compile cleanly for Android (`compileReleaseKotlin`, `assembleRelease`) and iOS simulator arm64 targets without build errors.
- Never duplicate photos in the gallery when sanitizing for sharing; zero-trace sharing uses temporary cache streams.
- Light mode must maintain high WCAG AAA contrast ratio with slate typography (`#0F172A`) over frosted white surfaces.
- Safe window insets (`statusBars`, `navigationBars`) must be strictly preserved across all new sheets and dialogs.

## Review Focus

1. Google Lens failure modes: If Google Lens is not installed on the user device, fallback gracefully to Google Search app or browser without crashing.
2. In-place metadata stripping on Android 10+: Handle scoped storage / MediaStore edit permissions gracefully.
3. Light/Dark theme switching: Dynamic live update across all visible screens without requiring app restart.
4. Large video metadata stripping: Avoid loading entire video files into memory; strip box atoms via streaming I/O.
5. Home/Lock wallpaper selection: Ensure system wallpaper intent allows selecting Home screen, Lock screen, or Both.

---

### Task 1: Theme Mode Domain & Dynamic Palette Tokens

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/theme/ThemeMode.kt`
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/local/LocalMetadataStore.kt`
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/theme/Theme.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/theme/ThemeModeTest.kt`

- [ ] **Step 1: Write unit tests for ThemeMode persistence in LocalMetadataStore**
  Create `ThemeModeTest.kt` verifying default `SYSTEM`, toggling between `LIGHT`, `DARK`, and `SYSTEM`, and reactive flow emissions.
- [ ] **Step 2: Run test to confirm failure**
  Run `./gradlew test --tests "*ThemeModeTest*" --no-daemon`.
- [ ] **Step 3: Implement ThemeMode & LocalMetadataStore updates**
  Define `enum class ThemeMode { SYSTEM, DARK, LIGHT }` and add `themeMode: StateFlow<ThemeMode>` with `setThemeMode(mode: ThemeMode)` and `cycleThemeMode()` in `LocalMetadataStore`.
- [ ] **Step 4: Update GalleryTheme with light color palette**
  Expand `Theme.kt` to define `GalleryLightColors` alongside `GalleryDarkColors`, supporting dynamic theming via `themeMode` state.
- [ ] **Step 5: Run tests and verify success**
  Run `./gradlew test --tests "*ThemeModeTest*" --no-daemon`.
- [ ] **Step 6: Commit changes**
  Commit with `git commit -am "feat(theme): implement ThemeMode and light theme palette"`.

---

### Task 2: Metadata Stripper Domain Contract & Unit Tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/security/MetadataStripper.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/security/FakeMetadataStripper.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/security/MetadataStripperTest.kt`

- [ ] **Step 1: Write unit test for MetadataStripper contract and summary**
  Create `MetadataStripperTest.kt` verifying sanitization results for images and videos with simulated metadata tags and clean URI outputs.
- [ ] **Step 2: Run test to confirm failure**
  Run `./gradlew test --tests "*MetadataStripperTest*" --no-daemon`.
- [ ] **Step 3: Implement MetadataStripper and FakeMetadataStripper**
  Define `SanitizationSummary` and `interface MetadataStripper`. Implement `FakeMetadataStripper` for deterministic test verification.
- [ ] **Step 4: Run tests and verify success**
  Run `./gradlew test --tests "*MetadataStripperTest*" --no-daemon`.
- [ ] **Step 5: Commit changes**
  Commit with `git commit -am "feat(security): metadata stripper interface and test driver"`.

---

### Task 3: Android Platform Subsystems (Google Lens, Wallpaper & Metadata Stripper)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/platform/PlatformActionHandler.kt`
- Create: `shared/src/androidMain/kotlin/com/ayoubzulfiqar/gallery/platform/AndroidPlatformActionHandler.kt`
- Create: `shared/src/androidMain/kotlin/com/ayoubzulfiqar/gallery/security/AndroidMetadataStripper.kt`
- Modify: `androidApp/src/main/kotlin/com/ayoubzulfiqar/gallery/MainActivity.kt`

- [ ] **Step 1: Implement AndroidMetadataStripper**
  Implement image EXIF sanitization using `ExifInterface` (stripping GPS, camera model, author, and device serials). Implement MP4 user data / GPS atom stripping. Support both `inPlace = true` and temporary cache share paths.
- [ ] **Step 2: Implement AndroidPlatformActionHandler**
  Implement `launchGoogleLens` (targeted intent with fallback to Google Search Lens and web), `setAsWallpaper` (`WallpaperManager.getCropAndSetWallpaperIntent` / `ACTION_ATTACH_DATA`), and `shareSanitized`.
- [ ] **Step 3: Inject handlers in MainActivity**
  Pass `AndroidPlatformActionHandler` and `AndroidMetadataStripper` from `MainActivity.kt` into `App.kt` via CompositionLocal or parameter injection.
- [ ] **Step 4: Verify compilation on Android**
  Run `./gradlew :shared:compileAndroidMain --no-daemon`.
- [ ] **Step 5: Commit changes**
  Commit with `git commit -am "feat(android): implement Google Lens, Wallpaper, and MetadataStripper bridges"`.

---

### Task 4: UI Icons & Contextual "More Options" Sheet

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/theme/GalleryIcons.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/ViewerMoreSheet.kt`
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/LightboxViewerScreen.kt`

- [ ] **Step 1: Add new vector icons to GalleryIcons**
  Add `Lens` (Google Lens camera search glyph), `Wallpaper` (device screen framing glyph), `ShieldCheck` (sanitization shield), `Sun` (light mode), `Moon` (dark mode), `AutoTheme` (split circle), and `MoreHorizontal` (3 dots).
- [ ] **Step 2: Create ViewerMoreSheet composable**
  Build frosted glass bottom sheet presenting: Google Lens visual search, Set as Wallpaper, Sanitize & Share, Strip Metadata In-Place, Move to Vault, and View Details.
- [ ] **Step 3: Wire More button and sheet into LightboxViewerScreen**
  Add `···` button to viewer chrome opening `ViewerMoreSheet` and routing actions to `PlatformActionHandler` and `MetadataStripper`.
- [ ] **Step 4: Verify compilation**
  Run `./gradlew :shared:compileAndroidMain --no-daemon`.
- [ ] **Step 5: Commit changes**
  Commit with `git commit -am "feat(ui): add ViewerMoreSheet and vector icons for Lens, Wallpaper, and Security"`.

---

### Task 5: Timeline Category Filters & Top Header Theme Toggle

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/App.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineViewModelTest.kt`

- [ ] **Step 1: Write unit tests for timeline category filter in TimelineViewModel**
  Add test in `TimelineViewModelTest.kt` verifying filtering by `ALL`, `PHOTOS`, `VIDEOS`, and `FAVORITES`.
- [ ] **Step 2: Implement category filter in TimelineViewModel**
  Add `TimelineCategoryFilter` enum, state flow `categoryFilter`, and combine filter into `uiState`.
- [ ] **Step 3: Add category filter chips and theme toggle button to TimelineScreen**
  Render horizontal filter chips under "Photos" header. Add Sun/Moon theme toggle button cycling themes in `LocalMetadataStore`.
- [ ] **Step 4: Run unit tests**
  Run `./gradlew test --tests "*TimelineViewModelTest*" --no-daemon`.
- [ ] **Step 5: Commit changes**
  Commit with `git commit -am "feat(timeline): add category filters and live theme toggle"`.

---

### Task 6: Luxury Adaptive App Icon

**Files:**
- Modify: `androidApp/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml`
- Create: `androidApp/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

- [ ] **Step 1: Design luxury vector background**
  Create rich obsidian gradient background with radial twilight aura in `ic_launcher_background.xml`.
- [ ] **Step 2: Design multi-layer refractive aperture foreground**
  Create precision optical aperture blades with neon cyan and electric violet refraction in `ic_launcher_foreground.xml`.
- [ ] **Step 3: Create Material You monochrome icon**
  Create clean outline vector in `ic_launcher_monochrome.xml` for dynamic themed icons.
- [ ] **Step 4: Update adaptive launcher XMLs**
  Reference foreground, background, and monochrome elements in `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`.
- [ ] **Step 5: Commit changes**
  Commit with `git commit -am "design(icon): luxury adaptive launcher icon with Material You monochrome support"`.

---

### Task 7: Full Release Build & Physical On-Device Verification

**Files:**
- Target: `323962490482`
- Script / Commands: `assembleRelease`, `adb install`, `adb shell uiautomator`, `adb exec-out screencap`

- [ ] **Step 1: Run full unit test suite**
  Execute `./gradlew test --no-daemon` ensuring 100% pass rate.
- [ ] **Step 2: Build release APK**
  Execute `./gradlew assembleRelease --no-daemon`.
- [ ] **Step 3: Install onto physical device**
  Install release APK onto device `323962490482` via `adb install -r -d`.
- [ ] **Step 4: Interactive verification on device**
  - Toggle between Light and Dark mode; verify contrast and frosted glass aesthetics.
  - Test timeline category filters (Photos, Videos, Favorites).
  - Open Lightbox viewer and trigger Google Lens.
  - Trigger "Set as Wallpaper" and verify system wallpaper chooser.
  - Test "Sanitize & Share" and verify zero duplicate images created.
  - Inspect launcher app icon on home screen.
- [ ] **Step 5: Capture evidence screenshots and logcat**
  Record verification screenshots and verify zero logcat crashes.
- [ ] **Step 6: Generate final verification report and commit**
  Create report artifact and commit any final adjustments to `main`.
