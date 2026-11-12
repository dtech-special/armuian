package dev.armuian.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.armuian.app.BuildConfig
import dev.armuian.app.data.AppSettings
import dev.armuian.app.data.DisplayMode
import dev.armuian.app.data.SettingsRepository
import dev.armuian.app.data.SystemCatalog
import dev.armuian.app.data.SystemEntry
import dev.armuian.app.data.ThemeMode
import dev.armuian.container.BackendChoice
import dev.armuian.container.BackendKind
import dev.armuian.container.BackendManager
import dev.armuian.container.BackendSelector
import dev.armuian.container.ContainerStatus
import dev.armuian.core.ArmLog
import dev.armuian.core.ArmResult
import dev.armuian.core.ContainerState
import dev.armuian.core.ErrorCode
import dev.armuian.core.ErrorInfo
import dev.armuian.core.toErrorInfo
import dev.armuian.detection.HostDetector
import dev.armuian.detection.HostInfo
import dev.armuian.image.BootstrapPlan
import dev.armuian.image.Bootstrapper
import dev.armuian.image.DownloadDecision
import dev.armuian.image.InstallState
import dev.armuian.image.MobileDataPolicy
import dev.armuian.image.NetworkPolicy
import dev.armuian.image.NetworkTypeDetector
import dev.armuian.image.Stage
import dev.armuian.root.ManagerDetector
import dev.armuian.root.PrivilegeLevel
import dev.armuian.root.ProcessShellRunner
import dev.armuian.root.RootBridge
import dev.armuian.root.RootStatus
import dev.armuian.root.privilegeLevel
import dev.armuian.storage.StorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Мастер установки: подтверждения обязательны на каждом шаге. */
sealed interface InstallFlow {
    data object Idle : InstallFlow
    data class VersionWarning(val entry: SystemEntry) : InstallFlow
    data class Loading(val entry: SystemEntry) : InstallFlow
    data class Confirm(val entry: SystemEntry, val plan: BootstrapPlan) : InstallFlow
    data class MobileWarning(val entry: SystemEntry, val plan: BootstrapPlan) : InstallFlow
    data class Error(val entry: SystemEntry, val info: ErrorInfo) : InstallFlow
}

/** Размер каталога: «вычисляется», «готово» и «ошибка» — три разных состояния; 0 не означает «ещё считаем». */
sealed interface SizeState {
    data object Computing : SizeState
    data class Ready(val bytes: Long) : SizeState
    data object Failed : SizeState
}

data class DeleteRequest(val entry: SystemEntry, val bytes: Long?, val path: String, val removeSource: Boolean)
enum class UpdateResult { NONE_INSTALLED, UP_TO_DATE, FOUND, ERROR }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Рабочий каталог. /data/adb/armuian требует root, поэтому пока staging — во внутренней памяти приложения. */
    val baseDir = File(app.filesDir, "armuian")
    private val storage = StorageManager(baseDir)
    private val bootstrapper = Bootstrapper(app, baseDir)
    private val detector = HostDetector(app)
    private val rootBridge = RootBridge(ProcessShellRunner(), ManagerDetector.forContext(app))
    private val manager = BackendManager(detector, rootBridge, baseDir)
    private val network = NetworkTypeDetector(app)

    /** Анимация появления списка образов проигрывается один раз за жизнь процесса. */
    var imagesIntroPlayed = false

    // ---------- устройство / root / backend ----------
    private val _host = MutableStateFlow<HostInfo?>(null)
    val host: StateFlow<HostInfo?> = _host.asStateFlow()

    private val _root = MutableStateFlow<RootStatus?>(null)
    val rootStatus: StateFlow<RootStatus?> = _root.asStateFlow()
    val privilege: StateFlow<PrivilegeLevel?> = _root.map { it.privilegeLevel() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _snapshot = MutableStateFlow<BackendManager.Snapshot?>(null)
    val snapshot: StateFlow<BackendManager.Snapshot?> = _snapshot.asStateFlow()

    /** Backend, который будет использован: ручной выбор или автоматически подобранный. */
    val effectiveBackend: StateFlow<BackendKind> = combine(settings, _snapshot) { s, snap ->
        BackendSelector.resolve(s.backendChoice, snap?.capabilities ?: emptyMap())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackendKind.SHARED_PID)

    /** Рекомендуемая среда для ЭТОГО устройства: null, пока возможности не проверены. Критерий — реальные проверки, не модель телефона. */
    val recommendedBackend: StateFlow<BackendKind?> = _snapshot.map { snap ->
        snap?.capabilities?.let { caps -> BackendSelector.resolve(BackendChoice.AUTO, caps).takeIf { caps[it]?.canStart == true } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _issueKind = MutableStateFlow<BackendKind?>(null)
    /** Если не null — показываем диалог с причинами недоступности этого backend. */
    val issueDialog: StateFlow<BackendKind?> = _issueKind.asStateFlow()
    fun showIssues(kind: BackendKind? = null) { _issueKind.value = kind ?: effectiveBackend.value }
    fun dismissIssues() { _issueKind.value = null }

    private val _guest = MutableStateFlow<ContainerStatus?>(null)
    val guestStatus: StateFlow<ContainerStatus?> = _guest.asStateFlow()
    private val _runningKind = MutableStateFlow<BackendKind?>(null)
    val runningKind: StateFlow<BackendKind?> = _runningKind.asStateFlow()
    private val _guestLog = MutableStateFlow<List<String>>(emptyList())
    val guestLog: StateFlow<List<String>> = _guestLog.asStateFlow()
    private var monitor: Job? = null

    // ---------- образы ----------
    val entries: StateFlow<List<SystemEntry>> = settings
        .map { s -> SystemCatalog.builtIn + s.customSources.map(SystemCatalog::custom) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SystemCatalog.builtIn)

    val installStates: StateFlow<Map<String, InstallState>> = entries.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyMap())
        else combine(list.map { e -> bootstrapper.observe(e.id).map { s -> e.id to s } }) { it.toMap() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _failedState = MutableStateFlow(false)
    private val stoppingFlow = MutableStateFlow(false)

    /** Реальное состояние для UI: из загрузчика, затем из гостевой сессии, затем из результата проверки. */
    val containerState: StateFlow<ContainerState> = combine(installStates, _guest, _failedState, stoppingFlow) { installs, guest, failed, stopping ->
        val running = installs.values.filterIsInstance<InstallState.Running>().firstOrNull()
        val queued = installs.values.any { it is InstallState.Queued }
        val installed = installs.values.any { it is InstallState.Installed }
        when {
            running != null -> when (running.progress?.stage) {
                Stage.EXTRACTING -> ContainerState.UNPACKING
                Stage.SAVING -> ContainerState.PREPARING
                else -> ContainerState.DOWNLOADING
            }
            queued -> ContainerState.DOWNLOADING
            !installed -> ContainerState.NOT_INSTALLED
            stopping -> ContainerState.STOPPING
            guest?.errorCode != null -> ContainerState.ERROR
            guest?.running == true -> ContainerState.RUNNING
            failed -> ContainerState.UNSUPPORTED
            else -> ContainerState.READY
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ContainerState.NOT_INSTALLED)

    // ---------- хранилище ----------
    private val _storageUsed = MutableStateFlow<Long?>(null)
    /** null = ещё вычисляется. */
    val storageUsed: StateFlow<Long?> = _storageUsed.asStateFlow()
    private val _cacheBytes = MutableStateFlow<Long?>(null)
    val cacheBytes: StateFlow<Long?> = _cacheBytes.asStateFlow()
    private val _sizes = MutableStateFlow<Map<String, SizeState>>(emptyMap())
    val installedSizes: StateFlow<Map<String, SizeState>> = _sizes.asStateFlow()

    private val _error = MutableStateFlow<ArmResult.Fail?>(null)
    val error: StateFlow<ArmResult.Fail?> = _error.asStateFlow()
    fun consumeError() { _error.value = null }
    private fun report(r: ArmResult<*>) { if (r is ArmResult.Fail) { ArmLog.e("Storage", "error_detail", r.message); _error.value = r } }

    private val _flow = MutableStateFlow<InstallFlow>(InstallFlow.Idle)
    val installFlow: StateFlow<InstallFlow> = _flow.asStateFlow()
    private val _delete = MutableStateFlow<DeleteRequest?>(null)
    val deleteRequest: StateFlow<DeleteRequest?> = _delete.asStateFlow()

    private val _updates = MutableStateFlow<Set<String>>(emptySet())
    val updates: StateFlow<Set<String>> = _updates.asStateFlow()
    private val _updatesBusy = MutableStateFlow(false)
    val updatesBusy: StateFlow<Boolean> = _updatesBusy.asStateFlow()
    private val _updateResult = MutableStateFlow<UpdateResult?>(null)
    val updateResult: StateFlow<UpdateResult?> = _updateResult.asStateFlow()

    val hasActiveDownload: StateFlow<Boolean> = installStates
        .map { m -> m.values.any { it is InstallState.Running || it is InstallState.Queued } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        ArmLog.i("App", "app_started", BuildConfig.VERSION_NAME, baseDir.path)
        viewModelScope.launch { _host.value = withContext(Dispatchers.IO) { detector.detect() } }
        viewModelScope.launch { report(storage.ensureLayout()) }
        viewModelScope.launch {
            // Состояние образов изменилось (установлен/удалён) — пересчитываем размеры и возможности (без запроса root).
            installStates.map { m -> m.mapValues { it.value::class } }.distinctUntilChanged().collect {
                refreshStorage()
                refreshCapabilities(askRoot = false)
            }
        }
        viewModelScope.launch { restoreGuestSession() }
    }

    // ---------- размеры ----------
    private suspend fun refreshStorage() {
        val installed = entries.value.filter { installStates.value[it.id] is InstallState.Installed }
        // Помечаем «Вычисляется», только если готового значения ещё нет (не мигаем при пересчёте).
        _sizes.value = installed.associate { e -> e.id to (_sizes.value[e.id] ?: SizeState.Computing) }
        withContext(Dispatchers.IO) {
            runCatching { storage.usedBytes() }.onSuccess { _storageUsed.value = it }
            runCatching { storage.usedBytes("cache") }.onSuccess { _cacheBytes.value = it }
            for (e in installed) {
                val r = runCatching { bootstrapper.installedSize(e.id) }
                _sizes.value = _sizes.value + (e.id to r.fold({ SizeState.Ready(it) }, { SizeState.Failed }))
            }
        }
    }

    // ---------- возможности backend ----------
    private fun currentImage(): SystemEntry? = entries.value.firstOrNull { installStates.value[it.id] is InstallState.Installed }

    private suspend fun refreshCapabilities(askRoot: Boolean) {
        val img = currentImage()
        val snap = manager.refresh(img?.id, img?.androidMajor, askRoot)
        _snapshot.value = snap
        _host.value = snap.host
        _root.value = snap.root
    }

    fun checkRoot() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try { refreshCapabilities(askRoot = true) } finally { _busy.value = false }
        }
    }

    fun runCompat() = checkRoot()
    fun diagRoot() { ArmLog.i("console", "raw", "$ root-check"); checkRoot() }
    fun diagCompat() { ArmLog.i("console", "raw", "$ compat-check"); checkRoot() }
    fun diagDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            ArmLog.i("console", "raw", "$ system-info")
            val h = detector.detect()
            _host.value = h
            ArmLog.i("system", "raw", "${h.deviceModel}, SoC=${h.soc ?: "n/a"}, CPU cores=${h.cpuCores}, RAM=${h.totalRamBytes ?: "n/a"}")
            ArmLog.i("system", "raw", "ABIs=${h.supportedAbis.joinToString()}, SELinux=${h.selinux ?: "n/a"}, AVF=${h.avfFeature}")
            ArmLog.i("system", "raw", "binder=${h.binderDeviceVisible}, binderfs=${h.binderFsVisible}, kvm=${h.kvmVisible}, ns=${h.namespaces?.joinToString(" ") ?: "n/a"}")
        }
    }

    // ---------- запуск / остановка ----------
    fun startEnvironment() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                refreshCapabilities(askRoot = true)
                val snap = _snapshot.value ?: return@launch
                val kind = BackendSelector.resolve(settings.value.backendChoice, snap.capabilities)
                val cap = snap.capabilities.getValue(kind)
                _failedState.value = cap.availability == dev.armuian.container.Availability.UNSUPPORTED
                if (!cap.canStart) {
                    ArmLog.w("App", "start_blocked", kind.name, cap.availability.name)
                    _issueKind.value = kind // конкретная причина — в диалоге
                    return@launch
                }
                val img = currentImage() ?: return@launch
                val r = manager.backend(kind).start(img.id)
                refreshGuest(kind)
                if (r is ArmResult.Fail) {
                    ArmLog.w("App", "start_failed", r.message)
                    _error.value = r
                    appendGuestLogToConsole()
                } else startMonitor(kind)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                ArmLog.ex("App", t)
                _error.value = ArmResult.Fail(t.message ?: t.javaClass.simpleName, t)
            } finally { _busy.value = false }
        }
    }

    fun stopEnvironment() {
        val kind = _runningKind.value ?: return
        viewModelScope.launch {
            stoppingFlow.value = true
            try {
                monitor?.cancel()
                val r = manager.backend(kind).stop()
                if (r is ArmResult.Fail && r.code != ErrorCode.NOT_RUNNING) _error.value = r
                refreshGuest(kind)
            } finally { stoppingFlow.value = false }
        }
    }

    fun restartEnvironment() {
        val kind = _runningKind.value ?: return
        val img = currentImage() ?: return
        viewModelScope.launch {
            stoppingFlow.value = true
            try {
                monitor?.cancel()
                val r = manager.backend(kind).restart(img.id)
                refreshGuest(kind)
                if (r is ArmResult.Fail) _error.value = r else startMonitor(kind)
            } finally { stoppingFlow.value = false }
        }
    }

    private suspend fun refreshGuest(kind: BackendKind) {
        val st = manager.backend(kind).status()
        _guest.value = st
        _runningKind.value = if (st.running) kind else null
        _guestLog.value = manager.backend(kind).logs()
    }

    private fun appendGuestLogToConsole() {
        _guestLog.value.takeLast(8).forEach { ArmLog.w("guest", "raw", it) }
    }

    /** Восстановление после перезапуска приложения: только если остались следы прошлой сессии. */
    private suspend fun restoreGuestSession() {
        for (k in listOf(BackendKind.SHARED_PID, BackendKind.NATIVE_CONTAINER)) {
            val b = manager.backend(k)
            if (b.hasSession()) {
                refreshGuest(k)
                if (_runningKind.value != null) { startMonitor(k); return }
            }
        }
    }

    /** Редкий опрос (раз в 10 с, только пока гость запущен); потерю root определяем по недоступности su. */
    private fun startMonitor(kind: BackendKind) {
        monitor?.cancel()
        monitor = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                val st = manager.backend(kind).status()
                _guest.value = st
                if (st.errorCode == ErrorCode.ROOT_LOST) {
                    ArmLog.e("App", "root_lost")
                    _root.value = RootStatus.Denied("Root access lost while the guest was running")
                    _runningKind.value = null
                    break
                }
                if (!st.running) { _runningKind.value = null; _guestLog.value = manager.backend(kind).logs(); break }
            }
        }
    }

    // ---------- установка ----------
    fun beginInstall(e: SystemEntry) {
        val hostMajor = _host.value?.androidMajor
        if (e.androidMajor != null && hostMajor != null && e.androidMajor != hostMajor) _flow.value = InstallFlow.VersionWarning(e)
        else loadPlan(e)
    }

    fun continueAfterWarning(e: SystemEntry) = loadPlan(e)

    private fun loadPlan(e: SystemEntry) {
        _flow.value = InstallFlow.Loading(e)
        viewModelScope.launch {
            try {
                val plan = bootstrapper.plan(e.id, e.ref, settings.value.mobilePolicy, network.current())
                _flow.value = InstallFlow.Confirm(e, plan)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                val info = t.toErrorInfo()
                ArmLog.e("Bootstrapper", "error_detail", info.message)
                _flow.value = InstallFlow.Error(e, info)
            }
        }
    }

    fun confirmInstall(e: SystemEntry, plan: BootstrapPlan) {
        // Сеть могла измениться, пока открыт экран подтверждения — решение принимаем заново.
        when (NetworkPolicy.decide(settings.value.mobilePolicy, network.current())) {
            DownloadDecision.Proceed -> startNow(plan)
            DownloadDecision.NeedMobileConfirmation -> _flow.value = InstallFlow.MobileWarning(e, plan)
            else -> _flow.value = InstallFlow.Idle
        }
    }

    fun confirmMobile(plan: BootstrapPlan) = startNow(plan)

    private fun startNow(plan: BootstrapPlan) {
        report(storage.ensureLayout())
        bootstrapper.start(plan, unmeteredOnly = settings.value.mobilePolicy == MobileDataPolicy.WIFI_ONLY)
        _flow.value = InstallFlow.Idle
    }

    fun dismissFlow() { _flow.value = InstallFlow.Idle }
    fun pause(e: SystemEntry) = bootstrapper.pause(e.id)
    fun cancel(e: SystemEntry) { viewModelScope.launch { bootstrapper.cancel(e.id); refreshStorage() } }

    // ---------- удаление ----------
    /** Образ занят, если из него запущена гостевая сессия (запущенный контейнер). */
    private fun isImageInUse(id: String): Boolean {
        val k = _runningKind.value ?: return false
        return manager.backend(k).currentImage() == id
    }

    fun requestDelete(e: SystemEntry) {
        if (isImageInUse(e.id)) {
            _error.value = ArmResult.Fail("Image is used by the running container", null, ErrorCode.IMAGE_IN_USE, listOf(e.title))
            return
        }
        viewModelScope.launch {
            val installed = installStates.value[e.id] is InstallState.Installed
            val size = if (!installed) 0L else (_sizes.value[e.id] as? SizeState.Ready)?.bytes
                ?: withContext(Dispatchers.IO) { runCatching { bootstrapper.installedSize(e.id) }.getOrNull() }
            _delete.value = DeleteRequest(e, size, bootstrapper.imageDir(e.id).path, removeSource = e.custom)
        }
    }

    fun dismissDelete() { _delete.value = null }

    fun confirmDelete() {
        val req = _delete.value ?: return
        _delete.value = null
        viewModelScope.launch {
            if (isImageInUse(req.entry.id)) { // состояние могло измениться, пока был открыт диалог
                _error.value = ArmResult.Fail("Image is used by the running container", null, ErrorCode.IMAGE_IN_USE, listOf(req.entry.title))
                return@launch
            }
            val r = bootstrapper.delete(req.entry.id)
            if (r is ArmResult.Fail) { _error.value = r; return@launch }
            if (req.removeSource) {
                settingsRepo.removeSource(req.entry.ref)
                ArmLog.w("App", "source_removed", req.entry.ref)
            }
            _updates.value = _updates.value - req.entry.id
            _sizes.value = _sizes.value - req.entry.id
            refreshStorage()
            refreshCapabilities(askRoot = false)
        }
    }

    // ---------- обновления ----------
    fun checkUpdates() {
        if (_updatesBusy.value) return
        viewModelScope.launch {
            _updatesBusy.value = true
            try {
                val installed = entries.value.filter { installStates.value[it.id] is InstallState.Installed }
                if (installed.isEmpty()) { _updateResult.value = UpdateResult.NONE_INSTALLED; return@launch }
                val newer = mutableSetOf<String>()
                for (e in installed) {
                    val have = bootstrapper.readMetadata(e.id)?.manifestDigest
                    val latest = bootstrapper.latestDigest(e.ref)
                    if (have != null && have != latest) newer += e.id
                }
                _updates.value = newer
                _updateResult.value = if (newer.isEmpty()) UpdateResult.UP_TO_DATE else UpdateResult.FOUND
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                ArmLog.e("Updates", "error_detail", t.message ?: ""); _updateResult.value = UpdateResult.ERROR
            } finally { _updatesBusy.value = false }
        }
    }

    fun consumeUpdateResult() { _updateResult.value = null }

    // ---------- настройки ----------
    fun setTheme(m: ThemeMode) { viewModelScope.launch { settingsRepo.setTheme(m) } }
    fun setDynamic(v: Boolean) { viewModelScope.launch { settingsRepo.setDynamic(v) } }
    fun setMobilePolicy(p: MobileDataPolicy) { viewModelScope.launch { settingsRepo.setMobilePolicy(p) } }
    fun setBackend(c: BackendChoice) { viewModelScope.launch { settingsRepo.setBackend(c) } }
    fun setDisplayMode(m: DisplayMode) { viewModelScope.launch { settingsRepo.setDisplay(m) } }
    fun setUiFont(f: dev.armuian.app.data.AppFont) { viewModelScope.launch { settingsRepo.setUiFont(f) } }
    fun setTerminalFont(f: dev.armuian.app.data.AppFont) { viewModelScope.launch { settingsRepo.setTerminalFont(f) } }
    fun resetFonts() { viewModelScope.launch { settingsRepo.resetFonts() } }
    fun setNavStyle(s: dev.armuian.app.data.NavBarStyle) { viewModelScope.launch { settingsRepo.setNavStyle(s) } }
    fun setNavBlur(v: Int) { viewModelScope.launch { settingsRepo.setNavBlur(v) } }
    fun addSource(ref: String) { viewModelScope.launch { settingsRepo.addSource(ref) } }

    fun clearCache() {
        if (hasActiveDownload.value) return
        viewModelScope.launch { report(withContext(Dispatchers.IO) { storage.clearCache() }); refreshStorage() }
    }

    // ---------- консоль ----------
    fun logText(): String {
        val f = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        val res = getApplication<Application>().resources
        val pkg = getApplication<Application>().packageName
        return ArmLog.entries.value.joinToString("\n") { "[${f.format(Date(it.timeMs))}] ${it.level} ${it.tag}: ${it.localized(res, pkg)}" }
    }
}
