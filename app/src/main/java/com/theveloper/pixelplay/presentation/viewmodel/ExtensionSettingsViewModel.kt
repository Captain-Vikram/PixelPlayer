package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.extensions.PixelPlayExtensionHost
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import dev.brahmkshatriya.echo.extension.loader.ExtensionUtils
import dev.brahmkshatriya.echo.extension.loader.ExtensionUtils.getAs
import dev.brahmkshatriya.echo.extension.loader.ExtensionUtils.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.settings.Settings

import dev.brahmkshatriya.echo.common.settings.SettingItem
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSlider
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import kotlinx.coroutines.flow.update

@HiltViewModel
class ExtensionSettingsViewModel @Inject constructor(
    private val extensionEngine: ExtensionLoader,
    private val host: PixelPlayExtensionHost
) : ViewModel() {

    private val _extension = MutableStateFlow<Extension<*>?>(null)
    val extension: StateFlow<Extension<* >?> = _extension.asStateFlow()

    private val _settingsItems = MutableStateFlow<List<Setting>>(emptyList())
    val settingsItems: StateFlow<List<Setting>> = _settingsItems.asStateFlow()
    
    private val _settingsValues = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val settingsValues: StateFlow<Map<String, Any?>> = _settingsValues.asStateFlow()

    fun loadExtension(extensionId: String) {
        viewModelScope.launch {
            val ext = extensionEngine.all.value.find { it.metadata.id == extensionId }
            _extension.value = ext
            val items = ext?.getAs<ExtensionClient, List<Setting>> { getSettingItems() }?.getOrNull() ?: emptyList()
            _settingsItems.value = items
            
            // Load current values
            val currentSettings = ExtensionUtils.getSettings(host.context, ext?.metadata ?: return@launch)
            val values = mutableMapOf<String, Any?>()
            items.forEach { setting ->
                values[setting.key] = when (setting) {
                    is SettingSwitch -> currentSettings.getBoolean(setting.key)
                    is SettingList -> currentSettings.getString(setting.key)
                    is SettingSlider -> currentSettings.getInt(setting.key)
                    is SettingTextInput -> currentSettings.getString(setting.key)
                    else -> null
                }
            }
            _settingsValues.value = values
        }
    }

    fun updateSetting(key: String, value: Any) {
        val ext = _extension.value ?: return
        viewModelScope.launch {
            val currentSettings = ExtensionUtils.getSettings(host.context, ext.metadata)
            when (value) {
                is Boolean -> currentSettings.putBoolean(key, value)
                is String -> currentSettings.putString(key, value)
                is Int -> currentSettings.putInt(key, value)
            }
            ext.getAs<ExtensionClient, Unit> { setSettings(currentSettings) }
            
            // Update local state
            _settingsValues.update { it + (key to value) }
        }
    }
}
