# Xblocker

An advanced Android content and domain blocker with VPN-based network interception, DNS filtering, device owner lockdown, and content classification.

```text
Xblocker
├── TaRZI-App        the core blocker: protection/content-blocking, VPN service, UI, settings
├── contracts        interfaces and data contracts (pure Kotlin)
└── ui-kit           the design system: theme, typography, tokens, component primitives
```

## Features

- **VPN-Based Network Interception**: Local VPN sinkhole and packet parser inspecting DNS requests and network traffic.
- **DNS Domain Filtering**: Real-time filtering against known malicious/adult domain lists with subdomain matching.
- **Encrypted DNS Defence**: Detects and blocks bypass attempts via DoH / DoT (e.g. Google, Cloudflare encrypted DNS).
- **Private DNS Guard**: Enforces opportunistic/filterable DNS mode via Device Owner policies.
- **Content Classification**: Local heuristics and AI-assisted classification (Gemini).
- **Device Owner Lockdown**: Prevents unauthorized disablement, package tampering, and evasion.
- **Resilient Room Database**: Local persistence for rules, audit events, blocked logs, and tamper events.

## Build

```bash
./gradlew :app:assembleDebug          # build the APK
./gradlew :app:installDebug           # install to a connected device
```

The debug build installs as `com.antigravity.shieldx.debug`.

## Test

```bash
./gradlew :app:testDebugUnitTest      # protection, VPN, classifier, device unit tests
```

## Structure

| Looking for | Path |
|---|---|
| Blocker UI, Navigation & Settings | `TaRZI-App/src/main/java/com/antigravity/shieldx/ui/` |
| VPN, DNS Filter & Interceptor | `TaRZI-App/src/main/java/com/antigravity/shieldx/vpn/` |
| Classification & Policy Engine | `TaRZI-App/src/main/java/com/antigravity/shieldx/{classifier,policy}/` |
| Device Admin & Tamper Detection | `TaRZI-App/src/main/java/com/antigravity/shieldx/{device,tamper}/` |
| Local Room Database & DAOs | `TaRZI-App/src/main/java/com/antigravity/shieldx/data/` |
| UI Kit & Theme Tokens | `ui-kit/src/main/java/com/antigravity/shieldx/ui/` |
| Core Contracts & Shared Models | `contracts/src/main/kotlin/com/antigravity/shieldx/core/` |
