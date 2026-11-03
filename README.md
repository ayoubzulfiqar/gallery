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
  * Share without metadata (lossless for JPEG/PNG, remuxed for video; location and camera details removed).
  * Remove metadata from original (JPEG/PNG, with system write consent and automatic rollback).
  * Technical EXIF Inspector (ISO, aperture, shutter speed, focal length, location).
  * **Move to Encrypted Vault** (Encrypts with AES-256-GCM).

### 🔒 AES-256-GCM Encrypted Vault
* **Envelope Encryption**: Every item is encrypted with streaming AES-256-GCM under a random data key; the key is wrapped by your PIN (PBKDF2-HMAC-SHA256) and sealed by a hardware-backed Android Keystore key, so vault files are useless off-device.
* **Adaptive Light & Dark UI**: Beautiful keypad with custom circular keys, glowing lock icon, and clear PIN indicator dots in both Light and Dark themes.
* **PIN Protection**: You create a 4–8 digit PIN on first use (weak sequences are rejected). Wrong attempts trigger a persistent, escalating lockout. The vault locks automatically when the app goes to the background, and screenshots are blocked while it is open.
* **Safe Transfers**: Items are encrypted first and the original is deleted only after you approve the system prompt. If you decline, the vault copy is rolled back, so nothing is lost or duplicated.
* **Inspect & Restore**: Preview photos in memory only, permanently delete items (with confirmation), or restore them byte-for-byte to their original folder.

### 🔙 Native System Back Navigation
* Integrated with Android's system back gesture and hardware back button via multiplatform `GalleryBackHandler`.
* Navigating folders, viewing images, or opening sheets will cleanly pop back one level instead of closing the application.

---

## 🛠️ Architecture & Tech Stack

This project strictly adheres to **Clean Architecture** principles and **Unidirectional Data Flow (UDF)**:

* **Compose Multiplatform**: Declarative UI shared across Android and iOS.
* **Kotlin Coroutines & StateFlow**: Asynchronous data pipelines and reactive UI states.
* **Image Loading**: Pluggable `PlatformImageLoader` with a shared LRU cache; Android uses MediaStore thumbnails and `ImageDecoder` (EXIF orientation, HEIC) with bounded parallel decoding.
* **AES-256-GCM Crypto**: JCA (Conscrypt/BoringSSL) on Android; a portable engine on iOS verified against NIST/FIPS vectors and cross-checked with JCA.
* **Android MediaStore**: Debounced background queries, consent-based trash/delete on Android 11+, and pending-insert imports.
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
│   │       ├── domain/          # Models, VaultTransferCoordinator, timeline grouping
│   │       ├── platform/        # Native bridge interfaces (Lens, Video Player)
│   │       ├── ui/
│   │       │   ├── albums/      # AlbumsScreen, FolderDetailScreen, AlbumsViewModel
│   │       │   ├── common/      # Image loading, dialogs, GalleryBackHandler
│   │       │   ├── editor/      # PhotoEditorScreen, PhotoEditorViewModel
│   │       │   ├── permission/  # Permission and partial-access UI
│   │       │   ├── navigation/  # GalleryNavState, GalleryDestination
│   │       │   ├── theme/       # GalleryTheme, FrostedGlass, GalleryIcons
│   │       │   ├── timeline/    # TimelineScreen, TimelineViewModel, MediaThumbnailItem
│   │       │   ├── vault/       # VaultScreen, VaultViewModel
│   │       │   └── viewer/      # LightboxViewerScreen, ZoomableMediaBox, ViewerMoreSheet
│   │       ├── core/            # crypto (AES-GCM, streaming AEAD, KDFs), io
│   │       ├── security/        # Lossless JPEG/PNG metadata strippers
│   │       └── vault/           # DefaultVaultRepository, storage abstractions
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
  ./gradlew :shared:testAndroidHostTest
  ```

* **Build Android Debug APK**:
  ```bash
  ./gradlew :androidApp:assembleDebug --no-daemon
  ```

* **Build Android Release APK**:
  ```bash
  ./gradlew assembleRelease --no-daemon
  ```

* **Measuring performance**: always judge smoothness on the **release** variant (R8-optimized, non-debuggable; signed with the debug key, so you can select it in Android Studio and press Run). Debug builds of Compose apps are several times slower by design. To get install-time optimization when sideloading, run:
  ```bash
  adb shell cmd package compile -m speed-profile -f com.ayoubzulfiqar.gallery
  ```

* **Install onto Connected Android Device**:
  ```bash
  adb install -r -d androidApp/build/outputs/apk/release/androidApp-release.apk
  adb shell am start -S -n com.ayoubzulfiqar.gallery/.MainActivity
  ```

---

## 📄 License

This project is licensed under the Apache 2.0 License.
