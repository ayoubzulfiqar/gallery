package com.ayoubzulfiqar.gallery.platform

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of a user-facing platform action; [message] is shown to the user when present. */
sealed interface ActionResult {
    data class Done(val message: String? = null) : ActionResult
    data object Cancelled : ActionResult
    data class Failed(val message: String) : ActionResult
}

/** Platform intents/activities. Implementations are bound to the current UI host (Activity). */
interface PlatformActionHandler {
    fun share(items: List<MediaItem>): ActionResult

    /** Shares privacy-sanitized copies (location and camera metadata removed). */
    suspend fun shareSanitized(items: List<MediaItem>): ActionResult

    fun launchGoogleLens(item: MediaItem): ActionResult
    fun setAsWallpaper(item: MediaItem): ActionResult
    fun playVideo(item: MediaItem): ActionResult
    fun openMap(latitude: Double, longitude: Double, label: String): ActionResult
    fun openAppSettings(): ActionResult
}

class NoOpPlatformActionHandler : PlatformActionHandler {
    private val unavailable = ActionResult.Failed("Not available on this device yet")
    override fun share(items: List<MediaItem>) = unavailable
    override suspend fun shareSanitized(items: List<MediaItem>) = unavailable
    override fun launchGoogleLens(item: MediaItem) = unavailable
    override fun setAsWallpaper(item: MediaItem) = unavailable
    override fun playVideo(item: MediaItem) = unavailable
    override fun openMap(latitude: Double, longitude: Double, label: String) = unavailable
    override fun openAppSettings() = unavailable
}

enum class MediaAccess {
    /** Not yet determined (first launch, before asking). */
    UNKNOWN,
    FULL,

    /** User granted access to selected photos only (Android 14+ / iOS limited library). */
    PARTIAL,
    DENIED,

    /** Denied with "don't ask again": only the system settings can grant access now. */
    DENIED_PERMANENTLY
}

interface MediaPermissionController {
    val access: StateFlow<MediaAccess>

    /** Re-reads the current grant state (e.g. after returning from settings). */
    fun refresh()

    /** Shows the system permission prompt (or the photo picker for partial access). */
    fun request()
}

class AlwaysGrantedPermissionController : MediaPermissionController {
    private val state = MutableStateFlow(MediaAccess.FULL)
    override val access: StateFlow<MediaAccess> = state.asStateFlow()
    override fun refresh() {}
    override fun request() {}
}
