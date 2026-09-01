# ShieldX: Classification Engine & Heuristics

## 1. Pipeline Overview

Content evaluation strictly follows a 5-tier evaluation ladder:

```
[ INPUT CONTENT / URL / DOMAIN ]
               │
               ▼
[ Tier 1: Domain Rule Engine ] ──(Match)──► [ BLOCK / ALLOW ]
               │ (No match)
               ▼
[ Tier 2: URL & Path Heuristics ] ──(Explicit)──► [ BLOCK ]
               │ (Clean)
               ▼
[ Tier 3: Local Text & Obfuscation ] ──(Explicit)──► [ BLOCK ]
               │ (Clean)
               ▼
[ Tier 4: Local Content Aggregator ]
               ├── Score >= 0.75 ──────────► [ BLOCK ]
               ├── Score < 0.35 ───────────► [ ALLOW ]
               └── 0.35 <= Score <= 0.75 ──► [ UNCERTAIN ]
                                                    │
                                                    ▼
                                    [ Tier 5: Optional AI (Gemini) ]
                                                    ├── BLOCK (Confidence >= Threshold) ─► [ BLOCK ]
                                                    ├── ALLOW ───────────────────────────► [ ALLOW ]
                                                    └── Offline / Error / Quota ─────────► [ LOCAL FALLBACK ]
```

---

## 2. Text Normalization (`TextNormalizer`)
To defeat common evasion techniques, `TextNormalizer` applies:
1. **URL Percent-Decoding**: `%70%6f%72%6e` -> `porn`.
2. **Zero-Width Character Removal**: Strips `\u200B`, `\u200C`, `\u200D`, `\uFEFF`.
3. **Unicode NFKC Decomposition**: Maps lookalike Unicode code points to standard forms.
4. **Homoglyph & Leetspeak Translation**: Cyrillic `р` -> `p`, `о` -> `o`, numbers `0` -> `o`, `3` -> `e`, `@` -> `a`.
5. **Repetition Collapsing**: `poooorrrrn` -> `porn`, `xxxxxx` -> `x`.
6. **Punctuation Stripping**: `p.o_r-n` -> `porn`.
