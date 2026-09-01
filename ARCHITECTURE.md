# TaRZI Architecture

## Structure

One Gradle build, five modules:

| Gradle module | Directory | Type |
|---|---|---|
| `:app` | `TaRZI-App/` | Android application |
| `:music` | `TaRZI-Music/` | Android library |
| `:ai-agent` | `TaRZI-AI-Agent/` | Android library |
| `:contracts` | `contracts/` | Pure Kotlin (JVM) library |
| `:ui-kit` | `ui-kit/` | Android library (Compose) |

Multi-module rather than three separate Gradle builds: the product ships one
APK, and module boundaries already give compile-time enforcement of the
dependency rules. Separate root builds would have required artifact publishing
or composite builds for no additional isolation.

## Dependency direction

```text
                    :app
                   /  |  \
                  /   |   \
                 v    v    v
          :music  :ui-kit  :ai-agent
              \      |      /
               v     v     v
                :contracts
```

`:app` → `:music`, `:ai-agent`, `:contracts`, `:ui-kit`
`:music` → `:contracts`, `:ui-kit`
`:ai-agent` → `:contracts`, `:ui-kit`
`:contracts` → nothing (coroutines only)
`:ui-kit` → nothing (Compose only)

There are no cycles. `:music` and `:ai-agent` cannot see `:app` or each other,
which the compiler enforces — a reverse import fails the build rather than
relying on discipline.

## Module responsibilities

**`:app`** — the Android application and composition root. Owns
`MainActivity` (single Activity, single `NavHost`), the app shell and bottom
navigation, settings/diagnostics/admin screens, and the entire protection
stack: VPN service, DNS filtering, content classification, policy engine,
blocklists, tamper monitoring, device admin. Also owns the Room database and
its repositories, and provides the contract implementations the other two
modules consume.

**`:music`** — the complete music platform. Multi-source search (YouTube
Music, Spotify, Deezer) with normalization and ranking, stream resolution
(InnerTube, NewPipe with PoToken, Lavalink with node failover), Media3/ExoPlayer
playback in a foreground `MediaSessionService`, queue/shuffle/repeat, library and
play history, lyrics — and its own Compose screens.

**`:ai-agent`** — the complete assistant. Wake word and speech recognition,
intent parsing, reasoning over DeepSeek/Gemini backends with an offline intent
fallback, a tool registry with risk-gated deterministic execution, persistent
memory, trigger-based automations, and the system-level services (accessibility,
overlay orb, screen vision) — and its own chat and voice-mode screens.

**`:contracts`** — the interfaces and DTOs the three projects speak through.
No Android UI, no implementations.

**`:ui-kit`** — the design system: color, typography, spacing/radius/motion
tokens, and component primitives. Pure Compose, no domain knowledge.

## Composition and lifecycle

`ShieldXApp` (Application) creates `SecurityManager`, the app-wide composition
root. `SecurityManager` builds three scoped graphs and wires them together:

```text
SecurityManager
├── ProtectionGraph(context, database)
│     repositories, classifiers, policy engine, device/tamper controllers,
│     ProtectionControllerImpl
├── MusicGraph(context, policyGate, playHistoryDao)
│     search engine, resolvers, Lavalink, library, lyrics, MusicControllerImpl
└── AgentGraph(context, configStore, auditSink, protectionInsights,
               daos, eventBus, protectionController, musicController)
      command registry, planner, executor, memory, automations, tool handlers
```

Each graph receives only interfaces from the others — `MusicGraph` gets a
`ContentPolicyGate`, never `PolicyEngine`; `AgentGraph` gets
`MusicController`/`ProtectionController`, never `MusicGraph`/`ProtectionGraph`.

`SecurityManager` also exposes every subsystem under its original property
names, so screens and services read from one place, and registers controllers
plus `AgentGraph` in `ServiceRegistry` for Android Services, which the framework
instantiates with a no-arg constructor and therefore cannot be
constructor-injected.

## Data flow

### Voice command → playback → UI

```text
User speaks
  └─> TarziVoiceService (wake word, STT)          [:ai-agent]
        └─> TarziBrain.handle(utterance)          [:ai-agent]
              ├─ AI backend (DeepSeek/Gemini) returns a tool call
              └─ or offline IntentEngine matches locally
                    └─> DeterministicExecutor (risk gate, confirmation, audit)
                          └─> ToolHandlers "PLAY"
                                └─> MusicController.searchAndPlay(query)   [contract]
                                      └─> MusicControllerImpl              [:music]
                                            ├─ TaRziMusicSearchEngine (3 adapters)
                                            ├─ ContentPolicyGate check (explicit tracks)
                                            ├─ MusicIdentifierResolver → Lavalink/NewPipe
                                            └─> MusicPlaybackService → ExoPlayer
                                                  └─> playbackStateFlow: StateFlow<PlaybackState>
                                                        └─> MainActivity collects  [:app]
                                                              └─> MiniPlayerBar / NowPlayingScreen
```

The agent never touches the player, and the music engine never touches the UI
tree. State flows one way: engine → `StateFlow<PlaybackState>` → App → Compose.

`"Pause"` and `"Resume"` follow the identical path through
`MusicController.pause()` / `.resume()`.

### Assistant turn ducking

`VoiceAssistantManager` (in-app voice) and `TarziVoiceService` (always-on) both
duck audio through `MusicController.duck(true/false)` — the former by
constructor injection, the latter via `ServiceRegistry` because it is a Service.

### Protection enforcement

```text
DNS query / package event
  └─> ProtectionVpnService / PackageChangeReceiver     [:app]
        └─> ClassificationManager (rule → URL → local → AI)
              └─> PolicyEngine.evaluateDomain()
                    ├─> allow
                    └─> block → BlockInterceptActivity + AuditRepository
```

Wholly inside `:app`. The agent can read protection state through
`ProtectionInsights` and change it through `ProtectionController`, but never
reaches into the enforcement path.

### UI composition

`MainActivity` hosts the single `NavHost` and places the mini player above the
bottom bar, collecting `musicController.playbackStateFlow` once at the top so
every screen shows consistent playback state. Feature screens come from their
owning module: `MusicHomeScreen`/`NowPlayingScreen` from `:music`,
`AssistantChatScreen`/`VoiceModeScreen` from `:ai-agent`, the rest from `:app`.
All of them draw from `:ui-kit`, which is why the app reads as one product.

## Persistence

One Room database (`tarzi_unified.db`, `AppDatabase` in `:app`), with schema
contributed by each project:

| Tables | Defined in |
|---|---|
| policies, domain rules, app rules, classification cache, blocked/tamper events, device state, model decisions, policy versions, configuration, audit events, user preferences | `:app` |
| play_history | `:music` |
| memory_items, automations, network_profiles | `:ai-agent` |

Each project defines its own entities and DAOs; `:app` hosts the single
`@Database` that registers them (an allowed App → Music / App → AI-Agent edge).
This keeps one file, one version, and no migration.

## Build system

Gradle 8.9, AGP 8.6.0, Kotlin 2.0.20, KSP, compileSdk 35, minSdk 26, JVM target
21. Dependencies are declared in `gradle/libs.versions.toml` and each module
declares only what it uses — `:music` carries Media3/NewPipe, `:ai-agent`
carries its AI/network dependencies, and neither pulls in the other's.
