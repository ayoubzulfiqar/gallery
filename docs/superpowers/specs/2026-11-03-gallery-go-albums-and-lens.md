# Specification: Google Gallery Go Redesign & Folder-Based Albums

## 1. Objective
Bring Google Gallery Go parity to the Kotlin Multiplatform Gallery app:
1. **Direct Google Lens Launch:** Bypass all system share dialogs by directly invoking Google Search Image (`com.google.android.apps.search.lens.LensShareEntryPointActivity`).
2. **Video Playback (Option A):** Directly launch device's default video player (`Intent.ACTION_VIEW` for `video/*`) with permission grant when a video is tapped.
3. **Gallery Go-Style Month Grouping:** Group Photos/All timeline by Month ("November 2026", "October 2026", etc.) with quick-jump folder carousel at the top (Screenshots, Videos, Camera).
4. **Folder-Based Albums Tab:** Query `BUCKET_DISPLAY_NAME` from Android MediaStore, present rich 2-column folder cards (Camera, Screenshots, Twitter, WhatsApp, Downloads, etc.) with live cover thumbnails and item counts, and enable navigating into folder details.

---

## 2. Architecture & Components

### 2.1 MediaStore Folder Discovery
* **Columns:** Add `FileColumns.BUCKET_DISPLAY_NAME` and `FileColumns.BUCKET_ID` to `AndroidPlatformMediaDataSource` projection.
* **Domain Model:**
  - Update [`MediaItem.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/model/MediaItem.kt) with `bucketId: String? = null`, `bucketName: String? = null`.
  - Create [`MediaFolder.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/model/MediaFolder.kt):
    ```kotlin
    data class MediaFolder(
        val id: String,
        val name: String,
        val coverUri: String,
        val itemCount: Int,
        val items: List<MediaItem>
    )
    ```
* **Repository:** Add `getFoldersFlow(): Flow<List<MediaFolder>>` in `MediaRepository`.

### 2.2 Direct Google Lens Bridge & Video Player Launcher
* In [`PlatformActionHandler.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/platform/PlatformActionHandler.kt):
  - `launchGoogleLens(contentUri: String): Boolean`
  - `playVideo(contentUri: String): Boolean`
* In [`AndroidPlatformActionHandler.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/androidMain/kotlin/com/ayoubzulfiqar/gallery/platform/AndroidPlatformActionHandler.kt):
  - Target `com.google.android.apps.search.lens.LensShareEntryPointActivity` on `com.google.android.googlequicksearchbox` with `FLAG_GRANT_READ_URI_PERMISSION`.
  - Target `Intent(Intent.ACTION_VIEW)` with `video/*` and `FLAG_GRANT_READ_URI_PERMISSION` for `playVideo()`.

### 2.3 Gallery Go Month Grouping & Top Folder Carousel
* In [`GroupMediaByTimelineUseCase.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/usecase/GroupMediaByTimelineUseCase.kt):
  - Ensure `TimelineDensity.MONTHS` formats headers cleanly as "Month Year" (e.g. "November 2026", "October 2026", etc.).
* In [`TimelineScreen.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineScreen.kt):
  - Set default density to `TimelineDensity.MONTHS`.
  - Add top horizontal Quick-Folder Carousel: displaying squircle thumbnail cards for "Screenshots", "Videos", "Camera", and "WhatsApp" right above the month sections.

### 2.4 Folder-Based Albums Screen & Navigation
* Create [`AlbumsViewModel.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/albums/AlbumsViewModel.kt).
* Create [`AlbumsScreen.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/albums/AlbumsScreen.kt):
  - 2-column or 3-column grid of folders with live cover thumbnail (`AsyncMediaImage`), folder name, and count badge.
  - Search filter bar for instant folder filtering.
* Create [`FolderDetailScreen.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/albums/FolderDetailScreen.kt):
  - Shows header with folder title, back button, and item count.
  - Responsive media grid for only this folder's items.
  - Video items launch default player; photo items open Lightbox.
* In [`GalleryNavigation.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/navigation/GalleryNavigation.kt):
  - Add `data class FolderDetail(val bucketId: String, val folderName: String) : GalleryDestination()`.
* In [`App.kt`](file:///home/sensei/Projects/multiplatform/Gallery/shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/App.kt):
  - Route `GalleryDestination.Albums` to `AlbumsScreen`.
  - Route `GalleryDestination.FolderDetail` to `FolderDetailScreen`.

---

## 3. Verification Criteria
1. Unit tests pass: `./gradlew test --no-daemon`.
2. Assemble release APK: `./gradlew assembleRelease --no-daemon`.
3. Install onto physical device `323962490482`.
4. Verify on-device:
   - Tapping **Lens** directly launches Google Lens with 0 dialogs.
   - Tapping a **Video** directly launches the default video player.
   - Photos tab displays Month grouping and top folder quick carousel.
   - Albums tab displays device folders (Camera, Screenshots, Twitter, WhatsApp, etc.) with real covers and counts.
   - Tapping a folder opens its dedicated media view.
