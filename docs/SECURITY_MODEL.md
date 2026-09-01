# ShieldX: Threat Model & Security Boundaries

## 1. Threat Actors & Scenarios

| Threat Actor | Capabilities | ShieldX Defense Strategy |
|---|---|---|
| **Curious User** | Accesses explicit sites, searches adult terms | Blocked at DNS layer, SafeSearch enforced at DNS/IP VIP level. |
| **Evasion Attempt (Bypass-Seeking User)** | Uses private browsing / incognito, obscure TLDs, Cyrillic homoglyphs (`рorn`), punctuation (`p.o.r.n`), URL percent-encoding (`%70%6f%72%6e`) | VPN filters all device traffic regardless of incognito; `TextNormalizer` strips Unicode homoglyphs and leetspeak; Suffix Trie catches nested subdomains. |
| **Alternative Browser Installation** | Downloads Tor Browser, unmonitored browsers, or private browsers | `PackageChangeReceiver` detects new packages; in Managed Mode, bypass browsers (Tor, anonymizers) are automatically suspended or blocked. |
| **VPN Interruption / Disabling** | Attempts to toggle off VPN in Android settings | In Managed Mode (Device Owner), `DISALLOW_CONFIG_VPN` prevents disabling; Always-On VPN with Lockdown denies all network traffic if VPN is stopped. |
| **Safe Mode Evasion** | Attempts to boot device into Safe Mode to disable 3rd party apps | In Device Owner mode, `DISALLOW_SAFE_BOOT` restriction prevents booting into safe mode. |
| **Tampering / Clock Skew** | Manipulates device system clock to expire policies or bypass time windows | `TamperMonitor` tracks monotonicity and trips `SUSPICIOUS` state on >5min time shifts. |
| **AI Failure / Network Blackout** | Gemini API is unreachable, quota exceeded, or device offline | System defaults to 100% offline deterministic rule engine. Never fails open. |

---

## 2. Explicit Non-Guarantees & Physical Boundaries
- Rooted devices / Custom ROMs / Bootloader unlocked devices: If an attacker has root privileges or physical JTAG access, kernel-level tampering cannot be prevented by any Android user-space application.
- Hardware compromise / Baseband exploits.
- Encrypted DNS (DoH/DoT) within closed apps: On standard consumer mode without Device Owner restrictions, applications utilizing hardcoded internal DoH IP endpoints can attempt bypass; in Device Owner mode, all outbound non-VPN ports and prohibited apps are restricted.
