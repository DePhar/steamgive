import os
from datetime import datetime, timedelta, timezone

os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///./test_gifthunter.db"
os.environ["JWT_SECRET"] = "test-secret-that-is-long-enough-for-ci"
os.environ["ACCESS_TOKEN_MINUTES"] = "5"

from fastapi.testclient import TestClient
import pytest

from app.main import Base, SessionLocal, app, engine


@pytest.fixture()
def client():
    with TestClient(app) as test_client:
        yield test_client


def register(client, username, email):
    response = client.post("/auth/register", json={
        "username": username,
        "email": email,
        "password": "a-long-test-password-123",
    })
    assert response.status_code == 201, response.text
    return response.json()


def login(client, email):
    response = client.post("/auth/login", json={
        "email": email,
        "password": "a-long-test-password-123",
    })
    assert response.status_code == 200, response.text
    return response.json()["access_token"]


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def create_giveaway(client, token, ends_at=None):
    return client.post("/giveaways", headers=auth(token), json={
        "title": "Test giveaway",
        "description": "A test prize",
        "prize_name": "Test game",
        "cost_points": 7,
        "winner_count": 1,
        "ends_at": (ends_at or datetime.now(timezone.utc) + timedelta(hours=1)).isoformat(),
    })


def test_register_login_and_profile(client):
    user = register(client, "tester_one", "one@example.com")
    assert user["points"] == 100
    token = login(client, "one@example.com")
    response = client.get("/users/me", headers=auth(token))
    assert response.status_code == 200
    assert response.json()["username"] == "tester_one"
    assert response.json()["points"] == 100


def test_duplicate_registration_is_rejected(client):
    register(client, "tester_two", "two@example.com")
    response = client.post("/auth/register", json={
        "username": "tester_two",
        "email": "other@example.com",
        "password": "a-long-test-password-123",
    })
    assert response.status_code == 409


def test_entry_charges_points_and_prevents_duplicate(client):
    register(client, "creator", "creator@example.com")
    register(client, "entrant", "entrant@example.com")
    creator_token = login(client, "creator@example.com")
    entrant_token = login(client, "entrant@example.com")
    giveaway = create_giveaway(client, creator_token)
    assert giveaway.status_code == 201, giveaway.text
    giveaway_id = giveaway.json()["id"]

    entered = client.post(f"/giveaways/{giveaway_id}/enter", headers=auth(entrant_token))
    assert entered.status_code == 201, entered.text
    profile = client.get("/users/me", headers=auth(entrant_token)).json()
    assert profile["points"] == 93

    duplicate = client.post(f"/giveaways/{giveaway_id}/enter", headers=auth(entrant_token))
    assert duplicate.status_code == 409


def test_creator_cannot_enter_own_giveaway(client):
    register(client, "owner", "owner@example.com")
    token = login(client, "owner@example.com")
    giveaway = create_giveaway(client, token)
    response = client.post(f"/giveaways/{giveaway.json()['id']}/enter", headers=auth(token))
    assert response.status_code == 409


def test_giveaway_cannot_be_created_with_naive_or_past_time(client):
    register(client, "creator3", "creator3@example.com")
    token = login(client, "creator3@example.com")
    naive = client.post("/giveaways", headers=auth(token), json={
        "title": "Naive time", "prize_name": "Prize", "ends_at": "2030-01-01T00:00:00"
    })
    assert naive.status_code == 422
    past = create_giveaway(client, token, datetime.now(timezone.utc) - timedelta(minutes=1))
    assert past.status_code == 422


def test_draw_requires_creator_and_end_time(client):
    register(client, "creator4", "creator4@example.com")
    register(client, "other", "other@example.com")
    creator_token = login(client, "creator4@example.com")
    other_token = login(client, "other@example.com")
    giveaway = create_giveaway(client, creator_token)
    giveaway_id = giveaway.json()["id"]
    forbidden = client.post(f"/giveaways/{giveaway_id}/draw", headers=auth(other_token))
    assert forbidden.status_code == 403
    too_early = client.post(f"/giveaways/{giveaway_id}/draw", headers=auth(creator_token))
    assert too_early.status_code == 409
