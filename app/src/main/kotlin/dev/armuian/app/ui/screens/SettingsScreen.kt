package dev.armuian.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.R
import dev.armuian.app.data.AppFont
import dev.armuian.app.data.DEFAULT_TERMINAL_FONT
import dev.armuian.app.data.DEFAULT_UI_FONT
import dev.armuian.app.data.NavBarStyle
import dev.armuian.app.data.ThemeMode
import dev.armuian.app.ui.AppFonts
import dev.armuian.app.ui.BlurSupported
import dev.armuian.app.ui.FontPreview
import dev.armuian.app.ui.LocalBottomBarPadding
import dev.armuian.app.ui.NavBarPreview
import dev.armuian.app.ui.MainViewModel
import dev.armuian.app.ui.SectionTitle
import dev.armuian.app.ui.fmtBytes
import dev.armuian.app.ui.privilegeText
import dev.armuian.core.ArmLog
import dev.armuian.image.MobileDataPolicy

/** Строка настроек: иконка слева, заголовок, краткое описание, справа — Switch или шеврон. */
@Composable
private fun SettingsRow(
    icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)? = null,
    enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { s -> { Text(s) } },
        trailingContent = trailing,
        modifier = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun <T> RadioDialog(
    title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit,
    labelFont: (T) -> androidx.compose.ui.text.font.FontFamily? = { null },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(value); onDismiss() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value); onDismiss() })
                        Text(label, fontFamily = labelFont(value), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun NavStyleRow(style: NavBarStyle, current: NavBarStyle, title: Int, desc: Int, onSelect: (NavBarStyle) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(style) }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = style == current, onClick = { onSelect(style) })
        Column(Modifier.padding(start = 4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, listState: LazyListState, onOpenAbout: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cache by vm.cacheBytes.collectAsStateWithLifecycle()
    val used by vm.storageUsed.collectAsStateWithLifecycle()
    val busy by vm.hasActiveDownload.collectAsStateWithLifecycle()
    val level by vm.privilege.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val computing = stringResource(R.string.computing)
    var confirmCache by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var mobileDialog by remember { mutableStateOf(false) }
    var uiFontDialog by remember { mutableStateOf(false) }
    var termFontDialog by remember { mutableStateOf(false) }
    val dyn = Build.VERSION.SDK_INT >= 31
    val cardColors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = RoundedCornerShape(24.dp)

    val themeLabel = when (s.themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }
    val mobileLabel = when (s.mobilePolicy) {
        MobileDataPolicy.ASK -> stringResource(R.string.mobile_ask)
        MobileDataPolicy.WIFI_ONLY -> stringResource(R.string.mobile_wifi)
        MobileDataPolicy.ALLOW -> stringResource(R.string.mobile_allow)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "appearance") {
            SectionTitle(stringResource(R.string.sec_appearance))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = shape, colors = cardColors) {
                SettingsRow(Icons.Outlined.Palette, stringResource(R.string.setting_theme), themeLabel, onClick = { themeDialog = true }) { Chevron() }
                SettingsRow(Icons.Outlined.ColorLens, stringResource(R.string.dynamic_color),
                    stringResource(if (dyn) R.string.dynamic_desc else R.string.dynamic_unsupported),
                    onClick = if (dyn) ({ vm.setDynamic(!s.dynamicColor) }) else null, enabled = dyn) {
                    Switch(checked = s.dynamicColor && dyn, onCheckedChange = { vm.setDynamic(it) }, enabled = dyn)
                }
            }
        }
        item(key = "navbar") {
            SectionTitle(stringResource(R.string.sec_navbar))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = shape, colors = cardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavBarPreview(s.navStyle, s.navBlur)
                    NavStyleRow(NavBarStyle.FROSTED_GLASS, s.navStyle, R.string.nav_frosted, R.string.nav_frosted_desc) { vm.setNavStyle(it) }
                    NavStyleRow(NavBarStyle.GLASS, s.navStyle, R.string.nav_glass, R.string.nav_glass_desc) { vm.setNavStyle(it) }
                    NavStyleRow(NavBarStyle.SOLID, s.navStyle, R.string.nav_solid, R.string.nav_solid_desc) { vm.setNavStyle(it) }
                    val blurEnabled = s.navStyle == NavBarStyle.FROSTED_GLASS && BlurSupported
                    Text(stringResource(R.string.nav_blur_intensity) + ": ${s.navBlur}%", style = MaterialTheme.typography.bodyMedium,
                        color = if (blurEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = s.navBlur.toFloat(), onValueChange = { vm.setNavBlur(it.toInt()) }, valueRange = 0f..100f, enabled = blurEnabled)
                    if (!BlurSupported) Text(stringResource(R.string.nav_blur_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "fonts") {
            SectionTitle(stringResource(R.string.sec_fonts))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = shape, colors = cardColors) {
                SettingsRow(Icons.Outlined.TextFields, stringResource(R.string.font_ui), s.uiFont.displayName, onClick = { uiFontDialog = true }) { Chevron() }
                SettingsRow(Icons.Outlined.Terminal, stringResource(R.string.font_terminal), s.terminalFont.displayName, onClick = { termFontDialog = true }) { Chevron() }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { FontPreview(s.uiFont, s.terminalFont) }
                SettingsRow(Icons.Outlined.RestartAlt, stringResource(R.string.font_reset), stringResource(R.string.font_reset_desc),
                    onClick = vm::resetFonts, enabled = s.uiFont != DEFAULT_UI_FONT || s.terminalFont != DEFAULT_TERMINAL_FONT)
            }
        }
        item(key = "downloads") {
            SectionTitle(stringResource(R.string.sec_downloads))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = shape, colors = cardColors) {
                SettingsRow(Icons.Outlined.SignalCellularAlt, stringResource(R.string.mobile_data), mobileLabel, onClick = { mobileDialog = true }) { Chevron() }
            }
        }
        item(key = "storage") {
            SectionTitle(stringResource(R.string.sec_storage))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = shape, colors = cardColors) {
                SettingsRow(Icons.Outlined.Folder, stringResource(R.string.storage_path), vm.baseDir.path)
                SettingsRow(Icons.Outlined.Storage, stringResource(R.string.row_storage_used), used?.let(::fmtBytes) ?: computing)
                SettingsRow(Icons.Outlined.CleaningServices, stringResource(R.string.clear_cache),
                    stringResource(R.string.cache_size) + ": " + (cache?.let(::fmtBytes) ?: computing) + if (busy) " · " + stringResource(R.string.busy_downloading) else "",
                    onClick = { confirmCache = true }, enabled = !busy && (cache ?: 0L) > 0L) { Chevron() }
            }
        }
        item(key = "container") {
            SectionTitle(stringResource(R.string.sec_container))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = shape, colors = cardColors) {
                SettingsRow(Icons.Outlined.AdminPanelSettings, stringResource(R.string.row_priv_mode),
                    level?.let { privilegeText(it) } ?: stringResource(R.string.root_unchecked))
                // Container Root не поддерживается ни одной из текущих сред — показываем как сведение, а не как рабочий переключатель.
                SettingsRow(Icons.Outlined.Security, stringResource(R.string.cont_root), stringResource(R.string.cont_root_unsupported))
            }
        }
        item(key = "app") {
            SectionTitle(stringResource(R.string.sec_app))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = shape, colors = cardColors) {
                val localeOk = Build.VERSION.SDK_INT >= 33
                SettingsRow(Icons.Outlined.Language, stringResource(R.string.sec_language),
                    stringResource(if (localeOk) R.string.language_open else R.string.language_system),
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(Uri.fromParts("package", ctx.packageName, null))) },
                    enabled = localeOk) { if (localeOk) Chevron() }
                SettingsRow(Icons.Outlined.Notifications, stringResource(R.string.sec_notifications), stringResource(R.string.notif_open),
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }) { Chevron() }
                SettingsRow(Icons.Outlined.Description, stringResource(R.string.sec_logs), stringResource(R.string.clear_log),
                    onClick = { ArmLog.clear() }) { Chevron() }
                SettingsRow(Icons.Outlined.Info, stringResource(R.string.about_entry), null, onClick = onOpenAbout) { Chevron() }
            }
        }
    }

    if (uiFontDialog) RadioDialog(
        stringResource(R.string.font_ui), AppFont.entries.map { it to it.displayName }, s.uiFont, vm::setUiFont, { uiFontDialog = false },
        labelFont = { AppFonts.family(it) },
    )
    if (termFontDialog) RadioDialog(
        stringResource(R.string.font_terminal), AppFont.entries.map { it to it.displayName }, s.terminalFont, vm::setTerminalFont, { termFontDialog = false },
        labelFont = { AppFonts.family(it) },
    )
    if (themeDialog) RadioDialog(
        stringResource(R.string.setting_theme),
        listOf(ThemeMode.SYSTEM to stringResource(R.string.theme_system), ThemeMode.LIGHT to stringResource(R.string.theme_light), ThemeMode.DARK to stringResource(R.string.theme_dark)),
        s.themeMode, vm::setTheme, { themeDialog = false },
    )
    if (mobileDialog) RadioDialog(
        stringResource(R.string.mobile_data),
        listOf(MobileDataPolicy.ASK to stringResource(R.string.mobile_ask), MobileDataPolicy.WIFI_ONLY to stringResource(R.string.mobile_wifi), MobileDataPolicy.ALLOW to stringResource(R.string.mobile_allow)),
        s.mobilePolicy, vm::setMobilePolicy, { mobileDialog = false },
    )
    if (confirmCache) AlertDialog(
        onDismissRequest = { confirmCache = false },
        title = { Text(stringResource(R.string.clear_cache_title)) },
        text = { Text(stringResource(R.string.clear_cache_text)) },
        confirmButton = { TextButton(onClick = { confirmCache = false; vm.clearCache() }) { Text(stringResource(R.string.clear_cache)) } },
        dismissButton = { TextButton(onClick = { confirmCache = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
