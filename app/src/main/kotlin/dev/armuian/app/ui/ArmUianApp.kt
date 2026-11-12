package dev.armuian.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.R
import dev.armuian.app.ui.screens.AboutScreen
import dev.armuian.app.ui.screens.ConsoleScreen
import dev.armuian.app.ui.screens.DeviceScreen
import dev.armuian.app.ui.screens.HomeScreen
import dev.armuian.app.ui.screens.SettingsScreen
import dev.armuian.app.ui.screens.SystemsScreen
import dev.armuian.core.ArmLog
import kotlinx.coroutines.launch

/** Вкладки: «Главная», «Образы» (скачивание), «Система» (устройство и среда выполнения), «Консоль», «Настройки». */
enum class Tab(val titleRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    IMAGES(R.string.tab_images, Icons.Outlined.Download, Icons.Filled.Download),
    SYSTEM(R.string.tab_system, Icons.Outlined.Memory, Icons.Filled.Memory),
    CONSOLE(R.string.tab_console, Icons.Outlined.Terminal, Icons.Filled.Terminal),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmUianApp(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showLaunchLog by rememberSaveable { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val backdrop = rememberBackdropState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val updatesBusy by vm.updatesBusy.collectAsStateWithLifecycle()
    val updateResult by vm.updateResult.collectAsStateWithLifecycle()

    // Состояние прокрутки каждой вкладки хранится здесь и не сбрасывается при переключении.
    val homeState = rememberLazyListState()
    val imagesState = rememberLazyListState()
    val systemState = rememberLazyListState()
    val settingsState = rememberLazyListState()

    val copiedText = stringResource(R.string.copied)
    val logActionLabel = stringResource(R.string.open_launch_log)
    // Копирование из журналов: единая точка, подтверждение — снэкбар.
    val onCopy: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar(copiedText) }
    }
    val msgNone = stringResource(R.string.no_installed_to_check)
    val msgUpToDate = stringResource(R.string.update_none)
    val msgFound = stringResource(R.string.update_found)
    val msgError = stringResource(R.string.update_error)
    LaunchedEffect(updateResult) {
        val r = updateResult ?: return@LaunchedEffect
        val text = when (r) {
            UpdateResult.NONE_INSTALLED -> msgNone
            UpdateResult.UP_TO_DATE -> msgUpToDate
            UpdateResult.FOUND -> msgFound
            UpdateResult.ERROR -> msgError
        }
        vm.consumeUpdateResult()
        snackbar.showSnackbar(text)
    }

    val failure by vm.error.collectAsStateWithLifecycle()
    val failureText = failure?.let { errorText(it.toInfo()) }
    LaunchedEffect(failureText) {
        if (failureText != null) {
            vm.consumeError()
            // К ошибке запуска сразу предлагаем открыть подробный журнал.
            val r = snackbar.showSnackbar(failureText, actionLabel = logActionLabel, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) showLaunchLog = true
        }
    }

    BackHandler(showAbout) { showAbout = false }

    // Место под плавающую панель: её высота + отступ + системная навигация.
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = if (showAbout) 0.dp else NavBarHeight + NavBarMargin + navInset + 8.dp

    CompositionLocalProvider(LocalBottomBarPadding provides barSpace) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.backdropSource(backdrop, enabled = settings.navStyle == dev.armuian.app.data.NavBarStyle.FROSTED_GLASS && BlurSupported),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(if (showAbout) R.string.about_entry else tab.titleRes)) },
                        navigationIcon = {
                            if (showAbout) IconButton(onClick = { showAbout = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        },
                        actions = {
                            if (!showAbout) when (tab) {
                                Tab.IMAGES -> {
                                    IconButton(onClick = vm::checkUpdates, enabled = !updatesBusy) {
                                        if (updatesBusy) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Outlined.Refresh, stringResource(R.string.check_updates))
                                    }
                                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.add_source)) }
                                }
                                Tab.CONSOLE -> {
                                    IconButton(onClick = { onCopy(vm.logText()) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.console_copy)) }
                                    IconButton(onClick = { ArmLog.clear() }) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.console_clear)) }
                                }
                                else -> {}
                            }
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    Crossfade(targetState = if (showAbout) null else tab, label = "nav") { t ->
                        when (t) {
                            null -> AboutScreen()
                            Tab.HOME -> HomeScreen(vm, homeState, onOpenLaunchLog = { showLaunchLog = true })
                            Tab.IMAGES -> SystemsScreen(vm, imagesState)
                            Tab.SYSTEM -> DeviceScreen(vm, systemState)
                            Tab.CONSOLE -> ConsoleScreen(vm, onCopy)
                            Tab.SETTINGS -> SettingsScreen(vm, settingsState, onOpenAbout = { showAbout = true })
                        }
                    }
                }
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = barSpace))
            if (!showAbout) FloatingNavBar(
                tabs = Tab.entries, selected = tab, onSelect = { tab = it },
                style = settings.navStyle, blurPercent = settings.navBlur, backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    InstallFlowHost(vm)
    DeleteDialog(vm)
    IssuesDialog(vm)
    if (showLaunchLog) LaunchLogDialog(onDismiss = { showLaunchLog = false }, onCopy = onCopy)
    if (showAdd) AddSourceDialog(onDismiss = { showAdd = false }, onAdd = vm::addSource)
}
