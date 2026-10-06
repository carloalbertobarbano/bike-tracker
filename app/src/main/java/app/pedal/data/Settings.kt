package app.pedal.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Units { METRIC, IMPERIAL }

enum class MapStyle(val label: String, val attribution: String) {
    STANDARD("Standard", "© OpenStreetMap contributors"),
    CYCLE("Cycle", "© OpenStreetMap contributors · CyclOSM"),
    TOPO("Terrain", "© OpenStreetMap contributors, SRTM · © OpenTopoMap (CC-BY-SA)"),
    SATELLITE("Satellite", "Imagery © Esri, Maxar, Earthstar Geographics"),
}

data class AppSettings(
    val units: Units = Units.METRIC,
    val autoPause: Boolean = true,
    val keepScreenOn: Boolean = true,
    val offRouteAlert: Boolean = true,
    val offRouteDistanceM: Int = 50,
    val mapStyle: MapStyle = MapStyle.CYCLE,
    val activeRouteId: Long = -1L,
)

/** Tiny SharedPreferences-backed settings store exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val value: AppSettings get() = _state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        prefs.edit()
            .putString("units", next.units.name)
            .putBoolean("autoPause", next.autoPause)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .putBoolean("offRouteAlert", next.offRouteAlert)
            .putInt("offRouteDistanceM", next.offRouteDistanceM)
            .putString("mapStyle", next.mapStyle.name)
            .putLong("activeRouteId", next.activeRouteId)
            .apply()
        _state.value = next
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            units = enumOr(prefs.getString("units", null), d.units),
            autoPause = prefs.getBoolean("autoPause", d.autoPause),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            offRouteAlert = prefs.getBoolean("offRouteAlert", d.offRouteAlert),
            offRouteDistanceM = prefs.getInt("offRouteDistanceM", d.offRouteDistanceM),
            mapStyle = enumOr(prefs.getString("mapStyle", null), d.mapStyle),
            activeRouteId = prefs.getLong("activeRouteId", d.activeRouteId),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
