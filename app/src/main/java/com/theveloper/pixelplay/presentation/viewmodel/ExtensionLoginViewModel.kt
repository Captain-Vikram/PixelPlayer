package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import dev.brahmkshatriya.echo.extension.loader.db.UserDao
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity.Companion.toCurrentUser
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity.Companion.toEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import dev.brahmkshatriya.echo.common.MusicExtension

sealed class ExtensionLoginState {
    object Idle : ExtensionLoginState()
    object Loading : ExtensionLoginState()
    data class CustomInputRequired(val forms: List<LoginClient.Form>) : ExtensionLoginState()
    data class WebViewRequired(val request: dev.brahmkshatriya.echo.common.helpers.WebViewRequest<List<User>>) : ExtensionLoginState()
    object Success : ExtensionLoginState()
    data class Error(val message: String) : ExtensionLoginState()
}

@HiltViewModel
class ExtensionLoginViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val userDao: UserDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val extensionId: String = savedStateHandle.get<String>("extensionId") ?: ""

    private val _state = MutableStateFlow<ExtensionLoginState>(ExtensionLoginState.Idle)
    val state: StateFlow<ExtensionLoginState> = _state.asStateFlow()

    private var loginClient: LoginClient? = null
    private var extension: MusicExtension? = null

    init {
        viewModelScope.launch {
            loadClient()
        }
    }

    private suspend fun loadClient() {
        val ext = extensionLoader.all.value.find { it.metadata.id == extensionId }
        if (ext == null) {
            _state.value = ExtensionLoginState.Error("Extension not found")
            return
        }
        
        val instance = ext.instance.value().getOrNull()
        if (instance !is LoginClient) {
            _state.value = ExtensionLoginState.Error("Extension does not support login")
            return
        }

        extension = ext as? MusicExtension
        loginClient = instance

        _state.value = when (instance) {
            is LoginClient.CustomInput -> ExtensionLoginState.CustomInputRequired(instance.forms)
            is LoginClient.WebView -> ExtensionLoginState.WebViewRequired(instance.webViewRequest)
            else -> ExtensionLoginState.Error("Unknown login client type")
        }
    }

    fun loginWithCustomInput(key: String, data: Map<String, String?>) {
        val client = loginClient as? LoginClient.CustomInput ?: return
        viewModelScope.launch {
            _state.value = ExtensionLoginState.Loading
            try {
                val users = client.onLogin(key, data)
                handleLoginSuccess(users)
            } catch (e: Exception) {
                Timber.e(e, "Extension login failed")
                _state.value = ExtensionLoginState.Error(e.message ?: "Login failed")
            }
        }
    }
    
    fun onWebViewLoginSuccess(users: List<User>?) {
        if (users != null) {
            viewModelScope.launch {
                handleLoginSuccess(users)
            }
        } else {
            _state.value = ExtensionLoginState.Error("Login cancelled or failed")
        }
    }

    private suspend fun handleLoginSuccess(users: List<User>) {
        val ext = extension ?: return
        if (users.isEmpty()) {
            _state.value = ExtensionLoginState.Error("No users returned from login")
            return
        }

        // Save users to database
        val user = users.first()
        val userEntity = user.toEntity(ext.metadata.type, ext.metadata.id)
        userDao.insertUser(userEntity)
        userDao.setCurrentUser(userEntity.toCurrentUser())

        // Notify client
        loginClient?.setLoginUser(user)

        _state.value = ExtensionLoginState.Success
    }
}
