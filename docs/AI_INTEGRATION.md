# ShieldX: Gemini 3.7 Flash AI Integration & Cost Controls

## 1. Principles
1. **Never Security Authority**: AI is strictly a secondary reasoning classifier for uncertain/ambiguous inputs. Deterministic offline rules are authoritative.
2. **Strict Structured Output**: All responses must match the schema:
   ```json
   {
     "decision": "ALLOW | BLOCK | UNCERTAIN",
     "category": "SAFE | NSFW | EXPLICIT | SEXUAL_SERVICE | OTHER",
     "confidence": 0.0,
     "reason_code": "string"
   }
   ```
3. **Fail-Safe Fallback**: If Gemini times out, returns HTTP 429 quota error, returns malformed text, or the device is offline, ShieldX handles it as `AI_UNAVAILABLE` and continues with deterministic local rules.

---

## 2. Cost & Latency Control Mechanisms

- **SHA-256 Classification Cache**:
  - Room DB table `classification_cache` stores content hash, decision, category, confidence, and 24-hour TTL.
  - Identical content/URLs are never re-sent to AI.
- **Request Deduplication**:
  - In-flight mutex map ensures concurrent requests for the same content share a single network call.
- **Circuit Breaker**:
  - Trips if 3 consecutive failures occur, preventing unnecessary network attempts during outages for 60 seconds.
- **Token Bucket Rate Limiting**:
  - Enforces a maximum of 30 requests per minute.
