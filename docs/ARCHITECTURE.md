# ShieldX: Architecture & Subsystems

## 1. System Overview

ShieldX is a system-level Android adult content blocker designed for both standard consumer devices (**Normal Protection Mode**) and enterprise managed devices (**Locked Managed Device Mode / Device Owner**).

```
                     +---------------------------------------+
                     |         Android User Traffic          |
                     +---------------------------------------+
                                         |
                                         v
                     +---------------------------------------+
                     |       ShieldX VpnService (TUN)        |
                     +---------------------------------------+
                                         |
                       +-----------------+-----------------+
                       |                                   |
                       v                                   v
             [ DNS Port 53 Queries ]             [ TCP/UDP Non-DNS Traffic ]
                       |                                   |
                       v                                   v
             +--------------------+              +--------------------+
             |     DnsFilter      |              |   Forwarded /      |
             +--------------------+              |   Protected Socket |
                       |                         +--------------------+
         +-------------+-------------+
         |                           |
         v                           v
  [ SafeSearch Domain ]       [ General Domain ]
         |                           |
         v                           v
  +------------------+       +------------------+
  | SafeSearch VIP   |       |  DomainMatcher   |
  | Rewriter (Google,|       |  (Trie / Suffix /|
  | Bing, YouTube)   |       |   TLD Rules)     |
  +------------------+       +------------------+
         |                           |
         |                   +-------+-------+
         |                   |               |
         |                   v               v
         |              [ Blocked ]     [ Allowed ]
         |                   |               |
         v                   v               v
  Synthetic Response  Sinkhole 0.0.0.0  Upstream Family DNS
```

---

## 2. Core Architectural Components

### 2.1 Deterministic Enforcement Authority
- **Authority Rule**: Deterministic offline policy enforcement is the sole security boundary.
- **Offline Reliability**: Works without internet, when AI is unavailable, or during network transitions.

### 2.2 Domain Intelligence Engine (`DomainMatcher`)
- Multi-tier in-memory index:
  1. Exact Allowlist Overrides
  2. Exact Blocklist
  3. Reverse Label Suffix Trie for subdomain trees (`*.sub.domain.com`)
  4. Adult TLD Matcher (`.xxx`, `.porn`, `.adult`, `.cam`, `.tube`, etc.)
- IDN Punycode decoding and URL normalization.

### 2.3 Layered Content Classification Pipeline
1. **Layer 1: Domain Rule Evaluation** -> Sub-millisecond lookup in `DomainMatcher`.
2. **Layer 2: Local URL & Path Heuristics** -> `UrlClassifier` tests paths, queries, extensions.
3. **Layer 3: Local Text & Obfuscation Normalizer** -> `TextNormalizer` strips Unicode homoglyphs, leetspeak, punctuation interleaving, and zero-width characters.
4. **Layer 4: Local Content Aggregator** -> Computes aggregate confidence score.
5. **Layer 5: Optional AI Classifier** -> For ambiguous content (`0.35 <= confidence <= 0.75`), queries Gemini 3.7 Flash using strict structured output JSON.

### 2.4 Device Owner & Enterprise Management
- Configures Always-On VPN with lockdown fail-closed mode (`setAlwaysOnVpnPackage(..., lockdownEnabled=true)`).
- Applies `DISALLOW_CONFIG_VPN` to prevent VPN tampering.
- Sets `setUninstallBlocked` to prevent removal.
- Disallows Safe Boot (`DISALLOW_SAFE_BOOT`) to prevent safe mode evasion.
- Suspends prohibited packages (`setPackagesSuspended`).
