package com.antigravity.shieldx.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.antigravity.shieldx.R
import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.DeviceMode
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.repository.AuditRepository
import com.antigravity.shieldx.data.repository.DomainRepository
import com.antigravity.shieldx.data.repository.PolicyRepository
import com.antigravity.shieldx.policy.SafeSearchEnforcer
import com.antigravity.shieldx.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ProtectionVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.antigravity.shieldx.vpn.START"
        const val ACTION_STOP = "com.antigravity.shieldx.vpn.STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "shieldx_vpn_channel"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, ProtectionVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProtectionVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)

    private lateinit var database: AppDatabase
    private lateinit var domainRepository: DomainRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var domainMatcher: DomainMatcher
    private lateinit var safeSearchEnforcer: SafeSearchEnforcer
    private lateinit var dnsFilter: DnsFilter
    private lateinit var packetParser: VpnPacketParser
    private lateinit var healthMonitor: VpnHealthMonitor

    private val upstreamDns = InetAddress.getByName("1.1.1.3") // Cloudflare Family (Malware + Adult Blocking)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        domainRepository = DomainRepository(this, database)
        policyRepository = PolicyRepository(database)
        auditRepository = AuditRepository(database)
        domainMatcher = DomainMatcher()
        safeSearchEnforcer = SafeSearchEnforcer()
        dnsFilter = DnsFilter(domainMatcher, safeSearchEnforcer)
        packetParser = VpnPacketParser()

        healthMonitor = VpnHealthMonitor(
            onRestartRequested = { startVpnInternal() },
            onFatalFailure = { reason ->
                serviceScope.launch {
                    auditRepository.logTamperEvent("VPN_FATAL_FAILURE", "CRITICAL", reason, com.antigravity.shieldx.core.model.TamperState.SUSPICIOUS)
                }
            }
        )

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopVpnInternal()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startVpnInternal()
        return START_STICKY
    }

    private fun startVpnInternal() {
        if (isRunning.get()) return

        serviceScope.launch {
            // Load latest rules into domain matcher
            val rules = domainRepository.getAllRules()
            domainMatcher.loadRules(rules)

            try {
                val builder = Builder()
                    .addAddress("10.254.1.2", 24)
                    .addAddress("fd00::2", 64)
                    .addDnsServer("10.254.1.1")
                    .addDnsServer("fd00::1")
                    // Plaintext DNS aimed at our virtual gateway (IPv4 & IPv6).
                    .addRoute("10.254.1.1", 32)
                    .addRoute("fd00::1", 128)
                    .setSession(getString(R.string.vpn_service_title))
                    .setMtu(1500)
                    .setBlocking(true)

                // Pull every known DoH/DoT resolver into the tunnel so the loop
                // can drop it. Without these routes the traffic never reaches us
                // and browser-native encrypted DNS sails straight past the filter.
                var routedResolvers = 0
                for (resolver in EncryptedDnsBlocker.routableEndpoints()) {
                    try {
                        builder.addRoute(resolver, 32)
                        routedResolvers++
                    } catch (_: IllegalArgumentException) {
                        // A malformed address must not take the whole tunnel down.
                    }
                }

                // IPv6 matters here: this device is on a carrier handing out
                // native IPv6, and the tunnel previously routed none of it, so
                // encrypted DNS over IPv6 bypassed the filter entirely.
                for (resolver in EncryptedDnsBlocker.routableEndpointsV6()) {
                    try {
                        builder.addRoute(resolver, 128)
                        routedResolvers++
                    } catch (_: IllegalArgumentException) {
                    }
                }

                // Never filter ourselves; doing so deadlocks our own upstream DNS.
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    healthMonitor.recordServiceCrash("Builder.establish() returned null")
                    return@launch
                }

                isRunning.set(true)
                _isRunningFlow.value = true
                healthMonitor.start()

                android.util.Log.i(
                    "TarziVpn",
                    "[VPN_UP] tunnel established, " + routedResolvers +
                        " encrypted-DNS resolvers routed for blocking"
                )

                // Launch TUN packet processing loop
                launchPacketProcessingLoop(vpnInterface!!)

            } catch (e: Exception) {
                healthMonitor.recordServiceCrash("Failed to initialize VPN interface: ${e.message}")
            }
        }
    }

    private fun launchPacketProcessingLoop(pfd: ParcelFileDescriptor) {
        serviceScope.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteBuffer.allocate(32768)

            try {
                while (isActive && isRunning.get()) {
                    buffer.clear()
                    val bytesRead = inputStream.read(buffer.array())
                    if (bytesRead <= 0) continue

                    healthMonitor.recordPacketActivity()
                    val parsed = packetParser.parse(buffer, bytesRead) ?: continue

                    // Encrypted DNS: drop rather than forward. The browser's DoH
                    // or DoT attempt then fails and it falls back to system DNS,
                    // which is the traffic this loop can actually inspect.
                    if (EncryptedDnsBlocker.shouldDrop(parsed.destIp, parsed.destPort, parsed.protocol)) {
                        launch {
                            auditRepository.logBlockedEvent(
                                target = parsed.destIp.joinToString(".") { (it.toInt() and 0xFF).toString() },
                                category = Category.OTHER_EXPLICIT,
                                reason = BlockReason.SAFESEARCH_ENFORCEMENT,
                                deviceMode = DeviceMode.NORMAL_CONSUMER,
                                details = "Encrypted DNS bypass blocked (port " + parsed.destPort + ")"
                            )
                        }
                        continue
                    }

                    // Process DNS UDP requests
                    if (parsed.protocol == VpnPacketParser.PROTOCOL_UDP && parsed.destPort == VpnPacketParser.PORT_DNS) {
                        val dnsQuery = dnsFilter.parseQuery(buffer.array(), parsed.payloadOffset, parsed.payloadLength)
                        if (dnsQuery != null) {
                            val policy = policyRepository.getPolicy()
                            val evalResult = dnsFilter.evaluate(dnsQuery, safeSearchEnabled = policy.safeSearchEnabled)

                            when (evalResult.action) {
                                PolicyDecision.BLOCK -> {
                                    // Log blocked event asynchronously
                                    launch {
                                        auditRepository.logBlockedEvent(
                                            target = dnsQuery.qName,
                                            category = evalResult.category,
                                            reason = evalResult.reason ?: BlockReason.KNOWN_ADULT_DOMAIN,
                                            deviceMode = DeviceMode.NORMAL_CONSUMER,
                                            details = "DNS query blocked and sinkholed"
                                        )
                                    }
                                    // Synthesize sinkhole response
                                    val responseIpPacket = dnsFilter.wrapIpUdp(
                                        srcIp = parsed.destIp,
                                        dstIp = parsed.sourceIp,
                                        srcPort = parsed.destPort,
                                        dstPort = parsed.sourcePort,
                                        dnsPayload = evalResult.responseBytes!!,
                                        ipVersion = parsed.ipVersion
                                    )
                                    outputStream.write(responseIpPacket)
                                }
                                PolicyDecision.RESTRICT -> {
                                    // SafeSearch rewrite
                                    val responseIpPacket = dnsFilter.wrapIpUdp(
                                        srcIp = parsed.destIp,
                                        dstIp = parsed.sourceIp,
                                        srcPort = parsed.destPort,
                                        dstPort = parsed.sourcePort,
                                        dnsPayload = evalResult.responseBytes!!,
                                        ipVersion = parsed.ipVersion
                                    )
                                    outputStream.write(responseIpPacket)
                                }
                                PolicyDecision.ALLOW, PolicyDecision.UNCERTAIN -> {
                                    // Forward to upstream secure family DNS.
                                    //
                                    // Each in-flight query gets its own socket. A
                                    // single socket shared across concurrent
                                    // coroutines - the previous approach - lets one
                                    // query's receive() steal another's response,
                                    // which times out the loser after 3 seconds and
                                    // forces the OS resolver to retry. Under normal
                                    // multi-app DNS load that reads as "the internet
                                    // got slower" even though no bulk traffic is
                                    // actually routed through this tunnel.
                                    launch(Dispatchers.IO) {
                                        var querySocket: DatagramSocket? = null
                                        try {
                                            querySocket = DatagramSocket().also {
                                                protect(it)
                                                it.soTimeout = 2500
                                            }

                                            val queryPacket = DatagramPacket(
                                                dnsQuery.rawDnsBytes,
                                                dnsQuery.rawDnsBytes.size,
                                                upstreamDns,
                                                53
                                            )
                                            querySocket.send(queryPacket)

                                            val respBuffer = ByteArray(2048)
                                            val respPacket = DatagramPacket(respBuffer, respBuffer.size)
                                            querySocket.receive(respPacket)

                                            val upstreamDnsPayload = ByteArray(respPacket.length)
                                            System.arraycopy(respPacket.data, 0, upstreamDnsPayload, 0, respPacket.length)

                                            val responseIpPacket = dnsFilter.wrapIpUdp(
                                                srcIp = parsed.destIp,
                                                dstIp = parsed.sourceIp,
                                                srcPort = parsed.destPort,
                                                dstPort = parsed.sourcePort,
                                                dnsPayload = upstreamDnsPayload,
                                                ipVersion = parsed.ipVersion
                                            )
                                            synchronized(outputStream) {
                                                outputStream.write(responseIpPacket)
                                            }
                                        } catch (_: Exception) {
                                            // Upstream timeout or network drop; the
                                            // requesting app's own resolver retry
                                            // logic takes over from here.
                                        } finally {
                                            runCatching { querySocket?.close() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    healthMonitor.recordServiceCrash("TUN loop exception: ${e.message}")
                }
            } finally {
                runCatching { inputStream.close() }
                runCatching { outputStream.close() }
            }
        }
    }

    private fun stopVpnInternal() {
        isRunning.set(false)
        _isRunningFlow.value = false
        healthMonitor.stop()
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    override fun onDestroy() {
        stopVpnInternal()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_vpn_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_vpn_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_service_title))
            .setContentText(getString(R.string.vpn_service_desc))
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
