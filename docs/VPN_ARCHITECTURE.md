# ShieldX: VPN Architecture & DNS Interception Engine

## 1. TUN Interface Configuration
ShieldX initializes a virtual network interface using Android's `VpnService.Builder`:
- **IPv4 Address**: `10.254.1.2/24`
- **IPv6 Address**: `fd00::2/64`
- **Virtual DNS Server**: `10.254.1.1` / `fd00::1`
- **MTU**: `1500`
- **Blocking Mode**: Enabled (`setBlocking(true)`)

---

## 2. Packet Flow & Binary Inspection
1. **Packet Read**: Raw IP packets are read from `FileInputStream(pfd.fileDescriptor)` into a pre-allocated 32KB buffer.
2. **Packet Demuxing (`VpnPacketParser`)**:
   - Inspects IP header (IPv4 vs IPv6).
   - Identifies transport protocol: UDP (17) vs TCP (6).
   - Identifies destination port.
3. **DNS UDP Port 53 Handling**:
   - `DnsFilter.parseQuery` extracts transaction ID, flags, QNAME, and QTYPE (A / AAAA).
   - Query is evaluated against `DomainMatcher`, `SafeSearchEnforcer`, and `PolicyEngine`.
   - **On Block**: Synthetic DNS answer containing `0.0.0.0` (IPv4) or `::` (IPv6) is synthesized and written back to TUN.
   - **On SafeSearch Rewrite**: Synthetic DNS answer containing forced SafeSearch VIP (`216.239.38.120`, etc.) is returned.
   - **On Allow**: Query is forwarded via `protect(socket)` to upstream family DNS (`1.1.1.3` / `185.228.168.168`), and response is piped back.

---

## 3. Fail-Closed & Watchdog Resilience
- **Watchdog (`VpnHealthMonitor`)**:
  - Periodically checks packet activity and heartbeat.
  - Monitors crash counters with exponential backoff (1s -> 2s -> 4s -> ... -> 60s max) to prevent battery-draining spin loops.
  - Trips `TamperState.LOCKDOWN` upon fatal crash in Managed Mode, ensuring Android OS keeps networking closed.
