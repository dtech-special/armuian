package dev.armuian.image

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class ConnectionType { WIFI, MOBILE, NONE }
enum class MobileDataPolicy { ASK, WIFI_ONLY, ALLOW }

sealed interface DownloadDecision {
    data object Proceed : DownloadDecision
    /** Показать предупреждение с размером и кнопками «Отмена» / «Скачать через мобильную сеть». */
    data object NeedMobileConfirmation : DownloadDecision
    data object BlockedNoNetwork : DownloadDecision
    data object BlockedWifiOnly : DownloadDecision
}

object NetworkPolicy {
    fun decide(policy: MobileDataPolicy, type: ConnectionType): DownloadDecision = when (type) {
        ConnectionType.NONE -> DownloadDecision.BlockedNoNetwork
        ConnectionType.WIFI -> DownloadDecision.Proceed
        ConnectionType.MOBILE -> when (policy) {
            MobileDataPolicy.ASK -> DownloadDecision.NeedMobileConfirmation
            MobileDataPolicy.WIFI_ONLY -> DownloadDecision.BlockedWifiOnly
            MobileDataPolicy.ALLOW -> DownloadDecision.Proceed
        }
    }
}

class NetworkTypeDetector(private val context: Context) {
    fun current(): ConnectionType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return ConnectionType.NONE) ?: return ConnectionType.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return ConnectionType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
            else -> ConnectionType.NONE
        }
    }
}
