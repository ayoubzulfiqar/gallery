package com.ayoubzulfiqar.gallery.data.local

import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LocalMetadataStore {

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _tags = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val tags: StateFlow<Map<String, Set<String>>> = _tags.asStateFlow()

    private val _albums = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val albums: StateFlow<Map<String, Set<String>>> = _albums.asStateFlow()

    private val _editRecipes = MutableStateFlow<Map<String, EditRecipe>>(emptyMap())
    val editRecipes: StateFlow<Map<String, EditRecipe>> = _editRecipes.asStateFlow()

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
