package com.capivaraec.smwc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.app.ui.components.SectionLabel

/** App settings: RetroAchievements account, clean ROM and theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: SmwcApplication, onBack: () -> Unit) {
    val useSystemColors by app.preferences.useSystemColors.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("RetroAchievements")
            SettingsCard { RaAccountSection(app) }

            SectionLabel("Clean ROM")
            SettingsCard {
                CleanRomRow(app, reason = "Choose your clean Super Mario World (USA) ROM to match sets by ROM hash and to apply patches")
            }

            SectionLabel("Appearance")
            SettingsCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Use system colors", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Material You: colors from your wallpaper instead of the app's gold theme",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = useSystemColors, onCheckedChange = app.preferences::setUseSystemColors)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

/**
 * The RetroAchievements account: a form until it is set up, then the user name with options to
 * change or remove it. The key is checked with RetroAchievements before it is saved.
 */
@Composable
private fun RaAccountSection(app: SmwcApplication) {
    val vm: RaSettingsViewModel = viewModel { RaSettingsViewModel(app) }
    val credentials by vm.credentials.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    if (credentials != null && !state.editing) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(credentials!!.username, style = MaterialTheme.typography.titleSmall)
                Text("Connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            TextButton(onClick = vm::startEditing) { Text("Change") }
            TextButton(onClick = vm::signOut) { Text("Remove") }
        }
        return
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Connect your RetroAchievements account", style = MaterialTheme.typography.titleSmall)
        Text(
            "Your user name and Web API key (retroachievements.org → Settings → Keys) are stored only on this device, encrypted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = vm::onUsernameChange,
            label = { Text("User name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = vm::onApiKeyChange,
            label = { Text("Web API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.save() }),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(shape = MaterialTheme.shapes.medium, onClick = vm::save, enabled = !state.saving) { Text(if (state.saving) "Checking…" else "Save") }
            if (state.editing) OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = vm::cancelEditing) { Text("Cancel") }
            TextButton(onClick = { uri.openUri("https://retroachievements.org/settings?tab=applications") }) { Text("Get your key") }
        }
    }
}
