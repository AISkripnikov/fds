package com.airmaxfield.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Обёртка над ConnectivityManager: список сетей телефона и привязка к ним. */
class AndroidNetworks(ctx: Context) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)

    @Suppress("DEPRECATION")
    fun list(): List<Pair<LocalNet, Network>> = cm.allNetworks.mapNotNull { n ->
        val caps = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
        val lp = cm.getLinkProperties(n)
        val v4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LocalNet.Kind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> LocalNet.Kind.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LocalNet.Kind.CELLULAR
            else -> LocalNet.Kind.OTHER
        }
        LocalNet(
            id = n.toString(), kind = kind,
            ipv4 = v4?.address?.hostAddress, prefix = v4?.prefixLength,
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        ) to n
    }

    /** Сеть Android, выбранная для [targetIp], или null. */
    fun networkFor(targetIp: String): Pair<LocalNet, Network>? {
        val all = list()
        val chosen = chooseNetwork(all.map { it.first }, targetIp) ?: return null
        return all.first { it.first.id == chosen.id }
    }
}
