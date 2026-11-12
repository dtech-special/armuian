package dev.armuian.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.armuian.container.BackendChoice
import dev.armuian.image.MobileDataPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Семейства шрифтов приложения (все — SIL OFL 1.1, variable-шрифты с осью насыщенности). */
enum class AppFont(val displayName: String) {
    INTER("Inter"), GOOGLE_SANS_FLEX("Google Sans Flex"), JETBRAINS_MONO("JetBrains Mono"),
}

/** Оформление плавающей панели навигации. Геометрия у всех трёх одинакова — отличается только поверхность. */
enum class NavBarStyle { FROSTED_GLASS, GLASS, SOLID }

/** Способ показа гостевой среды (графический вывод пока не подключён — см. GuestView). */
enum class DisplayMode { FULLSCREEN, WINDOW, EMBEDDED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val mobilePolicy: MobileDataPolicy = MobileDataPolicy.ASK,
    val customSources: List<String> = emptyList(),
    val backendChoice: BackendChoice = BackendChoice.AUTO,
    val displayMode: DisplayMode = DisplayMode.EMBEDDED,
    val uiFont: AppFont = DEFAULT_UI_FONT,
    val terminalFont: AppFont = DEFAULT_TERMINAL_FONT,
    val navStyle: NavBarStyle = NavBarStyle.FROSTED_GLASS,
    /** Интенсивность размытия панели, 0..100. */
    val navBlur: Int = DEFAULT_NAV_BLUR,
)

val DEFAULT_UI_FONT = AppFont.INTER
val DEFAULT_TERMINAL_FONT = AppFont.JETBRAINS_MONO
const val DEFAULT_NAV_BLUR = 50

private val Context.armDataStore by preferencesDataStore(name = "armuian_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic")
        val MOBILE = stringPreferencesKey("mobile")
        val SOURCES = stringSetPreferencesKey("sources")
        val BACKEND = stringPreferencesKey("backend")
        val DISPLAY = stringPreferencesKey("display")
        val UI_FONT = stringPreferencesKey("ui_font")
        val TERM_FONT = stringPreferencesKey("terminal_font")
        val NAV_STYLE = stringPreferencesKey("nav_style")
        val NAV_BLUR = intPreferencesKey("nav_blur")
    }

    val settings: Flow<AppSettings> = context.armDataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            mobilePolicy = p[Keys.MOBILE]?.let { runCatching { MobileDataPolicy.valueOf(it) }.getOrNull() } ?: MobileDataPolicy.ASK,
            customSources = (p[Keys.SOURCES] ?: emptySet()).sorted(),
            backendChoice = p[Keys.BACKEND]?.let { runCatching { BackendChoice.valueOf(it) }.getOrNull() } ?: BackendChoice.AUTO,
            uiFont = p[Keys.UI_FONT]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: DEFAULT_UI_FONT,
            terminalFont = p[Keys.TERM_FONT]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: DEFAULT_TERMINAL_FONT,
            navStyle = p[Keys.NAV_STYLE]?.let { runCatching { NavBarStyle.valueOf(it) }.getOrNull() } ?: NavBarStyle.FROSTED_GLASS,
            navBlur = (p[Keys.NAV_BLUR] ?: DEFAULT_NAV_BLUR).coerceIn(0, 100),
            displayMode = p[Keys.DISPLAY]?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() } ?: DisplayMode.EMBEDDED,
        )
    }

    suspend fun setTheme(m: ThemeMode) { context.armDataStore.edit { it[Keys.THEME] = m.name } }
    suspend fun setDynamic(v: Boolean) { context.armDataStore.edit { it[Keys.DYNAMIC] = v } }
    suspend fun setMobilePolicy(p: MobileDataPolicy) { context.armDataStore.edit { it[Keys.MOBILE] = p.name } }
    suspend fun setBackend(c: BackendChoice) { context.armDataStore.edit { it[Keys.BACKEND] = c.name } }
    suspend fun setDisplay(m: DisplayMode) { context.armDataStore.edit { it[Keys.DISPLAY] = m.name } }
    suspend fun setUiFont(f: AppFont) { context.armDataStore.edit { it[Keys.UI_FONT] = f.name } }
    suspend fun setTerminalFont(f: AppFont) { context.armDataStore.edit { it[Keys.TERM_FONT] = f.name } }
    suspend fun resetFonts() { context.armDataStore.edit { it.remove(Keys.UI_FONT); it.remove(Keys.TERM_FONT) } }
    suspend fun setNavStyle(st: NavBarStyle) { context.armDataStore.edit { it[Keys.NAV_STYLE] = st.name } }
    suspend fun setNavBlur(v: Int) { context.armDataStore.edit { it[Keys.NAV_BLUR] = v.coerceIn(0, 100) } }
    suspend fun addSource(ref: String) { context.armDataStore.edit { it[Keys.SOURCES] = (it[Keys.SOURCES] ?: emptySet()) + ref } }
    suspend fun removeSource(ref: String) { context.armDataStore.edit { it[Keys.SOURCES] = (it[Keys.SOURCES] ?: emptySet()) - ref } }
}
