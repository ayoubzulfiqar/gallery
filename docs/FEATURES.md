# Gallery Features & User Guide

A modern, fast, privacy-focused gallery application built with **Compose Multiplatform** targeting Android and iOS, designed with Google Gallery Go aesthetics, modern frosted glass UI, and military-grade AES-256-GCM vault security.

---

## 1. Feature Highlights

### 1.1 Photos Timeline (Gallery Go Style)
* **Chronological Grouping**: Media is organized into intuitive monthly sections ("November 2026", "October 2026", etc.) with sticky headers and item counts.
* **Top Quick Folders Carousel**: Horizontal carousel above the timeline for quick access to your most active folders:
  * Camera
  * Screenshots
  * WhatsApp Images
  * Game Space / Downloads
* **Pinch-to-Change Grid Density**:
  * **Years**: Ultra-dense micro thumbnails for high-level timeline browsing.
  * **Months**: Standard Gallery Go view (Default).
  * **Days**: Larger 3-column daily grid.
  * **Masonry**: Dynamic aspect-ratio staggered layout.
* **Category Filters**: Instant chips to filter by **All**, **Photos**, **Videos**, or **Favorites**.
* **Adaptive Theme**: Toggle between Light Mode and Dark Mode with persistent state across app launches.

---

### 1.2 Folder-Based Albums
* **Automatic MediaStore Discovery**: Automatically scans device storage folders (`bucket_display_name`).
* **Rich Folder Cards**: Displays dynamic live thumbnail covers, folder names, and item counts with modern **12.dp corner radius** matching the search bar.
* **Instant Filter Search**: Search bar allows instant real-time filtering across all device folders.
* **Folder Detail Navigation**: Tap any folder card to view its photos and videos with dedicated category chips and full system back navigation support.

---

### 1.3 Lightbox Media Viewer
* **Continuous Bidirectional Swiping**: Smooth `HorizontalPager` lets you swipe left and right continuously through all your media.
* **Centroid-Based Pinch Zoom**: Two-finger pinch to zoom around the exact focal point between your fingers.
* **Seamless Panning & Swiping**: Drag to pan with 1 finger when zoomed in; swipes smoothly to the next image when fully zoomed out.
* **Direct Google Lens**: Tap the **Lens** button to directly launch Google Lens image search without seeing Android share sheets.
* **Native Video Player**: Tap any video to launch your device's default video player (e.g., MX Player, VLC, Photos).
* **Frosted Glass Chrome**: Top and bottom control bars feature blur/frosted glass that adapts dynamically to Light and Dark mode.
* **Options & Tools Menu**:
  * Set as Wallpaper (Home / Lock screen).
  * Sanitize & Share (Strips GPS and camera metadata on-the-fly with 0 duplicates).
  * Sanitize In-Place (Removes EXIF tags directly from device file).
  * Technical EXIF Inspector (ISO, aperture, shutter speed, lens focal length, GPS location).
  * **Move to Encrypted Vault** (Encrypts and locks item with AES-256-GCM).

---

### 1.4 Encrypted Vault (AES-256-GCM)
* **Authenticated Encryption**: All vaulted media is encrypted using 256-bit AES-GCM with unique 12-byte random IVs and 16-byte authentication tags.
* **Light & Dark Mode**: Beautiful adaptive PIN entry keypad, indicator dots, and glowing lock badge.
* **PIN Protection**: Default PIN is `1234` (or `0000`).
* **Instant Isolation**: Moving a photo to the vault immediately hides it from the timeline, albums, and folder screens.
* **Inspection & Restoration**:
  * Tap any vaulted item inside the unlocked vault to open its preview dialog with file details.
  * Tap **"Restore to Gallery"** to decrypt the photo and return it to your public gallery timeline.
  * Tap **"Delete"** to permanently remove it.

---

### 1.5 System Back Navigation
* Integrated with Android's system back gesture and hardware back button.
* Pressing back inside a folder detail view, lightbox viewer, or vault returns cleanly to the previous screen rather than closing the application.
