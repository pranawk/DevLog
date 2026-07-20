package com.matrix.devlog.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matrix.devlog.data.ContributionDatabase
import com.matrix.devlog.data.ContributionRepository
import com.matrix.devlog.data.PlatformAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContributionViewModel(private val repository: ContributionRepository) : ViewModel() {
    
    val accounts: StateFlow<List<PlatformAccount>> = repository.allAccountsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _loadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingStates = _loadingStates.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun savePlatform(id: String, username: String, colorTheme: String) {
        viewModelScope.launch {
            _loadingStates.value = _loadingStates.value + (id to true)
            _message.value = "Saving settings for ${id.replaceFirstChar { it.uppercase() }}..."
            try {
                repository.saveAccount(id, username, colorTheme)
                _message.value = "Settings saved for ${id.replaceFirstChar { it.uppercase() }}!"
            } catch (e: Exception) {
                _message.value = "Error saving: ${e.localizedMessage}"
            } finally {
                _loadingStates.value = _loadingStates.value + (id to false)
            }
        }
    }

    fun refreshPlatform(id: String) {
        viewModelScope.launch {
            _loadingStates.value = _loadingStates.value + (id to true)
            _message.value = "Refreshing ${id.replaceFirstChar { it.uppercase() }}..."
            try {
                val success = repository.refreshAccountData(id)
                if (success) {
                    _message.value = "${id.replaceFirstChar { it.uppercase() }} refreshed successfully!"
                } else {
                    _message.value = "Failed to load fresh data. Keeping previous state."
                }
            } catch (e: Exception) {
                _message.value = "Failed: ${e.localizedMessage}"
            } finally {
                _loadingStates.value = _loadingStates.value + (id to false)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ContributionViewModel::class.java)) {
                val db = ContributionDatabase.getDatabase(context)
                val repository = ContributionRepository(context, db.contributionDao())
                @Suppress("UNCHECKED_CAST")
                return ContributionViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
