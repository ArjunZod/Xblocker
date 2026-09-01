# Parked: React Native UI pilot

Work stopped mid-setup when the priority moved to making the blocker app
standalone and deepening its filtering.

What exists: `package.json` + `node_modules` (React Native 0.76.5) at the repo
root, and `MusicNativeModule.kt` here — the Kotlin side of the bridge that
exposes the music engine (transport, search, explore, playback-state events)
to JS.

What was NOT done: the RN Gradle plugin wiring, the ReactActivity host, and any
JS/TSX screens. The module is parked outside the source set because it imports
`com.facebook.react`, which is not on the Gradle classpath, so leaving it in
`src/main` would break the :music build.
