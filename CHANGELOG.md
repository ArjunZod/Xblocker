# ShieldX Changelog

All notable changes to the ShieldX protection app are documented here.

---

## [1.1.0] — 2026-08-27

### 🛡️ Encrypted DNS Bypass Blocking (Critical Fix)
- **Added `EncryptedDnsBlocker`** — blocks DoH (DNS-over-HTTPS), DoT (DNS-over-TLS port 853), and DoQ (DNS-over-QUIC port 784) across all browsers
- Covers **70+ IPv4** and **20+ IPv6** resolver addresses (Cloudflare, Google, Quad9, OpenDNS, AdGuard, NextDNS, Mullvad, and more)
- Blocks **60+ DoH bootstrap hostnames** (dns.google, cloudflare-dns.com, etc.) at the DNS layer
- Deliberately preserves family-safe resolver endpoints (e.g., Cloudflare Family 1.1.1.3)
- Browser-agnostic: blocks by resolver address, not by app — any browser is covered

### 🌐 IPv6 Tunnel Routing
- VPN tunnel now routes IPv6 encrypted-DNS resolver addresses (previously only IPv4 was routed)
- Closes the bypass where DoH over native IPv6 sailed past the filter entirely

### 🔍 SafeSearch Expansion (4 → 11 engines)
- **Google** — forcesafesearch.google.com with full ccTLD coverage (100+ country domains)
- **YouTube** — restrict.youtube.com (Restricted Mode) with expanded host matching
- **Bing** — strict.bing.com
- **DuckDuckGo** — safe.duckduckgo.com
- **Yahoo** — safe.search.yahoo.com (including Yahoo Japan)
- **Yandex** — familysearch.yandex.ru (all regional domains: .ru, .com, .kz, .by, etc.)
- **Ecosia** — DNS-level redirect to SafeSearch VIP
- **Qwant** — DNS-level redirect to SafeSearch VIP
- **Brave Search** — DNS-level redirect to SafeSearch VIP
- **Startpage** — DNS-level redirect to SafeSearch VIP
- **Searx/SearXNG** — pattern-matched redirect for self-hosted meta-search instances

### 📱 App Policy Expansion (8 → 77 apps)

#### Browsers (8 → 55)
- Added: Firefox Beta/Nightly/Focus, Brave Beta/Nightly, Edge Beta/Canary/Dev
- Added: Opera Beta/Mini/GX, Samsung Internet Beta, Vivaldi/Vivaldi Snapshot
- Added: Kiwi, Via, Yandex, UC, Mi, Huawei, OPPO, Vivo, Realme browsers
- Added: Jelly (LineageOS), Lightning, Chromium, Ungoogled Chromium, Bromite
- Added: TV Bro, Privacy Browser, 2345 Browser, Aspect Browser
- Tor Browser remains **BLOCKED** (anonymizing bypass)

#### Social Media (NEW — 16 apps)
- Twitter/X, Instagram, Reddit, Snapchat, TikTok (both package variants)
- Tumblr, Discord, Telegram (official + X + web), Pinterest
- Kik Messenger, Whisper, Rocket.Chat, Slack
- Omegle → **BLOCKED** (anonymous video chat with explicit content)

#### Dating Apps (NEW — 6 apps)
- Tinder, Bumble, Grindr, Hinge, OkCupid, Badoo

### 🚫 Domain Blocklist Expansion (69 → 175+ domains)

#### New Categories Added
- **Adult Social Platforms**: OnlyFans clones (Fansly, LoyalFans, JustForFans, FanCentro, etc.)
- **NSFW Image Aggregators**: RedGIFs, Scrolller, Fapello, InfluencersGoneWild, LeakedBB
- **Hentai/Manga** (17 sites): nhentai variants, Hitomi, Hentai2Read, Fakku, Tsumino, etc.
- **Adult Imageboards**: Rule34 (all mirrors), Gelbooru, Danbooru, e621, Sankaku, Konachan
- **Erotic Literature**: Literotica, Nifty, ASSTR, SexStories
- **Web Proxies/VPN Bypass** (17 sites): CroxyProxy, FilterBypass, Hidester, Tor2Web, etc.
- **Shock/Gore Sites**: CrazyShit, TheYNC, Kaotic, eFukt, etc.
- **Imageboards**: 4chan, 8chan/8kun
- **Adult Torrent Trackers**: ThePirateBay, 1337x, RARBG, Nyaa/Sukebei
- **NSFW Twitter Mirrors**: xtwitter.com, fixupx.com, Nitter
- **NSFW Mastodon**: baraag.net, switter.at, nsfw.social
- **Adult Dating** (expanded): AshleyMadison variants, Seeking, WhatsYourPrice, SugarDaddyMeet
- **Adult Search Engines** (expanded): NudeVista, rexxx, FindTubes, ThePornDude

#### Adult TLD Blocking
- Domains ending in: `.xxx`, `.porn`, `.adult`, `.sex`, `.sexy`, `.cam`, `.webcam`, `.dating`, `.tube`

---

## [1.0.0] — 2026-08-25

### Initial Release
- Local VPN-based DNS filtering with domain blocklist
- SafeSearch enforcement for Google, YouTube, Bing, DuckDuckGo
- Sinkhole DNS responses for blocked domains (0.0.0.0)
- Upstream DNS via Cloudflare Family (1.1.1.3) for malware + adult blocking
- App policy engine with browser-level RESTRICTED/BLOCKED enforcement
- Tamper detection and audit logging
- Domain matching via suffix trie for sub-millisecond lookups
- Room database for persistent audit trail
- Foreground VPN service with health monitoring
- Material 3 Compose UI with dashboard
