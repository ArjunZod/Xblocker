# TaRZI

An Android super-app combining three subsystems that were previously one
tangled module:

```text
TaRZI
├── TaRZI-App        the shell: protection/content-blocking core, UI, navigation, settings
├── TaRZI-Music      the music platform: search, resolution, playback, queue, library, lyrics
└── TaRZI-AI-Agent   the assistant: voice, intent, reasoning, tool routing, automations
```

Plus two small shared modules:

```text
├── contracts        interfaces + DTOs the three projects talk through (pure Kotlin, no Android UI)
└── ui-kit           the design system: theme, typography, tokens, component primitives
```

## Build

There is no `gradlew` wrapper script in this repo. Use the cached Gradle 8.9
distribution with Android Studio's bundled JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
GRADLE="/c/Users/gamin/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle"

"$GRADLE" :app:assembleDebug          # build the APK
"$GRADLE" :app:installDebug           # install to a connected device
```

`adb` is not on PATH by default:

```bash
export PATH="$PATH:/c/Users/gamin/AppData/Local/Android/Sdk/platform-tools"
```

The debug build installs as `com.antigravity.shieldx.debug`.

## Test

```bash
"$GRADLE" :app:testDebugUnitTest        # protection, VPN, classifier, device (7 tests)
"$GRADLE" :music:testDebugUnitTest      # queue, lyrics, live stream resolution (3 tests)
"$GRADLE" :ai-agent:testDebugUnitTest   # intent, command registry/risk, wake word (3 tests)
```

`StreamResolutionLiveTest` talks to YouTube on purpose — it is the only way to
verify stream resolution actually works. It skips itself when the network is
unavailable.

## Where things live

| Looking for | Go to |
|---|---|
| App startup, navigation, screens, settings | `TaRZI-App/src/main/java/com/antigravity/shieldx/ui/` |
| Content blocking, VPN, classifier, policy, device admin | `TaRZI-App/src/main/java/com/antigravity/shieldx/{vpn,classifier,policy,device,tamper,apps,protection}/` |
| The Room database, repositories | `TaRZI-App/src/main/java/com/antigravity/shieldx/data/` |
| Music search / playback engine | `TaRZI-Music/src/main/java/com/antigravity/shieldx/music/` |
| Music screens (search, now playing, mini player) | `TaRZI-Music/src/main/java/com/antigravity/shieldx/ui/music/` |
| Assistant reasoning, tools, voice, automations | `TaRZI-AI-Agent/src/main/java/com/antigravity/shieldx/assistant/` |
| Assistant chat/voice screens | `TaRZI-AI-Agent/src/main/java/com/antigravity/shieldx/ui/assistant/` |
| Cross-project interfaces and shared models | `contracts/src/main/kotlin/com/antigravity/shieldx/core/` |
| Colors, type, spacing, shared components | `ui-kit/src/main/java/com/antigravity/shieldx/ui/` |

## Dependency rules

```text
        :app
       /    \
      v      v
  :music   :ai-agent
      \      /
       v    v
     :contracts
```

- `:app` may depend on `:music` and `:ai-agent`.
- `:music` and `:ai-agent` may **not** depend on `:app` or on each other.
- Anything the agent or music engine needs from the app is expressed as an
  interface in `:contracts`, which `:app` implements and injects at startup.
- `:contracts` depends on nothing but coroutines. `:ui-kit` depends on nothing
  but Compose.

See [PROJECT-BOUNDARIES.md](PROJECT-BOUNDARIES.md) for who owns what and
[ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces talk at runtime.

## Development workflow

1. Decide which project owns the change (see PROJECT-BOUNDARIES.md).
2. If it needs something from another project, add or reuse an interface in
   `:contracts` — don't reach across a module boundary.
3. Build and test that module, then `:app:assembleDebug`.
4. For anything touching playback, voice, or protection enforcement, verify on
   a real device — unit tests don't cover the service/ExoPlayer/VPN paths.

## Note on naming

The Kotlin package root and `applicationId` are still `com.antigravity.shieldx`
from the app's earlier "ShieldX" identity. This was left deliberately: changing
the `applicationId` would present as a different app to Android and lose the
installed app's local data. The module boundaries carry the ownership, not the
package names.
