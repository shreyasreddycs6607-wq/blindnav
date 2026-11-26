import base64
import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app import main
from app.main import app, decode_polyline6, make_token

client = TestClient(app)
JPEG = base64.b64encode(b"\xff\xd8\xff\xe0" + b"0" * 100).decode()


@pytest.fixture(autouse=True)
def clean(monkeypatch):
    for k in ("ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY", "GOOGLE_MAPS_API_KEY", "TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM",
              "BLINDNAV_REGISTER_KEY", "BLINDNAV_PROVIDERS"):
        monkeypatch.delenv(k, raising=False)
    monkeypatch.setenv("BLINDNAV_SECRET", "test-secret")
    main._hits.clear()
    main._transport = None
    yield
    main._transport = None


def auth(device="dev1"):
    return {"Authorization": f"Bearer {make_token(device)}"}


def fake(handler):
    main._transport = httpx.MockTransport(handler)


def test_health_open_and_lists_no_providers():
    r = client.get("/v1/health")
    assert r.status_code == 200 and r.json()["providers"] == []


def test_auth_required_and_register_flow():
    assert client.post("/v1/assistant/chat", json={"message": "hi"}).status_code == 401
    tok = client.post("/v1/auth/register", json={}).json()["token"]
    r = client.post("/v1/assistant/chat", json={"message": "hi"}, headers={"Authorization": f"Bearer {tok}"})
    assert r.status_code == 503 and r.json()["error"]["code"] == "provider_unavailable"  # authed, but no AI configured


def test_tampered_and_expired_tokens_rejected():
    good = make_token("d")
    assert client.get("/v1/nav/search?q=cafe", headers={"Authorization": "Bearer " + good[:-2] + "00"}).status_code == 401
    old = make_token("d", now=1)
    r = client.get("/v1/nav/search?q=cafe", headers={"Authorization": f"Bearer {old}"})
    assert r.status_code == 401 and r.json()["error"]["code"] == "token_expired"


def test_register_key_enforced(monkeypatch):
    monkeypatch.setenv("BLINDNAV_REGISTER_KEY", "k")
    assert client.post("/v1/auth/register", json={}).status_code == 401
    assert client.post("/v1/auth/register", json={}, headers={"X-Register-Key": "k"}).status_code == 200


def test_image_validation():
    h = auth()
    assert client.post("/v1/vision/scene", json={"image": "!!notb64"}, headers=h).status_code == 400
    assert client.post("/v1/vision/scene", json={"image": base64.b64encode(b"hello").decode()}, headers=h).status_code == 400
    big = base64.b64encode(b"\xff\xd8\xff" + b"0" * (4 * 1024 * 1024 + 1)).decode()
    assert client.post("/v1/vision/scene", json={"image": big}, headers=h).status_code == 413
    assert client.post("/v1/vision/scene", json={"image": JPEG, "language": "xx"}, headers=h).status_code == 400


def test_scene_uses_anthropic_and_sends_safety_prompt(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "x")
    seen = {}

    def handler(req):
        seen["body"] = json.loads(req.content)
        return httpx.Response(200, json={"content": [{"type": "text", "text": "A footpath with a scooter."}]})

    fake(handler)
    r = client.post("/v1/vision/scene", json={"image": JPEG, "language": "kn"}, headers=auth())
    assert r.status_code == 200
    j = r.json()
    assert j["description"] == "A footpath with a scooter." and j["advisory"] is True and j["model"] == "anthropic"
    assert "NEVER say a road is safe" in seen["body"]["system"] and "Kannada" in seen["body"]["system"]
    assert seen["body"]["messages"][0]["content"][0]["type"] == "image"


def test_provider_fallback_and_total_failure(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "x")
    monkeypatch.setenv("OPENAI_API_KEY", "y")

    def handler(req):
        if "anthropic" in str(req.url):
            return httpx.Response(500)
        return httpx.Response(200, json={"choices": [{"message": {"content": "From fallback."}}]})

    fake(handler)
    r = client.post("/v1/vision/ask", json={"image": JPEG, "question": "what is this"}, headers=auth())
    assert r.status_code == 200 and r.json()["answer"] == "From fallback." and r.json()["model"] == "openai"

    fake(lambda req: httpx.Response(500))
    r = client.post("/v1/vision/ask", json={"image": JPEG, "question": "what is this"}, headers=auth())
    assert r.status_code == 502 and r.json()["error"]["code"] == "upstream_failed"


def test_ocr_no_text(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "x")
    fake(lambda req: httpx.Response(200, json={"content": [{"type": "text", "text": "NO_TEXT"}]}))
    assert client.post("/v1/ocr/read", json={"image": JPEG, "language": "te"}, headers=auth()).json()["text"] == ""


def enc6(points, scale=1e6):
    out, plat, plon = [], 0, 0
    for lat, lon in points:
        for v, p in ((round(lat * scale), plat), (round(lon * scale), plon)):
            d = v - p
            d = ~(d << 1) if d < 0 else d << 1
            while d >= 0x20:
                out.append(chr((0x20 | (d & 0x1F)) + 63))
                d >>= 5
            out.append(chr(d + 63))
        plat, plon = round(lat * scale), round(lon * scale)
    return "".join(out)


SHAPE = enc6([(12.9, 77.5), (12.91, 77.51)])


def test_polyline6_decode():
    assert decode_polyline6(SHAPE) == [[12.9, 77.5], [12.91, 77.51]]


def test_search_and_route_parse(monkeypatch):
    def handler(req):
        if "photon" in str(req.url):
            return httpx.Response(200, json={"features": [{"properties": {"name": "City Market", "city": "Bengaluru", "state": "Karnataka"},
                                                           "geometry": {"coordinates": [77.57, 12.96]}}]})
        return httpx.Response(200, json={"trip": {"summary": {"length": 0.3, "time": 240}, "legs": [{
            "shape": SHAPE,
            "maneuvers": [{"instruction": "Walk north.", "length": 0.1, "begin_shape_index": 0},
                          {"instruction": "You have arrived.", "length": 0.0, "begin_shape_index": 1}]}]}})

    fake(handler)
    s = client.get("/v1/nav/search?q=city market", headers=auth()).json()["results"]
    assert s == [{"name": "City Market, Bengaluru, Karnataka", "lat": 12.96, "lon": 77.57}]
    r = client.post("/v1/nav/route", json={"from": {"lat": 12.9, "lon": 77.5}, "to": {"lat": 12.96, "lon": 77.57}}, headers=auth())
    j = r.json()
    assert r.status_code == 200 and j["distance_m"] == 300 and len(j["steps"]) == 2 and j["steps"][1]["lat"] == 12.91
    assert client.post("/v1/nav/route", json={"from": {"lat": 99, "lon": 0}, "to": {"lat": 0, "lon": 0}}, headers=auth()).status_code == 400


def test_route_upstream_failure():
    fake(lambda req: httpx.Response(400, json={"error": "no route"}))
    r = client.post("/v1/nav/route", json={"from": {"lat": 1, "lon": 1}, "to": {"lat": 2, "lon": 2}}, headers=auth())
    assert r.status_code == 502


def test_emergency_validation_config_and_rate_limit(monkeypatch):
    h = auth()
    body = {"contacts": ["+919876543210"], "location": {"lat": 12.9, "lon": 77.5}}
    assert client.post("/v1/emergency/notify", json={"contacts": ["12345"]}, headers=h).status_code == 400
    assert client.post("/v1/emergency/notify", json=body, headers=h).status_code == 503  # SMS not configured
    for k, v in (("TWILIO_ACCOUNT_SID", "AC1"), ("TWILIO_AUTH_TOKEN", "t"), ("TWILIO_FROM", "+10000000000")):
        monkeypatch.setenv(k, v)
    sent = []

    def handler(req):
        sent.append(req.content.decode())
        return httpx.Response(201, json={})

    fake(handler)
    r = client.post("/v1/emergency/notify", json=body, headers=h)
    assert r.status_code == 202 and r.json()["sent"] == ["+919876543210"] and "maps.google.com" in sent[0]
    client.post("/v1/emergency/notify", json=body, headers=h)
    client.post("/v1/emergency/notify", json=body, headers=h)
    assert client.post("/v1/emergency/notify", json=body, headers=h).status_code == 429


def test_general_rate_limit(monkeypatch):
    monkeypatch.setenv("BLINDNAV_RATE_PER_MIN", "2")
    h = auth("rl")
    assert client.get("/v1/nav/search?q=ab", headers=h).status_code != 429
    assert client.get("/v1/nav/search?q=ab", headers=h).status_code != 429
    assert client.get("/v1/nav/search?q=ab", headers=h).status_code == 429


def test_gemini_is_first_choice_and_request_shape(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "g")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "a")
    seen = {}

    def handler(req):
        seen["url"], seen["key"], seen["body"] = str(req.url), req.headers.get("x-goog-api-key"), json.loads(req.content)
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": "It looks like a 100 rupee note."}]}}]})

    fake(handler)
    r = client.post("/v1/vision/ask", json={"image": JPEG, "question": "which note?", "language": "hi",
                                            "history": [{"role": "user", "content": "hi"}, {"role": "assistant", "content": "hello"}]},
                    headers=auth())
    assert r.status_code == 200 and r.json()["model"] == "gemini" and "100 rupee" in r.json()["answer"]
    assert "generativelanguage.googleapis.com" in seen["url"] and seen["key"] == "g"
    parts = seen["body"]["contents"][-1]["parts"]
    assert parts[0]["inline_data"]["mime_type"] == "image/jpeg" and parts[1]["text"] == "which note?"
    assert seen["body"]["contents"][1]["role"] == "model"
    assert "Hindi" in seen["body"]["system_instruction"]["parts"][0]["text"]


def test_gemini_failure_falls_back_to_anthropic(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "g")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "a")

    def handler(req):
        if "googleapis" in str(req.url):
            return httpx.Response(429)
        return httpx.Response(200, json={"content": [{"type": "text", "text": "Fallback answer."}]})

    fake(handler)
    r = client.post("/v1/vision/scene", json={"image": JPEG}, headers=auth())
    assert r.status_code == 200 and r.json()["model"] == "anthropic"


def test_google_maps_search_and_route(monkeypatch):
    monkeypatch.setenv("GOOGLE_MAPS_API_KEY", "m")
    path5 = enc6([(12.9, 77.5), (12.91, 77.51)], 1e5)

    def handler(req):
        u = str(req.url)
        if "places.googleapis.com" in u:
            assert req.headers["x-goog-api-key"] == "m" and json.loads(req.content)["textQuery"] == "city market"
            return httpx.Response(200, json={"places": [{"displayName": {"text": "City Market"}, "formattedAddress": "Bengaluru",
                                                         "location": {"latitude": 12.96, "longitude": 77.57}}]})
        assert "routes.googleapis.com" in u and json.loads(req.content)["travelMode"] == "WALK"
        return httpx.Response(200, json={"routes": [{"distanceMeters": 1500, "duration": "1080s",
            "polyline": {"encodedPolyline": path5},
            "legs": [{"steps": [{"navigationInstruction": {"instructions": "Head north on MG Road"}, "distanceMeters": 900,
                                 "startLocation": {"latLng": {"latitude": 12.9, "longitude": 77.5}}}]}]}]})

    fake(handler)
    s = client.get("/v1/nav/search?q=city market&lat=12.9&lon=77.5", headers=auth()).json()
    assert s["source"] == "google" and s["results"] == [{"name": "City Market, Bengaluru", "lat": 12.96, "lon": 77.57}]
    j = client.post("/v1/nav/route", json={"from": {"lat": 12.9, "lon": 77.5}, "to": {"lat": 12.91, "lon": 77.51}}, headers=auth()).json()
    assert j["source"] == "google" and j["distance_m"] == 1500 and j["duration_s"] == 1080
    assert j["path"] == [[12.9, 77.5], [12.91, 77.51]]
    assert j["steps"][0]["instruction"] == "Head north on MG Road" and j["steps"][-1]["instruction"] == "You have arrived."


def test_google_maps_failure_falls_back_to_osm(monkeypatch):
    monkeypatch.setenv("GOOGLE_MAPS_API_KEY", "m")

    def handler(req):
        if "googleapis.com" in str(req.url):
            return httpx.Response(403)
        return httpx.Response(200, json={"features": [{"properties": {"name": "Cafe"}, "geometry": {"coordinates": [77.5, 12.9]}}]})

    fake(handler)
    s = client.get("/v1/nav/search?q=cafe", headers=auth()).json()
    assert s["source"] == "osm" and s["results"][0]["name"] == "Cafe"
    assert client.get("/v1/health").json()["maps"] == "google"
