# Implementation Plan: Google Gallery Go Redesign & Folder-Based Albums

## Proposed Plan
- **Task 1:** Domain Model & MediaStore Folder Discovery (Add `bucketId`, `bucketName`, `MediaFolder`, update `AndroidPlatformMediaDataSource` projection and queries).
- **Task 2:** Platform Action Handler Enhancements (Direct Google Lens targeting `LensShareEntryPointActivity` and `playVideo` targeting `ACTION_VIEW` for `video/*`).
- **Task 3:** Repository & Timeline Month Grouping (Add `getFoldersFlow()`, refine month headers and default density to `MONTHS`).
- **Task 4:** Top Quick-Access Carousel on Photos Screen (Screenshots, Videos, Camera with live covers).
- **Task 5:** Folder-Based Albums Screen & Folder Detail Screen (AlbumsScreen grid, search filter, and FolderDetailScreen navigation).
- **Task 6:** Video Click Routing (Option A: tap video opens default video player).
- **Task 7:** Full Verification on Physical Device (Build release, install on `323962490482`, test Lens, Videos, Month timeline, and Albums folders).

---

### Task 1: Domain Model & MediaStore Folder Discovery
**Files:**
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/model/MediaItem.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/model/MediaFolder.kt`
- Update: `shared/src/androidMain/kotlin/com/ayoubzulfiqar/gallery/data/datasource/AndroidPlatformMediaDataSource.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/datasource/FakePlatformMediaDataSource.kt`

**Actions:**
1. Add `bucketId: String? = null` and `bucketName: String? = null` to `MediaItem`.
2. Create `MediaFolder(id: String, name: String, coverUri: String, itemCount: Int, items: List<MediaItem>)`.
3. In `AndroidPlatformMediaDataSource`:
   - Add `FileColumns.BUCKET_DISPLAY_NAME` and `FileColumns.BUCKET_ID` to projection.
   - Read `bucket_display_name` and `bucket_id` when reading cursor.
4. Verify compilation with `./gradlew compileAndroidMain`.

---

### Task 2: Direct Google Lens Bridge & Video Player Launcher
**Files:**
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/platform/PlatformActionHandler.kt`
- Update: `shared/src/androidMain/kotlin/com/ayoubzulfiqar/gallery/platform/AndroidPlatformActionHandler.kt`

**Actions:**
1. In `PlatformActionHandler`:
   - Add `playVideo(contentUri: String): Boolean`.
2. In `AndroidPlatformActionHandler`:
   - For `launchGoogleLens`: Set component to `ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensShareEntryPointActivity")` with `ACTION_SEND` and `FLAG_GRANT_READ_URI_PERMISSION`.
   - For `playVideo`: Set `Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(contentUri), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)`.

---

### Task 3: Repository & Month Grouping
**Files:**
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/repository/MediaRepository.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/repository/DefaultMediaRepository.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/usecase/GroupMediaByTimelineUseCase.kt`

**Actions:**
1. In `MediaRepository`: Add `fun getFoldersFlow(): Flow<List<MediaFolder>>`.
2. In `DefaultMediaRepository`: Derive folders from `getMediaFlow()` by grouping by `bucketId ?: bucketName ?: "Default"`. Sort folders by latest item modification time, with "Camera" and "Screenshots" prioritized first.
3. In `GroupMediaByTimelineUseCase`:
   - For `TimelineDensity.MONTHS`: format header with full month name and year (e.g., "November 2026", "October 2026").
   - Set default density in `TimelineViewModel` to `TimelineDensity.MONTHS`.

---

### Task 4: Quick-Access Folder Carousel on Photos Screen
**Files:**
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineScreen.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineViewModel.kt`

**Actions:**
1. Expose `val quickFolders: StateFlow<List<MediaFolder>>` in `TimelineViewModel`.
2. In `TimelineScreen`: Add horizontal carousel below header displaying folder squircles (Screenshots, Videos, Camera, WhatsApp) with cover thumbnail and title.
3. Tapping a quick folder navigates to that folder's view or filters by it.

---

### Task 5: Folder-Based Albums Screen & Folder Detail Screen
**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/albums/AlbumsViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/albums/AlbumsScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/albums/FolderDetailScreen.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/navigation/GalleryNavigation.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/App.kt`

**Actions:**
1. In `GalleryNavigation`: Add `data class FolderDetail(val bucketId: String, val folderName: String) : GalleryDestination()`.
2. In `AlbumsScreen`: 2-column or 3-column folder cards with squircle `AsyncMediaImage` cover, folder title, item count badge, and a top search bar.
3. In `FolderDetailScreen`: Top app bar with back navigation, folder title, total count, and responsive media grid.
4. In `App.kt`: Wire `GalleryDestination.Albums` to `AlbumsScreen` and `GalleryDestination.FolderDetail` to `FolderDetailScreen`.

---

### Task 6: Video Click Routing (Option A)
**Files:**
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/App.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineScreen.kt`
- Update: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/LightboxViewerScreen.kt`

**Actions:**
1. When a `MediaType.VIDEO` item is tapped in `TimelineScreen` or `FolderDetailScreen`, call `activeActions.playVideo(item.uri)`.
2. In `LightboxViewerScreen`: Tapping the center video play button also launches `activeActions.playVideo(item.uri)`.

---

### Task 7: Build, Install, and On-Device Verification
**Actions:**
1. Run `./gradlew test --no-daemon`.
2. Run `./gradlew assembleRelease --no-daemon`.
3. Install onto physical device `323962490482`.
4. Launch app and verify:
   - Direct Google Lens: tap Lens pill -> directly opens Google Lens image analysis.
   - Video Playback: tap video -> launches default video player (Option A).
   - Photos tab: Month headers and top quick folder carousel.
   - Albums tab: Real device folders (Camera, Screenshots, Twitter, WhatsApp, Downloads, etc.) with covers and counts.
   - Tapping folder -> opens FolderDetailScreen.
5. Capture evidence screenshots and generate report artifact.
