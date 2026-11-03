package com.ayoubzulfiqar.gallery.data.local

import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalMetadataStore {
    private val lock = Any()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _tags = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val tags: StateFlow<Map<String, Set<String>>> = _tags.asStateFlow()

    private val _albums = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val albums: StateFlow<Map<String, Set<String>>> = _albums.asStateFlow()

    private val _editRecipes = MutableStateFlow<Map<String, EditRecipe>>(emptyMap())
    val editRecipes: StateFlow<Map<String, EditRecipe>> = _editRecipes.asStateFlow()

    fun toggleFavorite(id: String): Boolean {
        synchronized(lock) {
            val current = _favorites.value
            val isNowFavorite = id !in current
            _favorites.value = if (isNowFavorite) current + id else current - id
            return isNowFavorite
        }
    }

    fun isFavorite(id: String): Boolean = id in _favorites.value

    fun addTag(id: String, tag: String) {
        synchronized(lock) {
            val current = _tags.value.toMutableMap()
            val existing = current[id] ?: emptySet()
            current[id] = existing + tag
            _tags.value = current
        }
    }

    fun removeTag(id: String, tag: String) {
        synchronized(lock) {
            val current = _tags.value.toMutableMap()
            val existing = current[id] ?: return
            current[id] = existing - tag
            _tags.value = current
        }
    }

    fun getTags(id: String): Set<String> = _tags.value[id] ?: emptySet()

    fun saveEditRecipe(id: String, recipe: EditRecipe) {
        synchronized(lock) {
            val current = _editRecipes.value.toMutableMap()
            current[id] = recipe
            _editRecipes.value = current
        }
    }

    fun getEditRecipe(id: String): EditRecipe? = _editRecipes.value[id]
}
