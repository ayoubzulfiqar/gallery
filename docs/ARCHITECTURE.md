# Gallery Architecture Documentation

This document describes the architectural design, layer boundaries, data flow, cryptography, gesture handling, and platform integrations of the **Compose Multiplatform Gallery** application for Android and iOS.

---

## 1. Architectural Overview

The application follows **Clean Architecture** principles combined with **Unidirectional Data Flow (UDF)** using Compose Multiplatform, Kotlin Coroutines, and StateFlow:

```
┌─────────────────────────────────────────────────────────────┐
│                       UI / Presentation Layer               │
│  - Compose Multiplatform Screens (Timeline, Albums, Viewer) │
│  - ViewModels (Timeline, Albums, Viewer, Vault, Editor)      │
│  - Theme & Frosted Glass Shaders (Light & Dark Mode)        │
│  - GalleryBackHandler (Platform Android BackHandler / iOS)   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Observes Flows / Dispatches Intents
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                         Domain Layer                        │
│  - Models: MediaItem, MediaFolder, VaultEntry, ExifMetadata  │
│  - Use Cases: GroupMediaByTimelineUseCase                   │
│  - Repository Interfaces: MediaRepository, VaultRepository  │
└──────────────────────────────┬──────────────────────────────┘
                               │ Implements Interfaces
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                          Data Layer                         │
│  - Repositories: DefaultMediaRepository, VaultRepository    │
│  - Local Store: LocalMetadataStore (Favorites, Tags, Themes)│
│  - Platform Data Source: AndroidPlatformMediaDataSource     │
│  - Cryptography: AesGcmCipher (AES-256-GCM Authenticated)   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Layer Details

### 2.1 UI Layer (`shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/`)

* **Navigation (`ui/navigation/GalleryNavigation.kt`):**
  * `GalleryNavState` manages an in-memory stack of `GalleryDestination` (`Timeline`, `Albums`, `FolderDetail`, `Viewer`, `Editor`, `Vault`).
  * Supports `navigateTo`, `navigateBack`, and `replaceWith` for seamless transitional flows (e.g. moving an item to the Vault replaces the Lightbox Viewer with the Vault destination).
* **System Back Handling (`ui/common/GalleryBackHandler.kt`):**
  * Multiplatform `expect fun GalleryBackHandler` with `actual` implementation in Android using `androidx.activity.compose.BackHandler`.
  * Intercepts system back swipe/button presses while inside folder details, image viewer, or active sheets to cleanly pop screens instead of exiting the application.
* **Timeline (`ui/timeline/TimelineScreen.kt`):**
  * Google Gallery Go style month headers ("November 2026", "October 2026", etc.) displaying media counts.
  * Top quick-folder carousel (Camera, Screenshots, WhatsApp, Downloads) with 12.dp rounded corners.
  * Interactive pinch-to-zoom grid density: Years (micro) ➔ Months (default) ➔ Days ➔ Masonry.
  * Category filter pills: All, Photos, Videos, Favorites.
  * Marquee multi-selection floating action bar.
* **Albums (`ui/albums/AlbumsScreen.kt` & `FolderDetailScreen.kt`):**
  * Real-time search bar with 12.dp corner radius.
  * 2-to-3 column folder cards representing device directories (Camera, Screenshots, WhatsApp, Downloads, etc.) with dynamic live covers and item counts.
  * Dedicated `FolderDetailScreen` isolating media for the chosen folder with internal sub-filtering.
* **Lightbox Viewer (`ui/viewer/LightboxViewerScreen.kt`):**
  * Continuous bidirectional swiping via Compose `HorizontalPager`.
  * Context-aware two-finger pinch zoom around touch centroid (`ZoomableMediaBox.kt`), enabling single-finger panning while zoomed, and smooth horizontal swipe delegation when scale reaches 1.0f.
  * Theme-adaptive frosted glass top and bottom control bars.
* **Encrypted Vault (`ui/vault/VaultScreen.kt`):**
  * Dynamic Light & Dark Mode support.
  * Secure PIN lock keypad (Default PIN: `1234`) with biometric/shield lock badge.
  * Encrypted items grid with full preview modal dialog and **"Restore to Gallery"** action.

---

### 2.2 Domain Layer (`shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/`)

* **Models (`domain/model/`):**
  * `MediaItem`: Represents image or video metadata (`id`, `uri`, `displayName`, `mimeType`, `mediaType`, `dateTakenMillis`, `sizeBytes`, `bucketId`, `bucketName`, `isFavorite`, `isVaulted`).
  * `MediaFolder`: Represents a device storage album/directory (`id`, `name`, `coverUri`, `itemCount`, `items`).
  * `VaultEntry`: Metadata for an encrypted sandbox asset (`id`, `originalId`, `encryptedFilePath`, `originalDisplayName`, `mimeType`, `sizeBytes`).
* **Use Cases (`domain/usecase/`):**
  * `GroupMediaByTimelineUseCase`: High-performance grouping of media items into chronological timeline buckets based on chosen density (`TimelineDensity.MONTHS`, `DAYS`, `YEARS`).
* **Repository Interfaces (`domain/repository/`):**
  * `MediaRepository`: Abstraction for observing media items, folders, favorites, tags, and deletion requests.
  * `VaultRepository`: Abstraction for vault lock state, PIN authentication, AES-GCM file movement, and file restoration.

---

### 2.3 Data Layer (`shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/`)

* **`DefaultMediaRepository` (`data/repository/DefaultMediaRepository.kt`):**
  * Combines platform media stream with local metadata (favorites, custom tags, and vaulted IDs).
  * Automatically filters out any items marked as vaulted, ensuring files moved to the Vault instantly disappear from Timeline, Albums, and Folders.
* **`LocalMetadataStore` (`data/local/LocalMetadataStore.kt`):**
  * In-memory and persistent state store for user preferences:
    * `ThemeMode` (`SYSTEM`, `LIGHT`, `DARK`)
    * Favorites (`Set<String>`)
    * Tags (`Map<String, Set<String>>`)
    * Vaulted IDs (`Set<String>`)
* **`AndroidPlatformMediaDataSource` (`shared/src/androidMain/...`):**
  * Queries Android `MediaStore.Images.Media` and `MediaStore.Video.Media`.
  * Reads `_ID`, `DISPLAY_NAME`, `MIME_TYPE`, `MEDIA_TYPE`, `DATE_MODIFIED`, `WIDTH`, `HEIGHT`, `SIZE`, `BUCKET_ID`, and `BUCKET_DISPLAY_NAME`.
  * Formats standard content URIs (`content://media/external/images/media/...`).

---

## 3. Cryptography & Vault Security

The Vault uses **AES-256-GCM** (Galois/Counter Mode) authenticated encryption:

* **Cipher Engine (`vault/crypto/AesGcmCipher.kt`):**
  * Key length: 256 bits (32 bytes).
  * Initialization Vector (IV): 12 bytes randomly generated per file using cryptographically secure random (`SecureRandom`).
  * Authentication Tag: 128 bits (16 bytes) verified automatically by GCM upon decryption.
  * Payload format: `[ 12-byte IV ] + [ Ciphertext with appended 16-byte GCM Tag ]`.
* **Zero-Knowledge Architecture:**
  * When locked, vaulted files cannot be listed or decrypted.
  * Moving files to the vault encrypts them immediately, deposits them into the secure encrypted store, and completely hides them from the public gallery flow.
  * Vault restoration re-authenticates the payload and unvaults the file back to the public timeline.

---

## 4. Platform Interop & Native Bridges

* **Direct Google Lens Integration:**
  * Invokes `com.google.android.apps.search.lens.LensShareEntryPointActivity` directly with `FLAG_GRANT_READ_URI_PERMISSION`.
  * Fallbacks gracefully to Google App lens search and browser image search.
* **Native Video Playback:**
  * Launches `Intent(Intent.ACTION_VIEW)` with MIME `video/*` and read URI permissions.
  * Automatically opens the user's preferred video player (e.g. MX Player, VLC, Samsung Video, or Google Photos).
* **Frosted Glass Shaders:**
  * Uses `Modifier.blur()` where hardware rendering supports it, with automatic fallback to high-opacity frosted blur in both Light Mode (`#FFFFFFEB`) and Dark Mode (`#161B20DC`).
