# ShieldX: Privacy & Data Protection Architecture

## 1. Local-First Processing
- All DNS filtering, blocklist lookups, text normalization, and URL classification occur **100% locally on the device**.
- No user browsing history, search terms, or visit records are uploaded to remote servers.

---

## 2. Zero Explicit Media Storage & Redaction
- **No Explicit Previews**: Blocked events and notifications strictly display the category and reason code, never explicit thumbnails or full content.
- **URL & Credential Redaction (`Redactor`)**:
  - Sensitive query parameters (`password`, `token`, `key`, `auth`, `session`, `secret`) are automatically redacted before saving to audit logs.
  - Large binary blobs in URLs are stripped.

---

## 3. Google Play VpnService Policy Compliance
- `VpnService` is utilized solely for legitimate on-device content filtering and parental security.
- Clear disclosure is presented to the user during onboarding and on the main dashboard.
- No user traffic is routed to external proxy servers; DNS queries are resolved directly via standard upstream family DNS or synthetic sinkhole.
