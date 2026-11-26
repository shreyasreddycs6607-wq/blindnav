# BlindNav backend

Advisory cloud features only: scene description, questions, Kannada/Telugu OCR, place search, walking routes, SOS text messages.
**No obstacle warning runs here.** Those stay on the phone.

## Run
```
pip install -r requirements.txt
python -m pytest -q                       # 17 tests, no network or keys needed
set GEMINI_API_KEY=...                     # from Google AI Studio; keep keys in this server's environment, never in the app or in chat
set GOOGLE_MAPS_API_KEY=...                # optional; also ANTHROPIC_API_KEY / OPENAI_API_KEY as fallbacks
set BLINDNAV_SECRET=some-long-random-string
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```
Point the app's Settings > Server address at `http://<this-pc-ip>:8000` (phone and PC on the same Wi-Fi; allow the port in Windows Firewall).

## Settings (environment variables)
| Variable | Purpose |
|---|---|
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Google Gemini (default model `gemini-2.5-flash`: check it is still current and that your key has access) |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` | Anthropic (default model `claude-haiku-5-5`) |
| `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` | Any OpenAI-compatible API |
| `BLINDNAV_PROVIDERS` | Order tried, default `gemini,anthropic,openai`. Unconfigured ones are skipped; if one fails the next is used |
| `GOOGLE_MAPS_API_KEY` | Enables Google Places (search) and Routes (walking). Enable "Places API (New)" and "Routes API" for the key in Google Cloud. If unset or if Google fails, OpenStreetMap (Photon + Valhalla) is used |
| `BLINDNAV_SECRET` | Signs login tokens. Set it, or tokens die on every restart |
| `BLINDNAV_REGISTER_KEY` | If set, the app must send this key to register (type it in Settings > Server key) |
| `BLINDNAV_RATE_PER_MIN` (60), `BLINDNAV_SOS_PER_HOUR` (3) | Per-device limits |
| `PHOTON_URL`, `VALHALLA_URL` | Place search and routing (defaults are free public demo servers: fine for testing, not for real use) |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` | Enables SOS text messages |

## Endpoints
`GET /v1/health`, `POST /v1/auth/register`, `POST /v1/vision/scene`, `POST /v1/vision/ask`, `POST /v1/ocr/read`,
`POST /v1/assistant/chat`, `GET /v1/nav/search`, `POST /v1/nav/route`, `POST /v1/emergency/notify`.
Errors are `{"error": {"code": "...", "message": "..."}}`. Interactive docs at `/docs` while running.

## Known limits
- Never run against the real Gemini, Google Maps, Anthropic, OpenAI or Twilio here: only mocked in tests, using request shapes written from memory of their docs. The first live call may need small fixes (model names, field masks, API enablement, billing).
- Google Maps Platform is billed per request beyond a free monthly allowance per service; check current pricing and set a budget alert in Google Cloud.
- Registration is anonymous. There is no Play Integrity check, so a leaked URL lets anyone register and spend your AI credit (rate limits only slow this). Add attestation before any public release.
- Server-sent SMS in India may need sender/template registration (TRAI DLT) with your SMS provider: not checked.
- Photos are sent to the AI provider you configure. Nothing is stored by this server, but check that provider's retention terms.
- HTTP only (no TLS) as written. Put it behind HTTPS before real use.
