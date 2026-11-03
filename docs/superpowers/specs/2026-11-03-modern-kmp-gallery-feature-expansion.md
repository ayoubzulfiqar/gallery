# Architectural Specification: Modern KMP Gallery Feature Expansion

**Document:** `docs/superpowers/specs/2026-11-03-modern-kmp-gallery-feature-expansion.md`  
**Date:** 2026-11-03  
**Status:** PROPOSED FOR REVIEW  
**Scope:** Dynamic Light/Dark Theming, Google Lens Integration, Native Wallpaper Engine, Zero-Trace Security Metadata Stripper, Contextual More-Actions Sheet, Timeline Media Filters, and Luxury Adaptive App Icon.

---

## 1. Executive Summary & Goals

This specification extends the existing Kotlin Multiplatform Gallery application with high-demand professional features, enhanced security privacy tooling, and refined visual identity:
1. **Dynamic Theming System:** Full support for `ThemeMode.LIGHT`, `ThemeMode.DARK`, and `ThemeMode.SYSTEM` with smooth real-time toggling, frosted glass adaptation, and high-contrast typography.
2. **Google Lens Platform Integration:** Seamless one-tap image scanning into Google Lens via native Android intent with graceful fallbacks.
3. **Native Wallpaper Engine:** Direct system wallpaper configuration (Home Screen, Lock Screen, or Both) via Android `WallpaperManager`.
4. **Zero-Trace Security Metadata Stripper:** Dual-path privacy engine that wipes EXIF, GPS, camera identifiers, and video location atoms without causing gallery clutter:
   - *Sanitize & Share:* Strips metadata on-the-fly and feeds clean streams to the Android share sheet (zero gallery duplicates).
   - *Clean In-Place:* Safely sanitizes original files upon explicit user request.
5. **Contextual Action Sheet & Timeline Media Filters:** An intuitive `··· More` action sheet in the lightbox viewer and reactive category filters (`All`, `Photos`, `Videos`, `Favorites`) in the main timeline.
6. **Luxury Adaptive App Icon:** Bespoke vector adaptive icon featuring deep obsidian gradients, multi-layer optical aperture prism geometry, and Android 13+ Material You monochrome themed icon support.

---

## 2. Dynamic Theming Architecture

### 2.1 Theme Data Flow & Persistence
- [`ThemeMode`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/theme/ThemeMode.kt) enum:
  ```kotlin
  enum class ThemeMode {
      SYSTEM,
      DARK,
      LIGHT
  }
  ```
- Stored and observed in [`LocalMetadataStore`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/local/LocalMetadataStore.kt) via a persistent `StateFlow<ThemeMode>`.
- Header toggle in [`TimelineScreen`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineScreen.kt) cycles between modes with animated icon feedback (`GalleryIcons.Sun`, `GalleryIcons.Moon`, `GalleryIcons.AutoTheme`).

### 2.2 Color Tokens & Contrast Matrix

| Semantic Token | Dark Mode Palette | Light Mode Palette |
| :--- | :--- | :--- |
| **Canvas Background** | `#0B0F12` (Obsidian) | `#F8FAFC` (Slate 50) |
| **Surface Elev. 1** | `#161B20` (Dark Slate) | `#FFFFFF` (Pure White) |
| **Frosted Chrome Tint** | `Color.Black.copy(alpha = 0.55f)` | `Color.White.copy(alpha = 0.78f)` |
| **Chrome Border** | `Color.White.copy(alpha = 0.12f)` | `Color.Black.copy(alpha = 0.08f)` |
| **Primary Text** | `Color.White` | `#0F172A` (Slate 900) |
| **Secondary Text** | `Color.White.copy(alpha = 0.65f)` | `#475569` (Slate 600) |
| **Accent Primary** | `#00B4D8` (Neon Cyan) | `#0096C7` (Electric Blue) |
| **Destructive / Error** | `#FF453A` (Vibrant Coral) | `#DC2626` (Crimson) |

---

## 3. Platform Actions & External Integration Bridge

### 3.1 Multiplatform Abstraction
An interface [`PlatformActionHandler`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/platform/PlatformActionHandler.kt) provided to Composables via `LocalPlatformActions`:

```kotlin
interface PlatformActionHandler {
    /** Launches Google Lens or system visual search for the media content. */
    fun launchGoogleLens(contentUri: String): Boolean

    /** Opens system wallpaper chooser to set media as Home, Lock, or Both. */
    fun setAsWallpaper(contentUri: String)

    /** Shares media cleanly after stripping all GPS and personal metadata. */
    suspend fun shareSanitized(contentUri: String, mimeType: String)
}
```

### 3.2 Android Implementation (`AndroidPlatformActionHandler`)
1. **Google Lens Dispatcher:**
   - Prepares content URI with `FLAG_GRANT_READ_URI_PERMISSION`.
   - Fires targeted Intent `Intent.ACTION_VIEW` with MIME type `image/*` directed at `com.google.ar.lens`.
   - Fallback 1: Package `com.google.android.googlequicksearchbox` (`com.google.android.apps.search.lens.LensActivity`).
   - Fallback 2: Intent `android.intent.action.SEND` with visual search action or browser query `https://lens.google.com/uploadbyurl`.
2. **Wallpaper Dispatcher:**
   - Queries `WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)`.
   - If available, launches chooser directly; otherwise creates `Intent(Intent.ACTION_ATTACH_DATA)` setting data and type `image/*` with `EXTRA_FROM_RESOURCE` flag.

---

## 4. Security & Privacy: Zero-Trace Metadata Stripper

### 4.1 Architecture & Contract
Interface [`MetadataStripper`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/security/MetadataStripper.kt):

```kotlin
data class SanitizationSummary(
    val cleanUri: String,
    val strippedTagsCount: Int,
    val removedGps: Boolean,
    val isTempShareFile: Boolean
)

interface MetadataStripper {
    suspend fun stripImageMetadata(sourceUri: String, inPlace: Boolean): SanitizationSummary
    suspend fun stripVideoMetadata(sourceUri: String, inPlace: Boolean): SanitizationSummary
}
```

### 4.2 Sanitization Logic
1. **Images (JPEG, PNG, WebP):**
   - Parse EXIF attributes with Android `ExifInterface`.
   - Erase location coordinates: `TAG_GPS_LATITUDE`, `TAG_GPS_LONGITUDE`, `TAG_GPS_ALTITUDE`, `TAG_GPS_TIMESTAMP`, `TAG_GPS_PROCESSING_METHOD`, `TAG_GPS_DATESTAMP`.
   - Erase hardware identifiers: `TAG_MAKE`, `TAG_MODEL`, `TAG_DEVICE_SETTING_DESCRIPTION`, `TAG_SERIAL_NUMBER`, `TAG_BODY_SERIAL_NUMBER`, `TAG_LENS_SERIAL_NUMBER`.
   - Erase software and user info: `TAG_SOFTWARE`, `TAG_ARTIST`, `TAG_COPYRIGHT`, `TAG_USER_COMMENT`.
   - Save clean stream either directly in-place or to `context.cacheDir/sanitized/clean_[timestamp].jpg` for zero-duplicate sharing.
2. **Videos (MP4):**
   - Traverse MP4 atom container tree to detect user data (`udta`) and ISO location (`©xyz`) boxes.
   - Zero out or excise location track markers, preventing GPS leakage when sharing.

---

## 5. UI/UX Enhancements

### 5.1 Contextual "More Options" Sheet ([`ViewerMoreSheet`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/ViewerMoreSheet.kt))
Accessible from the Lightbox Viewer via a `···` button:
- 🔍 **Google Lens Search**
- 🖼 **Set as Wallpaper**
- 🛡 **Sanitize & Share (No Duplicates)**
- 🧹 **Strip Metadata In-Place**
- 🔒 **Move to Encrypted Vault**
- ℹ **Details & EXIF**

### 5.2 Timeline Category Filters
Filter chip row positioned under the "Photos" header:
- `All` (All media)
- `Photos` (Only images)
- `Videos` (Only videos with duration badges)
- `Favorites` (Starred media)

### 5.3 Luxury Adaptive App Icon
- **Vector Background:** Deep dark gradient (`#0B0F12` to `#16202A`).
- **Vector Foreground:** Precision-cut optical lens / camera aperture with neon cyan (`#00B4D8`) and electric violet (`#7928CA`) refractive blades.
- **Monochrome Asset:** Minimalist high-contrast aperture glyph for Android 13+ dynamic Material You theme matching.

---

## 6. Verification & Test Plan

1. **Unit Testing:**
   - Test `LocalMetadataStore` theme mode state persistence and toggling.
   - Test timeline category filtering logic with synthetic and media items.
   - Test `MetadataStripper` sanitization output contracts.
2. **On-Device Interactive Verification:**
   - Verify Light Mode UI contrast, frosted glass surfaces, and readability on physical screen.
   - Test Google Lens invocation with real on-device media.
   - Test "Set as Wallpaper" invoking system chooser.
   - Test "Sanitize & Share" sharing clean file without duplicate creation.
   - Verify updated adaptive launcher icon on phone home screen.
   - Validate logcat for 0 crashes or exceptions.
