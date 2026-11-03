# 📸 Compose Multiplatform Gallery

A high-performance, privacy-first, beautiful media gallery built with **Compose Multiplatform** targeting **Android** and **iOS**. Designed with Google Gallery Go aesthetics, modern frosted glass UI, responsive pinch-to-zoom gestures, and authenticated **AES-256-GCM** vault encryption.

---

## ✨ Key Features

### 📅 Gallery Go Month Grouping & Top Quick Folders
* **Chronological Month Sections**: Media is grouped by month ("November 2026", "October 2026") with sticky headers and media counts.
* **Top Quick Folders Carousel**: Horizontal carousel above the timeline for rapid access to active folders (Camera, Screenshots, WhatsApp, Downloads) with modern **12.dp border radius**.
* **Filter Chips**: Filter the entire timeline by **All**, **Photos**, **Videos**, or **Favorites** with a single tap.
* **Interactive Pinch-to-Density Gestures**: Pinch in or out to switch between **Years**, **Months** (Default), **Days**, and **Masonry** grid densities.

### 📁 Folder-Based Albums & Instant Search
* **Automatic MediaStore Bucket Discovery**: Scans device storage folders and generates 2-to-3 column folder cards.
* **Live Dynamic Covers**: Thumbnails reflect the newest media modified in that directory.
* **12.dp Border Radius**: Search bar, album cards, and photo thumbnails share a consistent, modern corner radius.
* **Folder Detail Navigation**: Open any album to view its media with dedicated category pills and clean back stack support.

### 🔍 Lightbox Media Viewer & Gestures
* **Continuous Bidirectional Swiping**: Built with Compose `HorizontalPager` to swipe left and right back-and-forth across photos and videos.
* **Centroid-Based Pinch-to-Zoom**: Two-finger pinch zooms precisely around the touch focal point (`ZoomableMediaBox`). Supports single-finger panning while zoomed in, and automatically delegates to horizontal paging when fully zoomed out.
* **Direct Google Lens Launch**: Tapping **Lens** directly opens Google Search by Image (`com.google.android.apps.search.lens.LensShareEntryPointActivity`) without system share dialogs.
* **Native Video Playback**: Automatically launches videos using your installed default video player (e.g., MX Player, VLC, Samsung Video, or Google Photos).
* **Adaptive Frosted Glass**: Dynamic translucent blurs that automatically adapt between Light Mode and Dark Mode.
* **Options & Tools Bottom Sheet**:
  * Set as Wallpaper (Home / Lock screen).
  * Sanitize & Share (Strips GPS and camera metadata on-the-fly).
  * Sanitize In-Place (Removes EXIF tags directly from device file).
  * Technical EXIF Inspector (ISO, aperture, shutter speed, focal length, location).
  * **Move to Encrypted Vault** (Encrypts with AES-256-GCM).

### 🔒 AES-256-GCM Encrypted Vault
* **Military-Grade Authenticated Encryption**: 256-bit AES in Galois/Counter Mode (GCM) with 12-byte random IVs and 16-byte authentication tags.
* **Adaptive Light & Dark UI**: Beautiful keypad with custom circular keys, glowing lock icon, and clear PIN indicator dots in both Light and Dark themes.
* **PIN Authentication**: Default PIN is `1234` (or `0000`).
* **Instant Gallery Isolation**: Moving photos to the Vault encrypts them into a secure sandbox and immediately hides them from Photos, Albums, and Folders.
* **Inspect & Restore**: Tap any vaulted photo to preview its details, permanently delete it, or tap **"Restore to Gallery"** to decrypt and return it to your public gallery timeline.

### 🔙 Native System Back Navigation
* Integrated with Android's system back gesture and hardware back button via multiplatform `GalleryBackHandler`.
* Navigating folders, viewing images, or opening sheets will cleanly pop back one level instead of closing the application.

---

## 🛠️ Architecture & Tech Stack

This project strictly adheres to **Clean Architecture** principles and **Unidirectional Data Flow (UDF)**:

* **Compose Multiplatform**: Declarative UI shared across Android and iOS.
* **Kotlin Coroutines & StateFlow**: Asynchronous data pipelines and reactive UI states.
* **Coil 3 Multiplatform**: High-performance image loading, decoding, memory caching, and disk caching with Android ContentUri support.
* **AES-256-GCM Crypto**: Platform-native cryptographic implementations via Java `Cipher` on Android and Apple `CommonCrypto` / CryptoKit on iOS.
* **Android MediaStore**: Real on-device media querying with dynamic projections and bucket categorization.
* **Detailed Architecture Docs**: See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/FEATURES.md](docs/FEATURES.md).

---

## 📂 Project Structure

```
Gallery/
├── androidApp/                  # Android application module
│   └── src/main/                # AndroidManifest, MainActivity, launcher icons
├── iosApp/                      # iOS application module (Xcode project & SwiftUI shell)
├── shared/                      # Kotlin Multiplatform core module
│   ├── src/commonMain/kotlin/
│   │   └── com/ayoubzulfiqar/gallery/
│   │       ├── data/            # Repositories, LocalMetadataStore, Data Sources
│   │       ├── domain/          # MediaItem, MediaFolder, VaultEntry, UseCases
│   │       ├── platform/        # Native bridge interfaces (Lens, Video Player)
│   │       ├── ui/
│   │       │   ├── albums/      # AlbumsScreen, FolderDetailScreen, AlbumsViewModel
│   │       │   ├── common/      # AsyncMediaImage, GalleryBackHandler
│   │       │   ├── gesture/     # PinchDensityState, TimelineDensity
│   │       │   ├── navigation/  # GalleryNavState, GalleryDestination
│   │       │   ├── theme/       # GalleryTheme, FrostedGlass, GalleryIcons
│   │       │   ├── timeline/    # TimelineScreen, TimelineViewModel, MediaThumbnailItem
│   │       │   ├── vault/       # VaultScreen, VaultViewModel
│   │       │   └── viewer/      # LightboxViewerScreen, ZoomableMediaBox, ViewerMoreSheet
│   │       └── vault/           # AesGcmCipher, DefaultVaultRepository
│   ├── src/androidMain/         # Android MediaStore source, back handler, native actions
│   └── src/iosMain/             # iOS platform implementations
└── docs/                        # Architecture documentation, feature specs & screenshots
    ├── ARCHITECTURE.md
    ├── FEATURES.md
    └── screenshots/
```

---

## 🚀 Getting Started

### Prerequisites
* JDK 17 or higher
* Android SDK (API 34+ recommended; tested on Android 16 / SDK 36)
* Xcode 15+ (for iOS build)

### Build Commands

* **Run all Multiplatform Tests**:
  ```bash
  ./gradlew :shared:allTests --no-daemon
  ```

* **Build Android Debug APK**:
  ```bash
  ./gradlew :androidApp:assembleDebug --no-daemon
  ```

* **Build Android Release APK**:
  ```bash
  ./gradlew assembleRelease --no-daemon
  ```

* **Install onto Connected Android Device**:
  ```bash
  adb install -r -d androidApp/build/outputs/apk/release/androidApp-release.apk
  adb shell am start -S -n com.ayoubzulfiqar.gallery/.MainActivity
  ```

---

## 📄 License

This project is licensed under the Apache 2.0 License.
