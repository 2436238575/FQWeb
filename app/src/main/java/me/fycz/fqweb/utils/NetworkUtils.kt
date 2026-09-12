package me.fycz.fqweb.utils

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

object NetworkUtils {

    /**
     * Get local Ip address.
     */
    fun getLocalIPAddress(): InetAddress? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (e: SocketException) {
            log(e)
            null
        } ?: return null

        var fallback: InetAddress? = null
        val nifs = interfaces.toList()
        for (nif in nifs) {
            //跳过 VPN/隧道与点对点接口,优先物理网卡
            if (!nif.isUp || nif.isPointToPoint || nif.isVirtual) continue
            if (nif.name.startsWith("tun") || nif.name.startsWith("tap") || nif.name.startsWith("ppp")) continue
            val addresses = nif.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && isIPv4Address(address.hostAddress)) {
                    if (nif.name.startsWith("wlan") || nif.name.startsWith("eth")) {
                        return address
                    }
                    if (fallback == null) fallback = address
                }
            }
        }
        return fallback
    }

    /**
     * Check if valid IPV4 address.
     *
     * @param input the address string to check for validity.
     * @return True if the input parameter is a valid IPv4 address.
     */
    private val IPV4 =
        "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)$".toRegex()

    fun isIPv4Address(input: String?): Boolean {
        return input != null && IPV4.matches(input)
    }

}