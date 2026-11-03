package com.ayoubzulfiqar.gallery.data.local

import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-local metadata that the platform media store does not hold: favorites, tags, theme and
 * view preferences. Every mutation is persisted through [store], so state survives restarts.
 */
class LocalMetadataStore(private val store: KeyValueStore = InMemoryKeyValueStore()) {

    private val _themeMode = MutableStateFlow(
        store.getString(KEY_THEME)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _density = MutableStateFlow(
        store.getString(KEY_DENSITY)?.let { name -> TimelineDensity.entries.firstOrNull { it.name == name } }
            ?: TimelineDensity.MONTHS
    )
    val timelineDensity: StateFlow<TimelineDensity> = _density.asStateFlow()

    private val _favorites = MutableStateFlow(decodeIds(store.getString(KEY_FAVORITES)))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _tags = MutableStateFlow(decodeTags(store.getString(KEY_TAGS)))
    val tags: StateFlow<Map<String, Set<String>>> = _tags.asStateFlow()

    // Edit recipes are session-scoped: edits are exported as new files rather than kept as sidecars.
    private val _editRecipes = MutableStateFlow<Map<String, EditRecipe>>(emptyMap())
    val editRecipes: StateFlow<Map<String, EditRecipe>> = _editRecipes.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        store.putString(KEY_THEME, mode.name)
    }

    fun cycleThemeMode() {
        setThemeMode(
            when (_themeMode.value) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
        )
    }

    fun setTimelineDensity(density: TimelineDensity) {
        _density.value = density
        store.putString(KEY_DENSITY, density.name)
    }

    fun toggleFavorite(id: String): Boolean {
        var isNowFavorite = false
        _favorites.update { current ->
            isNowFavorite = id !in current
            if (isNowFavorite) current + id else current - id
        }
        persistFavorites()
        return isNowFavorite
    }

    fun setFavorite(ids: Collection<String>, favorite: Boolean) {
        if (ids.isEmpty()) return
        _favorites.update { current -> if (favorite) current + ids else current - ids.toSet() }
        persistFavorites()
    }

    fun isFavorite(id: String): Boolean = id in _favorites.value

    /** Removes local metadata for media that no longer exists (deleted, vaulted). */
    fun forget(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        if (_favorites.value.any { it in idSet }) {
            _favorites.update { it - idSet }
            persistFavorites()
        }
        if (_tags.value.keys.any { it in idSet }) {
            _tags.update { it - idSet }
            persistTags()
        }
    }

    fun addTag(id: String, tag: String) {
        val clean = sanitizeTag(tag) ?: return
        _tags.update { current -> current + (id to ((current[id] ?: emptySet()) + clean)) }
        persistTags()
    }

    fun removeTag(id: String, tag: String) {
        _tags.update { current ->
            val existing = current[id] ?: return@update current
            val remaining = existing - tag
            if (remaining.isEmpty()) current - id else current + (id to remaining)
        }
        persistTags()
    }

    fun getTags(id: String): Set<String> = _tags.value[id] ?: emptySet()

    fun saveEditRecipe(id: String, recipe: EditRecipe) {
        _editRecipes.update { it + (id to recipe) }
    }

    fun getEditRecipe(id: String): EditRecipe? = _editRecipes.value[id]

    private fun persistFavorites() = store.putString(KEY_FAVORITES, _favorites.value.joinToString(RECORD_SEPARATOR))

    private fun persistTags() = store.putString(
        KEY_TAGS,
        _tags.value.entries.joinToString(RECORD_SEPARATOR) { (id, tags) -> id + FIELD_SEPARATOR + tags.joinToString(FIELD_SEPARATOR) }
    )

    private fun sanitizeTag(tag: String): String? =
        tag.trim().filterNot { it.isISOControl() }.take(MAX_TAG_LENGTH).takeIf { it.isNotEmpty() }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DENSITY = "timeline_density"
        const val KEY_FAVORITES = "favorites_v1"
        const val KEY_TAGS = "tags_v1"
        const val RECORD_SEPARATOR = "\n"
        const val FIELD_SEPARATOR = "\t"
        const val MAX_TAG_LENGTH = 64

        fun decodeIds(raw: String?): Set<String> =
            raw?.split(RECORD_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

        fun decodeTags(raw: String?): Map<String, Set<String>> {
            if (raw.isNullOrEmpty()) return emptyMap()
            return raw.split(RECORD_SEPARATOR).mapNotNull { record ->
                val fields = record.split(FIELD_SEPARATOR)
                val id = fields.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val tags = fields.drop(1).filter { it.isNotEmpty() }.toSet()
                if (tags.isEmpty()) null else id to tags
            }.toMap()
        }
    }
}
