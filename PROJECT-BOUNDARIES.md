# Project Boundaries

Every source file has exactly one owning project. This table is the reference
for where new code goes.

## Ownership

| Area | Owner |
|---|---|
| Application class, startup, composition root | TaRZI-App |
| Android UI shell, navigation, bottom bar | TaRZI-App |
| Settings, diagnostics, admin/device-owner screens | TaRZI-App |
| Protection UI and enforcement (VPN, DNS filtering, blocklists) | TaRZI-App |
| Content classification (rule, URL, local, AI) | TaRZI-App |
| Policy engine, safe search, tamper/integrity monitoring | TaRZI-App |
| Device admin, lockdown, restrictions | TaRZI-App |
| App package policy and package-change receiver | TaRZI-App |
| The Room database and its repositories | TaRZI-App |
| Permissions and manifest app-level config | TaRZI-App |
| Music search and discovery | TaRZI-Music |
| YouTube Music / Spotify / Deezer adapters, normalization, ranking | TaRZI-Music |
| Stream resolution (InnerTube, NewPipe, PoToken), Lavalink + node failover | TaRZI-Music |
| Media3 / ExoPlayer playback, MediaSession, playback service | TaRZI-Music |
| Queue, shuffle, repeat | TaRZI-Music |
| Library, liked songs, play history | TaRZI-Music |
| Lyrics | TaRZI-Music |
| Music screens (search/home, now playing, mini player) | TaRZI-Music |
| Voice input, speech-to-text, wake word | TaRZI-AI-Agent |
| Intent detection and command parsing | TaRZI-AI-Agent |
| Agent reasoning, AI backends (DeepSeek, Gemini), context | TaRZI-AI-Agent |
| Tool registry, tool routing, execution + risk gating | TaRZI-AI-Agent |
| Agent memory and automations | TaRZI-AI-Agent |
| Accessibility service, overlay orb, screen vision | TaRZI-AI-Agent |
| Assistant chat and voice-mode screens | TaRZI-AI-Agent |
| Music commands issued by the assistant | AI-Agent → Music, via `MusicController` |
| Protection commands issued by the assistant | AI-Agent → App, via `ProtectionController` |
| Theme, typography, spacing tokens, component primitives | ui-kit (shared) |
| Cross-project interfaces and DTOs | contracts (shared) |

## Deliberate exceptions

Two things sit where a strict reading of the table wouldn't put them, for
reasons worth knowing before "fixing" them:

**`TarziControlScreen` stays in TaRZI-App.** It toggles the agent's
accessibility/voice/overlay services, but it also wires device-admin recovery
and lockdown, which are App-owned. App is allowed to depend on AI-Agent, so it
lives on the App side rather than dragging protection controllers into the
agent.

**The physical Room database stays in TaRZI-App.** Music owns
`PlayHistoryEntity`/`Dao` and the agent owns `MemoryItemEntity`,
`AutomationEntity`, `NetworkProfileEntity` with their DAOs — those live in
their own projects. But `AppDatabase` (in App) is the single `@Database` that
registers them and owns the `.db` file, because App → Music and App → AI-Agent
are allowed edges and one database file avoids a risky migration of live user
data. Each project defines its own schema; App just hosts the file.

## Shared contracts

Everything in `contracts/src/main/kotlin/com/antigravity/shieldx/core/`:

| Contract | Implemented by | Consumed by | Purpose |
|---|---|---|---|
| `MusicController` | Music (`MusicControllerImpl`) | App, AI-Agent | Play/pause/queue/search/duck |
| `ProtectionController` | App (`ProtectionControllerImpl`) | AI-Agent | Protection profile and status |
| `TaRZIController` | both controllers | — | Shared `execute(ToolRequest)` base |
| `ContentPolicyGate` | App (`PolicyEngine`) | Music | Should explicit tracks be blocked |
| `ConfigStore` | App (`ConfigRepository`) | AI-Agent | API keys, settings |
| `AuditSink` | App (`AuditRepository`) | AI-Agent | Append tool-execution audit records |
| `ProtectionInsights` | App (`AuditRepository`) | AI-Agent | Block counts / recent blocked events |
| `EventBus` + `TaRZIEvent` | contracts | all | Decoupled cross-subsystem events |
| `ServiceRegistry` | contracts | all | Locator for Android Services, which can't be constructor-injected |

Shared DTOs in `core.model`: `Track`, `PlaybackState`, `PlaybackCommand`,
`MusicSource`, `RepeatMode`, `ToolRequest`, `ToolResult`, `RiskLevel`,
`Capability`, `ConfirmationPolicy`, `ProtectionProfile`, `BlockedEventSummary`.

App-only models stay in App's `core.model`: `Category`, `PolicyDecision`,
`BlockReason`, `DeviceMode`, `TamperState`, `AppPolicy`, `MatchType`.

## Rules

1. `:music` and `:ai-agent` must never import from `:app`. If they need
   something App has, add an interface to `:contracts`.
2. `:music` and `:ai-agent` must never import each other. They talk through
   `MusicController`.
3. `:contracts` imports nothing from any project. `:ui-kit` imports no domain
   code.
4. The music engine and the agent never touch `NavController`, `Activity`, or
   another project's Compose state. Each exposes state (`StateFlow`) and
   screens; App decides where they appear.
5. One implementation per responsibility — no copies across projects.

These are checkable with grep, and that sweep is part of the definition of done
for any change that crosses a boundary:

```bash
grep -rn "import com.antigravity.shieldx.\(core.security\|data.local\|data.repository\|policy\|vpn\|tamper\|device\|classifier\|apps\|protection\)\." TaRZI-Music/src TaRZI-AI-Agent/src --include=*.kt
grep -rn "import com.antigravity.shieldx.assistant" TaRZI-Music/src --include=*.kt
grep -rn "import com.antigravity.shieldx.music" TaRZI-AI-Agent/src --include=*.kt
```

All three must return nothing.
