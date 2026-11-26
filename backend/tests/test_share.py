import pytest
from fastapi.testclient import TestClient

from app import main
from app.main import app, make_token

client = TestClient(app)


@pytest.fixture(autouse=True)
def clean(monkeypatch):
    monkeypatch.setenv("BLINDNAV_SECRET", "test-secret")
    monkeypatch.delenv("BLINDNAV_PUBLIC_URL", raising=False)
    main._hits.clear()
    main._shares.clear()


def auth(device="sharer"):
    return {"Authorization": f"Bearer {make_token(device)}"}


def test_lifecycle_and_expiry(monkeypatch):
    t = [1000.0]
    monkeypatch.setattr(main, "now", lambda: t[0])
    j = client.post("/v1/track/start", json={"hours": 99}, headers=auth()).json()
    assert j["hours"] == 15 and j["url"].endswith("/t/" + j["id"]) and j["expires_at"] == 1000 + 15 * 3600
    sid, tok = j["id"], {"X-Track-Token": j["update_token"]}

    first = client.get(f"/v1/track/{sid}").json()
    assert first["active"] is True and first["lat"] is None
    assert client.post(f"/v1/track/{sid}/update", json={"lat": 12.9, "lon": 77.5}).status_code == 403
    assert client.post(f"/v1/track/{sid}/update", json={"lat": 12.9, "lon": 77.5}, headers={"X-Track-Token": "x"}).status_code == 403
    assert client.post(f"/v1/track/{sid}/update", json={"lat": 99, "lon": 0}, headers=tok).status_code == 400

    t[0] += 30
    assert client.post(f"/v1/track/{sid}/update", json={"lat": 12.9, "lon": 77.5, "accuracy_m": 8}, headers=tok).status_code == 200
    t[0] += 20
    g = client.get(f"/v1/track/{sid}").json()
    assert (g["lat"], g["lon"], g["seconds_ago"], g["active"]) == (12.9, 77.5, 20, True)
    page = client.get(f"/t/{sid}")
    assert page.status_code == 200 and sid in page.text and "noindex" in page.headers["x-robots-tag"]

    t[0] += 15 * 3600
    assert client.get(f"/v1/track/{sid}").json()["active"] is False
    assert client.post(f"/v1/track/{sid}/update", json={"lat": 1, "lon": 1}, headers=tok).status_code == 410


def test_stop_unknown_limit_and_public_url(monkeypatch):
    monkeypatch.setenv("BLINDNAV_PUBLIC_URL", "https://example.test/")
    ids = [client.post("/v1/track/start", json={"hours": 1}, headers=auth("s2")).json() for _ in range(4)]
    assert ids[0]["url"].startswith("https://example.test/t/")
    assert client.get(f"/v1/track/{ids[0]['id']}").json()["active"] is False  # oldest ended: max 3 live per device
    assert client.get(f"/v1/track/{ids[3]['id']}").json()["active"] is True
    assert client.post(f"/v1/track/{ids[3]['id']}/stop", headers={"X-Track-Token": "bad"}).status_code == 403
    assert client.post(f"/v1/track/{ids[3]['id']}/stop", headers={"X-Track-Token": ids[3]["update_token"]}).status_code == 200
    assert client.get(f"/v1/track/{ids[3]['id']}").json()["active"] is False
    assert client.get("/v1/track/" + "0" * 32).status_code == 404
    assert client.get("/t/not-an-id").status_code == 404
    assert client.post("/v1/track/start", json={"hours": 1}).status_code == 401
