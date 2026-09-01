# ShieldX: Security Bypass Test Matrix

| # | Bypass Attack Vector | Mechanism / Payload | ShieldX Defense Implementation | Result |
|---|---|---|---|---|
| 1 | **Incognito / Private Browsing** | Chrome / Firefox incognito window | VpnService captures all network sockets at the OS kernel level, bypassing browser-specific incognito isolation | **BLOCKED** |
| 2 | **Cyrillic Homoglyphs** | `\u0440orn` (`рorn` using Cyrillic er) | `TextNormalizer` homoglyph map maps Cyrillic `р` to Latin `p` prior to rule evaluation | **BLOCKED** |
| 3 | **Leetspeak Obfuscation** | `p0rn`, `h3nt4i`, `pr0n` | `TextNormalizer` converts numeric leetspeak substitutions to ASCII base characters | **BLOCKED** |
| 4 | **Punctuation Interleaving** | `p.o_r-n` | `TextNormalizer.stripPunctuationAndSpacing` collapses non-alphanumerics into compact string | **BLOCKED** |
| 5 | **Repeated Character Evasion** | `poooorrrrn`, `xxxxxx` | `TextNormalizer` collapses 3+ repeated characters into single instances | **BLOCKED** |
| 6 | **URL Percent-Encoding** | `%70%6f%72%6e` | `TextNormalizer` applies UTF-8 URL decoding prior to regex/dictionary scans | **BLOCKED** |
| 7 | **Nested Subdomains** | `cdn.video.stream.pornhub.com` | `DomainMatcher` Suffix Trie searches reversed label hierarchy and matches root domain | **BLOCKED** |
| 8 | **Mixed Case Variations** | `PoRnHuB.CoM` | Hostnames and URLs are normalized to lowercase prior to trie insertion and query matching | **BLOCKED** |
| 9 | **Adult TLDs** | `site.xxx`, `live.cam`, `unrated.tube` | `DomainMatcher` inspects top-level domain suffix and blocks designated adult TLDs | **BLOCKED** |
| 10 | **Port & Path Injection** | `pornhub.com:8443/video` | `normalizeDomain` strips port, path, and queries before hostname lookup | **BLOCKED** |
| 11 | **SafeSearch Bypass on Google/Bing** | Disabling SafeSearch in browser cookies | `SafeSearchEnforcer` overrides DNS resolution of Google/Bing/YouTube to force-safesearch VIP IPs | **BLOCKED** |
| 12 | **VPN Disabling (Managed Mode)** | User attempts to disconnect VPN in settings | `DISALLOW_CONFIG_VPN` disables VPN settings; Always-On VPN lockdown denies internet | **BLOCKED** |
| 13 | **Safe Mode Evasion** | User boots into Android Safe Mode | `DISALLOW_SAFE_BOOT` restriction prevents booting into safe mode on Device Owner installs | **BLOCKED** |
| 14 | **Anonymizing Bypass Apps** | Installing Tor Browser / Orbot | `BrowserRegistry` flags anonymizing apps; in Managed Mode, `RestrictionController` suspends them | **BLOCKED** |
| 15 | **AI Service Blackout** | Gemini API offline / 429 quota error | `ClassificationManager` defaults strictly to deterministic offline rule engine | **BLOCKED (Fail-Safe)** |
