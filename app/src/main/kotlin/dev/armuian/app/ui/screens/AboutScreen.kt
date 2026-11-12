package dev.armuian.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.armuian.app.BuildConfig
import dev.armuian.app.R
import dev.armuian.app.ui.InfoRow
import dev.armuian.app.ui.SectionTitle
import dev.armuian.core.AppInfo

private data class Lib(val name: String, val license: String, val url: String, val asset: String? = null)

private val LIBS = listOf(
    Lib("AndroidX / Jetpack Compose / WorkManager / DataStore", "Apache 2.0", "https://github.com/androidx/androidx"),
    Lib("Material Components / Material 3", "Apache 2.0", "https://github.com/material-components/material-components-android"),
    Lib("Kotlin / kotlinx.coroutines / kotlinx.serialization", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Lib("OkHttp / Okio", "Apache 2.0", "https://github.com/square/okhttp"),
    Lib("Apache Commons Compress", "Apache 2.0", "https://github.com/apache/commons-compress"),
    Lib("JetBrains Mono", "SIL OFL 1.1", "https://github.com/JetBrains/JetBrainsMono", "OFL-JetBrainsMono.txt"),
    Lib("Inter", "SIL OFL 1.1", "https://github.com/rsms/inter", "OFL-Inter.txt"),
    Lib("Google Sans Flex", "SIL OFL 1.1", "https://fonts.google.com/specimen/Google+Sans+Flex", "OFL-GoogleSansFlex.txt"),
    Lib("Material Symbols (mobile_check)", "Apache 2.0", "https://github.com/google/material-design-icons", "MaterialSymbols-Apache-2.0.txt"),
    Lib("redroid", "Apache 2.0 / GPL-2.0", "https://github.com/remote-android/redroid-doc"),
)

@Composable
fun AboutScreen() {
    val uri = LocalUriHandler.current
    var licenseOf by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Lib?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(AppInfo.APP_NAME, style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodyMedium)
                    InfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                    InfoRow(stringResource(R.string.author_title), "${AppInfo.AUTHOR_TIKTOK} · ${AppInfo.AUTHOR_GITHUB}")
                    Button(onClick = { uri.openUri(AppInfo.GITHUB_REPO_URL) }) { Text(stringResource(R.string.open_github)) }
                    OutlinedButton(onClick = { uri.openUri(AppInfo.TIKTOK_URL) }) { Text("TikTok ${AppInfo.AUTHOR_TIKTOK}") }
                    OutlinedButton(onClick = { uri.openUri(AppInfo.GITHUB_PROFILE_URL) }) { Text("GitHub ${AppInfo.AUTHOR_GITHUB}") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle(stringResource(R.string.thanks_title))
                    Text(stringResource(R.string.thanks_text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.licenses_title))
                    Text(stringResource(R.string.license_app), style = MaterialTheme.typography.bodyMedium)
                    LIBS.forEach { l ->
                        Column {
                            Text(l.name, style = MaterialTheme.typography.bodyMedium)
                            Text(l.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.material3.TextButton(onClick = { uri.openUri(l.url) }) { Text(l.url.removePrefix("https://")) }
                            if (l.asset != null) androidx.compose.material3.TextButton(onClick = { licenseOf = l }) { Text(stringResource(R.string.license_text)) }
                        }
                    }
                }
            }
        }
    }
    licenseOf?.let { LicenseDialog(it) { licenseOf = null } }
}

@Composable
private fun LicenseDialog(lib: Lib, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val text = androidx.compose.runtime.remember(lib) {
        runCatching { ctx.assets.open("licenses/${lib.asset}").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lib.name) },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = dev.armuian.app.ui.LocalMonoFont.current)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
