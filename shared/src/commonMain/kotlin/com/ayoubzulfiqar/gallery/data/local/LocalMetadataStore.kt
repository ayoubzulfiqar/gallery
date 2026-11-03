package com.ayoubzulfiqar.gallery.data.local

import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LocalMetadataStore {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _tags = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val tags: StateFlow<Map<String, Set<String>>> = _tags.asStateFlow()

    private val _albums = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val albums: StateFlow<Map<String, Set<String>>> = _albums.asStateFlow()

    private val _editRecipes = MutableStateFlow<Map<String, EditRecipe>>(emptyMap())
    val editRecipes: StateFlow<Map<String, EditRecipe>> = _editRecipes.asStateFlow()

    private val _vaultedIds = MutableStateFlow<Set<String>>(emptySet())
    val vaultedIds: StateFlow<Set<String>> = _vaultedIds.asStateFlow()

    fun markVaulted(id: String) {
        _vaultedIds.update { it + id }
    }

    fun markVaulted(ids: Collection<String>) {
        _vaultedIds.update { it + ids }
    }

    fun unmarkVaulted(id: String) {
        _vaultedIds.update { it - id }
    }

    fun isVaulted(id: String): Boolean = id in _vaultedIds.value

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun cycleThemeMode() {
        _themeMode.update { current ->
            when (current) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
        }
    }

    fun toggleFavorite(id: String): Boolean {
        var isNowFavorite = false
        _favorites.update { current ->
            isNowFavorite = id !in current
            if (isNowFavorite) current + id else current - id
        }
        return isNowFavorite
    }

    fun isFavorite(id: String): Boolean = id in _favorites.value

    fun addTag(id: String, tag: String) {
        _tags.update { current ->
            val mutable = current.toMutableMap()
            val existing = mutable[id] ?: emptySet()
            mutable[id] = existing + tag
            mutable
        }
    }

    fun removeTag(id: String, tag: String) {
        _tags.update { current ->
            val mutable = current.toMutableMap()
            val existing = mutable[id] ?: return@update current
            mutable[id] = existing - tag
            mutable
        }
    }

    fun getTags(id: String): Set<String> = _tags.value[id] ?: emptySet()

    fun saveEditRecipe(id: String, recipe: EditRecipe) {
        _editRecipes.update { current ->
            val mutable = current.toMutableMap()
            mutable[id] = recipe
            mutable
        }
    }

    fun getEditRecipe(id: String): EditRecipe? = _editRecipes.value[id]
}
