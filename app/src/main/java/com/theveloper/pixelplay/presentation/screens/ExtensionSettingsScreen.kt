package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.viewmodel.ExtensionSettingsViewModel
import dev.brahmkshatriya.echo.common.settings.*
import dev.brahmkshatriya.echo.common.Extension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSettingsScreen(
    extensionId: String,
    onBack: () -> Unit,
    viewModel: ExtensionSettingsViewModel = hiltViewModel()
) {
    val extension by viewModel.extension.collectAsStateWithLifecycle()
    val settings by viewModel.settingsItems.collectAsStateWithLifecycle()
    val values by viewModel.settingsValues.collectAsStateWithLifecycle()

    LaunchedEffect(extensionId) {
        viewModel.loadExtension(extensionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val name = (extension as? Extension<*>)?.metadata?.name ?: "Extension Settings"
                    Text(name) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(settings) { setting ->
                when (setting) {
                    is SettingCategory -> {
                        SettingCategoryHeader(setting.title)
                    }
                    is SettingSwitch -> {
                        SettingSwitchItem(
                            setting = setting,
                            currentValue = values[setting.key] as? Boolean ?: setting.defaultValue,
                            onValueChange = { viewModel.updateSetting(setting.key, it) }
                        )
                    }
                    is SettingList -> {
                        val defaultVal = setting.defaultEntryIndex?.let { setting.entryValues.getOrNull(it) } ?: ""
                        SettingListItem(
                            setting = setting,
                            currentValue = values[setting.key] as? String ?: defaultVal,
                            onValueChange = { viewModel.updateSetting(setting.key, it) }
                        )
                    }
                    is SettingSlider -> {
                        SettingSliderItem(
                            setting = setting,
                            currentValue = values[setting.key] as? Int ?: setting.defaultValue ?: 0,
                            onValueChange = { viewModel.updateSetting(setting.key, it) }
                        )
                    }
                    is SettingTextInput -> {
                        SettingTextInputItem(
                            setting = setting,
                            currentValue = values[setting.key] as? String ?: setting.defaultValue ?: "",
                            onValueChange = { viewModel.updateSetting(setting.key, it) }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun SettingCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
private fun SettingSwitchItem(
    setting: SettingSwitch,
    currentValue: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        headlineContent = { Text(setting.title) },
        supportingContent = setting.summary?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = currentValue,
                onCheckedChange = onValueChange
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingListItem(
    setting: SettingList,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    val currentLabel = remember(currentValue, setting.entryTitles, setting.entryValues) {
        val index = setting.entryValues.indexOf(currentValue)
        if (index != -1) setting.entryTitles[index] else currentValue
    }

    ListItem(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable { showDialog = true },
        headlineContent = { Text(setting.title) },
        supportingContent = { Text(currentLabel) }
    )

    if (showDialog) {
        BasicAlertDialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = setting.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    setting.entryValues.forEachIndexed { index, value ->
                        val label = setting.entryTitles[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(value)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = value == currentValue,
                                onClick = null
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSliderItem(
    setting: SettingSlider,
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(text = setting.title, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = currentValue.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = setting.from.toFloat()..setting.to.toFloat(),
                steps = setting.steps ?: 0,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = currentValue.toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Composable
private fun SettingTextInputItem(
    setting: SettingTextInput,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var text by remember(currentValue) { mutableStateOf(currentValue) }
    
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(setting.title) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (text != currentValue) {
                    IconButton(onClick = { onValueChange(text) }) {
                        Icon(Icons.Rounded.Check, contentDescription = "Save")
                    }
                }
            }
        )
    }
}
