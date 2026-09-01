# ShieldX: Test Plan & Automated Verification

## 1. Test Suite Architecture

### 1.1 Unit Tests
- **`TextNormalizerTest`**: Validates homoglyphs, NFKC, leetspeak, repeated chars, zero-width spaces, URL percent-encoding.
- **`DomainMatcherTest`**: Tests exact domains, subdomain trie trees, adult TLDs, allowlist overrides, and IDN punycode.
- **`DnsFilterTest`**: Tests DNS packet decoding, synthetic A/AAAA sinkholes, and SafeSearch VIP synthesis.
- **`ClassifierTests`**: Tests `RuleClassifier`, `UrlClassifier`, `LocalContentClassifier`, and `AIResponseValidator`.
- **`FaultInjectionAndBypassTest`**: Validates system resilience against malformed AI responses, timeouts, and multi-vector evasion attempts.

---

## 2. Execution Instructions
Run all unit tests via Gradle:
```bash
./gradlew testDebugUnitTest
```

Run debug build assembly:
```bash
./gradlew assembleDebug
```
