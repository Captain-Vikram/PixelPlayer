package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.viewmodel.ExtensionLoginState
import com.theveloper.pixelplay.presentation.viewmodel.ExtensionLoginViewModel
import dev.brahmkshatriya.echo.common.clients.LoginClient
import com.theveloper.pixelplay.extensions.webview.ExtensionWebViewManager
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionLoginScreen(
    onNavigateUp: () -> Unit,
    webViewManager: ExtensionWebViewManager,
    viewModel: ExtensionLoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        if (state is ExtensionLoginState.Success) {
            onNavigateUp()
        } else if (state is ExtensionLoginState.WebViewRequired) {
            val request = (state as ExtensionLoginState.WebViewRequired).request
            scope.launch {
                val result = webViewManager.await(request, "Login required")
                viewModel.onWebViewLoginSuccess(result.getOrNull())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extension Login") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val currentState = state) {
                is ExtensionLoginState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ExtensionLoginState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateUp) {
                            Text("Go Back")
                        }
                    }
                }
                is ExtensionLoginState.CustomInputRequired -> {
                    CustomInputForm(
                        forms = currentState.forms,
                        onSubmit = { key, data -> viewModel.loginWithCustomInput(key, data) }
                    )
                }
                is ExtensionLoginState.WebViewRequired -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Awaiting web login...")
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun CustomInputForm(
    forms: List<LoginClient.Form>,
    onSubmit: (String, Map<String, String?>) -> Unit
) {
    var selectedFormKey by remember { mutableStateOf(forms.firstOrNull()?.key) }
    val selectedForm = forms.find { it.key == selectedFormKey }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (forms.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = forms.indexOf(selectedForm).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            ) {
                forms.forEach { form ->
                    Tab(
                        selected = form.key == selectedFormKey,
                        onClick = { selectedFormKey = form.key },
                        text = { Text(form.label) }
                    )
                }
            }
        }

        selectedForm?.let { form ->
            val inputValues = remember(form.key) {
                mutableStateMapOf<String, String>().apply {
                    form.inputFields.forEach { put(it.key, "") }
                }
            }

            form.inputFields.forEach { field ->
                val isPassword = field.type == LoginClient.InputField.Type.Password
                OutlinedTextField(
                    value = inputValues[field.key] ?: "",
                    onValueChange = { inputValues[field.key] = it },
                    label = { Text(field.label + if (field.isRequired) " *" else "") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.type) {
                            LoginClient.InputField.Type.Email -> KeyboardType.Email
                            LoginClient.InputField.Type.Number -> KeyboardType.Number
                            LoginClient.InputField.Type.Password -> KeyboardType.Password
                            LoginClient.InputField.Type.Url -> KeyboardType.Uri
                            else -> KeyboardType.Text
                        }
                    )
                )
            }

            Button(
                onClick = { onSubmit(form.key, inputValues.toMap()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }
        }
    }
}
