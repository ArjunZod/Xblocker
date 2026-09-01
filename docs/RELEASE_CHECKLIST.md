# ShieldX: Release Readiness Checklist

- [x] **SDK Versions**: Target SDK 35 (Android 15), Compile SDK 35, Min SDK 26 (Android 8.0 Oreo).
- [x] **Foreground Service Compliance**: `ProtectionVpnService` declared with `specialUse` / `dataSync` FGS type and ongoing notification channel.
- [x] **Device Admin & Enterprise**: `DeviceAdminReceiver` declared with `BIND_DEVICE_ADMIN` and `@xml/device_admin`.
- [x] **ProGuard / R8 Hardening**: R8 shrinking enabled for release builds, preserving Room databases, DAOs, and AndroidX crypto.
- [x] **Network Security Configuration**: Strict cleartext traffic rejection (`cleartextTrafficPermitted="false"`).
- [x] **Zero Hardcoded Secrets**: No API keys, passwords, or credentials in source code. Gemini API key is configured dynamically by admin.
- [x] **Zero Explicit Media Previews**: Block interception screen and notifications contain zero explicit imagery.
- [x] **Unit & Integration Test Coverage**: All core unit tests, fault injection tests, and bypass test matrix pass.
