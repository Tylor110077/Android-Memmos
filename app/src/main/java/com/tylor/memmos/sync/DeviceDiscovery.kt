package com.tylor.memmos.sync

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * 局域网设备发现：广播探测包（MEMMOS_DISCOVER_V1 → UDP 28423），
 * 收集 Obsidian memos-graph 的回报（设备名 / host / TCP 端口 / 配对码）。
 *
 * 目标地址：受限广播 255.255.255.255 + DHCP 网段广播 + 模拟器宿主 10.0.2.2。
 * WiFi 下部分驱动会过滤广播，用 MulticastLock 放行。
 */
object DeviceDiscovery {

    data class Device(val name: String, val host: String, val port: Int, val pairCode: String)

    private const val REQ = "MEMMOS_DISCOVER_V1"
    private const val DISCOVERY_PORT = 28423

    suspend fun discover(ctx: Context, timeoutMs: Long = 3500): List<Device> = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("memmos-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val results = linkedMapOf<String, Device>()
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.soTimeout = 150
                socket.bind(InetSocketAddress(0))

                val payload = REQ.toByteArray()
                val targets = buildList {
                    add(InetAddress.getByName("255.255.255.255"))
                    add(InetAddress.getByName("10.0.2.2")) // 模拟器宿主
                    runCatching {
                        val ip = wifi.dhcpInfo?.ipAddress ?: 0
                        val mask = wifi.dhcpInfo?.netmask ?: 0
                        if (ip != 0 && mask != 0) {
                            val bc = ip or mask.inv()
                            add(InetAddress.getByAddress(
                                byteArrayOf(
                                    (bc and 0xFF).toByte(), (bc shr 8 and 0xFF).toByte(),
                                    (bc shr 16 and 0xFF).toByte(), (bc shr 24 and 0xFF).toByte(),
                                ),
                            ))
                        }
                    }
                }
                targets.forEach { addr ->
                    runCatching {
                        socket.send(DatagramPacket(payload, payload.size, addr, DISCOVERY_PORT))
                    }
                }

                val buf = ByteArray(1024)
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeoutMs) {
                    val pkt = DatagramPacket(buf, buf.size)
                    val received = runCatching { socket.receive(pkt); true }.getOrDefault(false)
                    if (!received) continue
                    val text = String(pkt.data, 0, pkt.length)
                    if (!text.startsWith("{")) continue
                    runCatching {
                        val o = JSONObject(text)
                        if (o.optString("service") == "memmos-sync") {
                            val host = pkt.address.hostAddress ?: o.optString("host")
                            val dev = Device(
                                name = o.optString("name").ifBlank { "未知设备" },
                                host = host,
                                port = o.optInt("port", 28422),
                                pairCode = o.optString("code"),
                            )
                            results[dev.host] = dev
                        }
                    }
                }
            }
            results.values.toList()
        } finally {
            lock.release()
        }
    }
}
