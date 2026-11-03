# Modern Kotlin Multiplatform Gallery App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade, highly optimal, and feature-rich Modern Gallery application in Kotlin Multiplatform (Compose Multiplatform) for Android and iOS with multi-density pinch timeline, spring swipe-to-dismiss viewer, non-destructive photo editor, and biometric encrypted vault.

**Architecture:** Layered Clean MVI architecture. Pure KMP domain and data models; interface-driven platform media engine (`MediaStore` on Android, `PhotoKit` on iOS); local SQLite metadata storage for favorites, custom tags, and albums; Coil 3 image decoding pipeline; and Compose Multiplatform UI with dynamic frosted glass and spring physics gestures.

**Tech Stack:** Kotlin Multiplatform 2.4.21, Compose Multiplatform 1.12.2, Material 3 1.12.0-alpha03, Coil 3, kotlinx-coroutines 1.10.1, kotlinx-datetime 0.6.2, kotlinx-serialization 1.8.0.

**Spec:** [docs/superpowers/specs/2026-11-03-modern-kmp-gallery-design.md](file:///home/sensei/Projects/multiplatform/Gallery/docs/superpowers/specs/2026-11-03-modern-kmp-gallery-design.md)

## Global Constraints

- **Language & Runtime:** Kotlin 2.4.21 with Kotlin Multiplatform Gradle plugin.
- **Android Constraints:** `compileSdk = 37`, `minSdk = 24`, `targetSdk = 37`, Java 11 bytecode target.
- **iOS Constraints:** iOS 15+ target, Compose Multiplatform iOS Simulator Arm64 & iOS Arm64 frameworks.
- **Dependency Baseline:** Use Compose Multiplatform 1.12.2 and Material 3 1.12.0-alpha03 as defined in `gradle/libs.versions.toml`.
- **Clean Architecture:** Domain and Business Logic in `commonMain` must never import Android `android.*` or iOS `platform.*` SDK packages. All hardware interactions pass through interfaces or `expect/actual` bridges.

## Review Focus

1. **Large Media Library Memory Exhaustion:** Loading 10,000+ items into Compose LazyGrid must not crash or cause GC pause stutters; windowed cursor pagination and downsampled thumbnail keys must be verified.
2. **Gesture Dismiss Boundary Clamping:** Rapid downward swipe-to-dismiss must smoothly spring back if release velocity is below 120dp and properly dismiss if above, never leaving the viewer stuck in a half-scaled blank state.
3. **Non-Destructive State Commutativity:** Applying color adjustments, rotation, and cropping must be commutative and reversible without quality degradation or modifying the source file.
4. **Encrypted Vault Ciphertext Integrity:** Tampered or truncated ciphertext in the private vault must fail cleanly with `VaultError.IntegrityCompromised` without leaking unencrypted byte fragments in memory.
5. **Partial Storage Permission Graceful Fallback:** If Android 14 `READ_MEDIA_VISUAL_USER_SELECTED` or iOS Limited Photo Access is granted, the app must display available media cleanly without crashing on inaccessible URIs.

---

### Task 1: Core Domain Entities, Enums & Repository Contracts

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/model/MediaModels.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/model/EditorModels.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/model/VaultModels.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/repository/MediaRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/repository/VaultRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/domain/MediaModelsTest.kt`

**Interfaces:**
- Produces:
  - `data class MediaItem(...)`
  - `enum class MediaType { IMAGE, VIDEO, RAW, GIF, BURST }`
  - `data class MediaTimelineGroup(val title: String, val dateRange: ClosedRange<Long>, val items: List<MediaItem>)`
  - `data class ExifMetadata(...)`
  - `data class ColorAdjustments(...)`, `data class CropRect(...)`, `data class EditRecipe(...)`
  - `interface MediaRepository`: `fun getMediaFlow(): Flow<List<MediaItem>>`, `suspend fun getTimelineGroups(): List<MediaTimelineGroup>`, `suspend fun toggleFavorite(id: String): Boolean`, `suspend fun getExif(id: String): ExifMetadata?`
  - `interface VaultRepository`: `suspend fun isVaultUnlocked(): Boolean`, `suspend fun unlockVault(pin: String): Boolean`, `suspend fun lockVault()`, `suspend fun getVaultItems(): List<MediaItem>`

- [ ] **Step 1: Write the failing domain models unit test**

```kotlin
package com.ayoubzulfiqar.gallery.domain

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.CropRect
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaModelsTest {
    @Test
    fun testMediaItemCreationAndDefaults() {
        val item = MediaItem(
            id = "media_1",
            uri = "content://media/1",
            displayName = "photo.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMillis = 1700000000000L,
            dateModifiedMillis = 1700000000000L,
            width = 4000,
            height = 3000,
            sizeBytes = 2048000L
        )
        assertEquals("media_1", item.id)
        assertFalse(item.isFavorite)
        assertFalse(item.isVaulted)
        assertEquals(0, item.orientation)
    }

    @Test
    fun testEditRecipeClampingAndDefaults() {
        val adjustments = ColorAdjustments(brightness = 0.5f, contrast = 1.2f)
        val recipe = EditRecipe(
            cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f),
            adjustments = adjustments,
            rotationDegrees = 90f
        )
        assertEquals(90f, recipe.rotationDegrees)
        assertEquals(0.5f, recipe.adjustments.brightness)
        assertEquals(1.2f, recipe.adjustments.contrast)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `com.ayoubzulfiqar.gallery.domain.model.*` does not exist yet.

- [ ] **Step 3: Implement domain models and repository interfaces**

Create the pure Kotlin models and repository interfaces in `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/` with exact types matching the spec.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/domain/
git commit -m "feat(domain): add core media, editor, and vault models with repository interfaces"
```

---

### Task 2: Platform Media Data Source Abstraction & In-Memory Test Driver

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/datasource/PlatformMediaDataSource.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/datasource/FakePlatformMediaDataSource.kt`
- Create: `shared/src/androidMain/kotlin/com/ayoubzulfiqar/gallery/data/datasource/AndroidPlatformMediaDataSource.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/data/FakePlatformMediaDataSourceTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `MediaType`, `ExifMetadata` from Task 1.
- Produces:
  - `interface PlatformMediaDataSource`:
    - `fun observeMedia(batchSize: Int = 100): Flow<List<MediaItem>>`
    - `suspend fun queryMediaWindow(offset: Int, limit: Int): List<MediaItem>`
    - `suspend fun deleteMedia(ids: List<String>): Boolean`
    - `suspend fun fetchExif(id: String): ExifMetadata?`
  - `class FakePlatformMediaDataSource : PlatformMediaDataSource`

- [ ] **Step 1: Write the failing test for `FakePlatformMediaDataSource`**

```kotlin
package com.ayoubzulfiqar.gallery.data

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakePlatformMediaDataSourceTest {
    @Test
    fun testWindowedPaginationAndDeletion() = runTest {
        val fakeSource = FakePlatformMediaDataSource()
        fakeSource.populateWithSyntheticMedia(count = 250)

        val window = fakeSource.queryMediaWindow(offset = 0, limit = 50)
        assertEquals(50, window.size)
        assertEquals(MediaType.IMAGE, window.first().mediaType)

        val observed = fakeSource.observeMedia().first()
        assertEquals(250, observed.size)

        val deleted = fakeSource.deleteMedia(listOf(window.first().id))
        assertTrue(deleted)
        assertEquals(249, fakeSource.observeMedia().first().size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `PlatformMediaDataSource` and `FakePlatformMediaDataSource` do not exist.

- [ ] **Step 3: Implement `PlatformMediaDataSource`, `FakePlatformMediaDataSource`, and `AndroidPlatformMediaDataSource`**

Implement `PlatformMediaDataSource` in `commonMain`, the thread-safe `FakePlatformMediaDataSource` with synthetic items generator, and `AndroidPlatformMediaDataSource` in `androidMain` wrapping Android `ContentResolver` queries.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/datasource/ shared/src/androidMain/kotlin/com/ayoubzulfiqar/gallery/data/datasource/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/data/
git commit -m "feat(data): implement platform media data source abstraction and test double"
```

---

### Task 3: Local Metadata Storage & Timeline Grouping Engine

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/local/LocalMetadataStore.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/repository/DefaultMediaRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/usecase/GroupMediaByTimelineUseCase.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/data/DefaultMediaRepositoryTest.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/domain/GroupMediaByTimelineUseCaseTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `MediaTimelineGroup`, `MediaRepository` from Task 1; `PlatformMediaDataSource` from Task 2.
- Produces:
  - `class LocalMetadataStore`: manages in-memory/persistent favorites set, custom tags map, and album mappings.
  - `class DefaultMediaRepository(platformSource: PlatformMediaDataSource, localStore: LocalMetadataStore) : MediaRepository`
  - `class GroupMediaByTimelineUseCase`: groups media items by day/month/year clusters.

- [ ] **Step 1: Write failing test for Timeline Grouping & Metadata Overlays**

```kotlin
package com.ayoubzulfiqar.gallery.domain

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import com.ayoubzulfiqar.gallery.domain.usecase.GroupMediaByTimelineUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultMediaRepositoryTest {
    @Test
    fun testFavoriteToggleAndTimelineGrouping() = runTest {
        val platformSource = FakePlatformMediaDataSource()
        platformSource.populateWithSyntheticMedia(count = 20)
        val localStore = LocalMetadataStore()
        val repo = DefaultMediaRepository(platformSource, localStore)

        val items = repo.getMediaFlow().first()
        val firstId = items.first().id

        // Toggle favorite
        val isFav = repo.toggleFavorite(firstId)
        assertTrue(isFav)
        assertTrue(repo.getMediaFlow().first().first { it.id == firstId }.isFavorite)

        // Timeline grouping
        val groups = repo.getTimelineGroups()
        assertTrue(groups.isNotEmpty())
        assertEquals(20, groups.sumOf { it.items.size })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `LocalMetadataStore`, `DefaultMediaRepository`, and `GroupMediaByTimelineUseCase` do not exist.

- [ ] **Step 3: Implement `LocalMetadataStore`, `GroupMediaByTimelineUseCase`, and `DefaultMediaRepository`**

Implement metadata storage with reactive Flow emitting merged items, and date grouping by year/month/day.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/data/ shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/domain/usecase/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/
git commit -m "feat(data): implement local metadata store and timeline grouping engine"
```

---

### Task 4: Zero-Knowledge Biometric Vault & AES-256-GCM Engine

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/vault/crypto/AesGcmCipher.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/vault/data/DefaultVaultRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/vault/AesGcmCipherTest.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/vault/DefaultVaultRepositoryTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `VaultRepository` from Task 1.
- Produces:
  - `class AesGcmCipher`: `fun encrypt(plainText: ByteArray, key: ByteArray): ByteArray`, `fun decrypt(cipherTextWithIvAndTag: ByteArray, key: ByteArray): ByteArray`
  - `sealed class VaultError : Throwable()`
  - `class DefaultVaultRepository : VaultRepository`

- [ ] **Step 1: Write failing test for AES-256-GCM cipher and Vault Repository**

```kotlin
package com.ayoubzulfiqar.gallery.vault

import com.ayoubzulfiqar.gallery.vault.crypto.AesGcmCipher
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AesGcmCipherTest {
    @Test
    fun testEncryptionDecryptionRoundtrip() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32) { it.toByte() }
        val original = "Highly sensitive private photo byte payload".encodeToByteArray()

        val encrypted = cipher.encrypt(original, key)
        assertTrue(encrypted.size > original.size)

        val decrypted = cipher.decrypt(encrypted, key)
        assertContentEquals(original, decrypted)
    }

    @Test
    fun testTamperedCiphertextRejection() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32) { 7.toByte() }
        val encrypted = cipher.encrypt("secret".encodeToByteArray(), key)
        // Tamper with the last byte (auth tag)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()

        assertFailsWith<IllegalStateException> {
            cipher.decrypt(encrypted, key)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `AesGcmCipher` does not exist.

- [ ] **Step 3: Implement `AesGcmCipher` and `DefaultVaultRepository`**

Implement secure byte-level AES-GCM encryption with 12-byte random IV prepended and 16-byte tag verification, along with vault state management in `DefaultVaultRepository`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/vault/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/vault/
git commit -m "feat(vault): implement zero-knowledge AES-256-GCM encryption and vault repository"
```

---

### Task 5: Non-Destructive Photo Editing Pipeline & ColorMatrix Math

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/editor/ColorMatrixHelper.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/editor/FilterPresets.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/editor/EditPipeline.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/editor/ColorMatrixHelperTest.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/editor/EditPipelineTest.kt`

**Interfaces:**
- Consumes: `ColorAdjustments`, `CropRect`, `EditRecipe` from Task 1.
- Produces:
  - `object ColorMatrixHelper`: `fun buildColorMatrix(adjustments: ColorAdjustments): FloatArray` (4x5 color matrix for Compose `ColorFilter.colorMatrix(ColorMatrix(...))`).
  - `object FilterPresets`: list of preset matrix profiles (`VIVID`, `FILM_NOIR`, `WARM_SUN`, `COOL_TWILIGHT`, `CYBERPUNK`, `CHROME`, `VINTAGE`).
  - `class EditPipeline`: undo/redo recipe stack with geometric transformations (crop, rotate 90°, flip).

- [ ] **Step 1: Write failing test for ColorMatrix math and EditPipeline history**

```kotlin
package com.ayoubzulfiqar.gallery.editor

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorMatrixHelperTest {
    @Test
    fun testIdentityMatrixWhenAdjustmentsAreDefault() {
        val matrix = ColorMatrixHelper.buildColorMatrix(ColorAdjustments())
        assertEquals(20, matrix.size)
        // Red diagonal 1, Green diagonal 1, Blue diagonal 1, Alpha diagonal 1
        assertEquals(1f, matrix[0])
        assertEquals(1f, matrix[6])
        assertEquals(1f, matrix[12])
        assertEquals(1f, matrix[18])
    }

    @Test
    fun testEditPipelineUndoRedo() {
        val pipeline = EditPipeline()
        assertEquals(0f, pipeline.currentRecipe.rotationDegrees)

        pipeline.rotateBy90()
        assertEquals(90f, pipeline.currentRecipe.rotationDegrees)
        assertTrue(pipeline.canUndo)

        pipeline.undo()
        assertEquals(0f, pipeline.currentRecipe.rotationDegrees)
        assertTrue(pipeline.canRedo)

        pipeline.redo()
        assertEquals(90f, pipeline.currentRecipe.rotationDegrees)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `ColorMatrixHelper` and `EditPipeline` do not exist.

- [ ] **Step 3: Implement `ColorMatrixHelper`, `FilterPresets`, and `EditPipeline`**

Implement 4x5 matrix multiplication for brightness, contrast, saturation, and warmth; define filter presets; implement undo/redo history stack for `EditRecipe`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/editor/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/editor/
git commit -m "feat(editor): implement non-destructive color matrix calculations and edit pipeline"
```

---

### Task 6: Modern UI Design System, Frosted Glass & Gesture Physics

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/theme/GalleryTheme.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/theme/FrostedGlass.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/gesture/PinchDensityState.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/gesture/PinchDensityStateTest.kt`

**Interfaces:**
- Produces:
  - `enum class TimelineDensity(val columns: Int)`: `YEARS(8)`, `MONTHS(5)`, `DAYS(3)`, `MASONRY(1)`
  - `class PinchDensityState`: tracks pinch scale factor and calculates snap target to nearest `TimelineDensity`
  - `Modifier.frostedGlass(blurRadius: Dp = 20.dp, alpha: Float = 0.75f)`
  - `GalleryTheme`: customized dark/light tokens with edge-to-edge support

- [ ] **Step 1: Write failing test for `PinchDensityState` scale snapping**

```kotlin
package com.ayoubzulfiqar.gallery.ui.gesture

import kotlin.test.Test
import kotlin.test.assertEquals

class PinchDensityStateTest {
    @Test
    fun testPinchInCollapsesToMonthsAndYears() {
        val state = PinchDensityState(initialDensity = TimelineDensity.DAYS)
        assertEquals(3, state.currentDensity.columns)

        // Pinching in (scale < 1.0)
        state.onPinch(scaleDelta = 0.7f)
        assertEquals(TimelineDensity.MONTHS, state.calculateSnapTarget())

        state.onPinch(scaleDelta = 0.4f)
        assertEquals(TimelineDensity.YEARS, state.calculateSnapTarget())
    }

    @Test
    fun testPinchOutExpandsToMasonry() {
        val state = PinchDensityState(initialDensity = TimelineDensity.DAYS)
        state.onPinch(scaleDelta = 1.6f)
        assertEquals(TimelineDensity.MASONRY, state.calculateSnapTarget())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `PinchDensityState` and `TimelineDensity` do not exist.

- [ ] **Step 3: Implement `GalleryTheme`, `FrostedGlass`, and `PinchDensityState`**

Implement the Material 3 theme palette, frosted glass Compose modifier using blur and translucent surface overlay, and pinch density gesture calculations.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/theme/ shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/gesture/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/gesture/
git commit -m "feat(ui): implement design system, frosted glass modifier, and pinch density state"
```

---

### Task 7: Multi-Density Timeline Grid Screen & Marquee Drag Selection

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/MediaThumbnailItem.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/MarqueeSelectionBar.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/TimelineViewModelTest.kt`

**Interfaces:**
- Consumes: `MediaRepository`, `MediaTimelineGroup`, `MediaItem` from Tasks 1-3; `PinchDensityState`, `TimelineDensity`, `frostedGlass` from Task 6.
- Produces:
  - `TimelineViewModel`: exposes `uiState: StateFlow<TimelineUiState>`, handles density toggles, favorite toggling, and multi-selection
  - `TimelineScreen`: composable rendering multi-column lazy grid with sticky headers, pinch-to-zoom gestures, and marquee drag selection bar

- [ ] **Step 1: Write failing test for `TimelineViewModel` state transitions**

```kotlin
package com.ayoubzulfiqar.gallery.ui.timeline

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import com.ayoubzulfiqar.gallery.ui.gesture.TimelineDensity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineViewModelTest {
    @Test
    fun testSelectionAndDensityChange() = runTest {
        val platformSource = FakePlatformMediaDataSource()
        platformSource.populateWithSyntheticMedia(count = 10)
        val repo = DefaultMediaRepository(platformSource, LocalMetadataStore())
        val viewModel = TimelineViewModel(repo)

        viewModel.setDensity(TimelineDensity.MONTHS)
        assertEquals(TimelineDensity.MONTHS, viewModel.density.value)

        viewModel.toggleSelection("item_1")
        assertTrue(viewModel.selectedIds.value.contains("item_1"))
        assertTrue(viewModel.isSelectionMode.value)

        viewModel.clearSelection()
        assertFalse(viewModel.isSelectionMode.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `TimelineViewModel` does not exist.

- [ ] **Step 3: Implement `TimelineViewModel`, `TimelineScreen`, `MediaThumbnailItem`, and `MarqueeSelectionBar`**

Build the reactive ViewModel, multi-density LazyVerticalGrid, sticky headers, media thumbnail composables, and the floating frosted marquee selection bottom bar.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/timeline/
git commit -m "feat(ui): implement multi-density timeline grid and marquee selection bar"
```

---

### Task 8: Immersive Lightbox Media Viewer with Spring Swipe-to-Dismiss

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/ViewerViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/LightboxViewerScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/ZoomableMediaBox.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/VideoPlayerOverlay.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/ViewerViewModelTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `MediaType`, `MediaRepository` from Tasks 1-3.
- Produces:
  - `ViewerViewModel`: handles current item index, favorite toggle, chrome visibility toggle, and video playback states.
  - `LightboxViewerScreen`: composable supporting double-tap zoom, pinch-to-zoom, horizontal paging between media items, and velocity-aware vertical swipe-down dismiss.

- [ ] **Step 1: Write failing test for `ViewerViewModel`**

```kotlin
package com.ayoubzulfiqar.gallery.ui.viewer

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerViewModelTest {
    @Test
    fun testChromeVisibilityToggleAndNavigation() = runTest {
        val platformSource = FakePlatformMediaDataSource()
        platformSource.populateWithSyntheticMedia(count = 5)
        val repo = DefaultMediaRepository(platformSource, LocalMetadataStore())
        val viewModel = ViewerViewModel(repo, initialItemId = "media_2")

        assertTrue(viewModel.isChromeVisible.value)
        viewModel.toggleChrome()
        assertFalse(viewModel.isChromeVisible.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `ViewerViewModel` does not exist.

- [ ] **Step 3: Implement `ViewerViewModel`, `ZoomableMediaBox`, `VideoPlayerOverlay`, and `LightboxViewerScreen`**

Build interactive transform gesture canvas (scale, pan, double-tap zoom), swipe-to-dismiss threshold detection with spring physics, and video player overlay.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/viewer/
git commit -m "feat(ui): implement immersive lightbox viewer with spring swipe-to-dismiss gestures"
```

---

### Task 9: Photo Editor Screen & EXIF Inspector Bottom Sheet

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/editor/PhotoEditorViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/editor/PhotoEditorScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/exif/ExifInspectorSheet.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/editor/PhotoEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `EditRecipe`, `ColorAdjustments`, `ExifMetadata` from Task 1; `ColorMatrixHelper`, `EditPipeline` from Task 5.
- Produces:
  - `PhotoEditorViewModel`: reactive state management for active adjustment slider, selected filter preset, rotation, and crop rect.
  - `PhotoEditorScreen`: interactive crop box, filter strip, adjustment sliders, and real-time color-filtered image canvas.
  - `ExifInspectorSheet`: modal bottom sheet displaying camera parameters, ISO, shutter, aperture, and map geotag coordinates with privacy scrubber toggle.

- [ ] **Step 1: Write failing test for `PhotoEditorViewModel`**

```kotlin
package com.ayoubzulfiqar.gallery.ui.editor

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoEditorViewModelTest {
    @Test
    fun testEditorAdjustmentUpdateAndFilterApplication() {
        val sampleItem = MediaItem(
            id = "test_img",
            uri = "file://test.jpg",
            displayName = "test.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMillis = 1000L,
            dateModifiedMillis = 1000L,
            width = 1920,
            height = 1080,
            sizeBytes = 1024L
        )
        val viewModel = PhotoEditorViewModel(sampleItem)
        viewModel.updateBrightness(0.3f)
        assertEquals(0.3f, viewModel.currentRecipe.value.adjustments.brightness)

        viewModel.applyFilter("VIVID")
        assertEquals("VIVID", viewModel.currentRecipe.value.filterPresetId)
        assertTrue(viewModel.canUndo.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `PhotoEditorViewModel` does not exist.

- [ ] **Step 3: Implement `PhotoEditorViewModel`, `PhotoEditorScreen`, and `ExifInspectorSheet`**

Implement slider controllers for exposure, saturation, warmth, vignette; crop preset selectors (Original, 1:1, 4:3, 16:9); rotation actions; and the EXIF inspector bottom sheet.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/editor/ shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/exif/ shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/editor/
git commit -m "feat(ui): implement photo editor screen and EXIF inspector bottom sheet"
```

---

### Task 10: Biometric Vault Screen, App Navigation & End-to-End Integration

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/vault/VaultViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/vault/VaultScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/navigation/GalleryNavigation.kt`
- Modify: `shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/App.kt`
- Test: `shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/navigation/NavigationStateTest.kt`

**Interfaces:**
- Consumes: `TimelineScreen` from Task 7, `LightboxViewerScreen` from Task 8, `PhotoEditorScreen` from Task 9, `VaultRepository` from Task 4.
- Produces:
  - `GalleryNavigation`: type-safe screen destinations (`Timeline`, `Viewer(id)`, `Editor(id)`, `Vault`, `Albums`, `MapSearch`).
  - `App()`: root composable with dynamic frosted navigation bar, animated tab transitions, and biometric vault lock overlay.

- [ ] **Step 1: Write failing test for Navigation State Machine**

```kotlin
package com.ayoubzulfiqar.gallery.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationStateTest {
    @Test
    fun testDestinationTransitions() {
        val navState = GalleryNavState(initialDestination = GalleryDestination.Timeline)
        assertEquals(GalleryDestination.Timeline, navState.currentDestination)

        navState.navigateTo(GalleryDestination.Viewer(mediaId = "123"))
        assertEquals(GalleryDestination.Viewer(mediaId = "123"), navState.currentDestination)

        navState.navigateBack()
        assertEquals(GalleryDestination.Timeline, navState.currentDestination)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: Compilation failure because `GalleryNavState` and `GalleryDestination` do not exist.

- [ ] **Step 3: Implement `VaultViewModel`, `VaultScreen`, `GalleryNavigation`, and update `App.kt`**

Build the Biometric PIN unlock keypad and vaulted items grid; build the type-safe navigation controller; update `App.kt` to host `GalleryTheme`, root navigation, and floating frosted navigation bar.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 5: Run full project verification**

Run: `./gradlew assembleDebug :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL without warnings or errors.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/vault/ shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/ui/navigation/ shared/src/commonMain/kotlin/com/ayoubzulfiqar/gallery/App.kt shared/src/commonTest/kotlin/com/ayoubzulfiqar/gallery/ui/navigation/
git commit -m "feat(ui): complete biometric vault, root navigation, and end-to-end integration"
```
