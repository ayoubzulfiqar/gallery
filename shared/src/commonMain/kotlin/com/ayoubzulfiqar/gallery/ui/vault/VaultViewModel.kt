package com.ayoubzulfiqar.gallery.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val pinInput: String = "",
    val errorMessage: String? = null
)

class VaultViewModel(
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        checkUnlockStatus()
    }

    fun onPinDigit(digit: String) {
        val currentPin = _uiState.value.pinInput
        if (currentPin.length < 4) {
            val newPin = currentPin + digit
            _uiState.value = _uiState.value.copy(pinInput = newPin, errorMessage = null)
            if (newPin.length == 4) {
                attemptUnlock(newPin)
            }
        }
    }

    fun onPinBackspace() {
        val currentPin = _uiState.value.pinInput
        if (currentPin.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(pinInput = currentPin.dropLast(1), errorMessage = null)
        }
    }

    fun attemptUnlock(pin: String) {
        viewModelScope.launch {
            val success = vaultRepository.unlockVault(pin)
            if (success) {
                val items = vaultRepository.getVaultItems()
                _uiState.value = VaultUiState(
                    isUnlocked = true,
                    items = items,
                    pinInput = "",
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    pinInput = "",
                    errorMessage = "Incorrect PIN. Please try again."
                )
            }
        }
    }

    fun lockVault() {
        viewModelScope.launch {
            vaultRepository.lockVault()
            _uiState.value = VaultUiState(isUnlocked = false)
        }
    }

    private fun checkUnlockStatus() {
        viewModelScope.launch {
            val unlocked = vaultRepository.isVaultUnlocked()
            if (unlocked) {
                val items = vaultRepository.getVaultItems()
                _uiState.value = _uiState.value.copy(isUnlocked = true, items = items)
            }
        }
    }
}
