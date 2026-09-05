# Xblocker Architecture

## Structure

One Gradle build, three modules:

| Gradle module | Directory | Type |
|---|---|---|
| `:app` | `TaRZI-App/` | Android application (Blocker Core) |
| `:contracts` | `contracts/` | Pure Kotlin (JVM) library |
| `:ui-kit` | `ui-kit/` | Android library (Compose) |

## Dependency direction

```text
            :app
           /    \
          v      v
      :ui-kit   :contracts
```

- `:app` depends on `:contracts` and `:ui-kit`.
- `:contracts` depends on nothing (coroutines only).
- `:ui-kit` depends on Compose only.

## Blocker Subsystems (TaRZI-App)

1. **VPN Service & DNS Filter (`vpn/`)**:
   - Local Android `VpnService` sinkhole loop capturing IPv4 UDP/TCP DNS packets (port 53).
   - Domain matching engine with wildcard and suffix matching against adult/malicious domain datasets.
   - Encrypted DNS Defense: detects and blocks DoH/DoT resolvers (8.8.8.8, 1.1.1.1, Cloudflare DoH, etc.).
   - Private DNS Guard: device-owner enforcement keeping DNS opportunistic and filterable.

2. **Policy & Content Classification (`policy/`, `classifier/`)**:
   - High-throughput rule cache, keyword matching, and local AI (Gemini) classification.

3. **Device Owner & Tamper Monitoring (`device/`, `tamper/`)**:
   - Device admin lockdown preventing uninstall, safe mode evasion, or VPN bypass.

4. **Persistence (`data/`)**:
   - Room database (`AppDatabase`) storing rules, blocked events, policies, audit logs, and settings.
