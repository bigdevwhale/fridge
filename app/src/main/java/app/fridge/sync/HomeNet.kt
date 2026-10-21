package app.fridge.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

/**
 * Which local network this phone is on, as a fingerprint: the router's address and the network's
 * own address and size ("192.168.31.1 192.168.31.0/24"). No Wi-Fi name — Android asks for location
 * to tell that — and no location at all. "Home" is then simply a network where the two phones have
 * already met; the background hum listens only there.
 *
 * Only Wi-Fi and Ethernet count: on mobile data there is nobody to meet. A VPN on top doesn't
 * matter, since the Wi-Fi underneath is watched directly.
 */
class HomeNet(context: Context) {
    private val _here = MutableStateFlow<String?>(null)
    val here: StateFlow<String?> = _here.asStateFlow()
    private val nets = ConcurrentHashMap<Network, String>()

    init {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching {
            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                    fingerprint(lp)?.let { nets[network] = it } ?: nets.remove(network)
                    _here.value = nets.values.firstOrNull()
                }

                override fun onLost(network: Network) {
                    nets.remove(network)
                    _here.value = nets.values.firstOrNull()
                }
            })
        }
    }

    companion object {
        fun fingerprint(lp: LinkProperties): String? {
            val link = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
            val gateway = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress ?: "-"
            return "$gateway ${network(link.address.address, link.prefixLength)}/${link.prefixLength}"
        }

        /** The network part of an IPv4 address: 192.168.31.24 with /24 gives 192.168.31.0. */
        fun network(ip: ByteArray, prefix: Int): String {
            val bits = ip.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val n = bits and mask
            return (3 downTo 0).joinToString(".") { ((n shr (it * 8)) and 0xFF).toString() }
        }
    }
}
