# ShieldX — Android System-Level Adult Content Protection

ShieldX is a production-grade Android security application designed to aggressively prevent access to pornography, sexually explicit websites, adult search results, adult streaming, cam services, NSFW media, and explicit applications across the entire device.

---

## Key Highlights

- **Deterministic Security Authority**: 100% offline enforcement via a high-performance local VPN DNS/packet filtering engine and Device Owner policy controls.
- **Two Deployment Modes**:
  - **Normal Consumer Mode**: Local DNS filtering, SafeSearch, and local classification.
  - **Locked Managed Device Mode (Device Owner / Android Enterprise)**: Authoritative DevicePolicyManager controls, Always-on VPN with fail-closed lockdown, uninstall prevention, VPN settings lockdown (`DISALLOW_CONFIG_VPN`), and app suspension.
- **SafeSearch DNS VIP Enforcement**: Hardware/network-level SafeSearch VIP rewriting for Google, YouTube Restricted Mode, Bing, and DuckDuckGo.
- **Advanced Obfuscation Defenses**: `TextNormalizer` strips Cyrillic homoglyphs, numeric leetspeak, repeated character padding, zero-width characters, and percent-encoded queries.
- **Optional Gemini AI Layer**: Integrates Gemini 3.7 Flash strictly for uncertain content scoring with JSON schema validation, SHA-256 caching, rate limiting, and circuit breakers.
- **Zero Privacy Leakage**: No user browsing history uploaded, zero explicit previews, and strict credential/URL query redaction.

---

## Project Structure

```
w:\x blocker\
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── res/ (device_admin.xml, network_security_config.xml, raw blocklists)
│   │   │   └── java/com/antigravity/shieldx/
│   │   │       ├── ShieldXApp.kt
│   │   │       ├── core/ (model, security, logging, util)
│   │   │       ├── data/ (Room database, entities, DAOs, repositories)
│   │   │       ├── vpn/ (ProtectionVpnService, VpnPacketParser, DnsFilter, DomainMatcher, VpnHealthMonitor)
│   │   │       ├── device/ (ShieldXDeviceAdminReceiver, DeviceOwnerController, RestrictionController, AdminRecoveryController)
│   │   │       ├── apps/ (BrowserRegistry, AppScanner, AppPolicyEngine, PackageChangeReceiver)
│   │   │       ├── classifier/ (RuleClassifier, UrlClassifier, LocalContentClassifier, GeminiClassifier)
│   │   │       ├── tamper/ (TamperMonitor, IntegrityMonitor, SecurityStateMachine)
│   │   │       ├── policy/ (PolicyEngine, SafeSearchEnforcer, BlocklistManager)
│   │   │       └── ui/ (theme, navigation, dashboard, policies, blocked, diagnostics, admin)
│   │   └── test/java/com/antigravity/shieldx/
│   │       ├── core/ (TextNormalizerTest)
│   │       ├── vpn/ (DomainMatcherTest, DnsFilterTest)
│   │       ├── classifier/ (ClassifierTests)
│   │       └── fault/ (FaultInjectionAndBypassTest)
└── docs/
    ├── ARCHITECTURE.md
    ├── SECURITY_MODEL.md
    ├── DEVICE_OWNER_SETUP.md
    ├── VPN_ARCHITECTURE.md
    ├── CLASSIFICATION_ENGINE.md
    ├── AI_INTEGRATION.md
    ├── PRIVACY.md
    ├── TEST_PLAN.md
    ├── BYPASS_TEST_MATRIX.md
    ├── RELEASE_CHECKLIST.md
    └── TROUBLESHOOTING.md
```

---

## Building and Running

### Build Debug APK:
```bash
./gradlew assembleDebug
```

### Run Unit Tests:
```bash
./gradlew testDebugUnitTest
```

### Provision Device Owner Mode via ADB:
```bash
adb shell dpm set-device-owner com.antigravity.shieldx/.device.ShieldXDeviceAdminReceiver
```
