import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.config import get_settings
from app.retrieval import clear_mock_chunks, register_mock_chunk

client = TestClient(app)
VALID_TOKEN = get_settings().rag_service_token


@pytest.fixture(autouse=True)
def cleanup():
    clear_mock_chunks()
    yield
    clear_mock_chunks()


def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "agilemind-rag-service"


def test_ask_missing_token_rejected():
    response = client.post(
        "/ask",
        json={"question": "What is the sprint status?", "project_id": 1}
    )
    assert response.status_code == 401
    assert "Invalid or missing" in response.json()["detail"]


def test_ask_invalid_token_rejected():
    response = client.post(
        "/ask",
        headers={"X-RAG-Service-Token": "wrong-token-value"},
        json={"question": "What is the sprint status?", "project_id": 1}
    )
    assert response.status_code == 401


def test_ask_empty_question_rejected():
    response = client.post(
        "/ask",
        headers={"X-RAG-Service-Token": VALID_TOKEN},
        json={"question": "   ", "project_id": 1}
    )
    assert response.status_code == 422


def test_ask_invalid_project_id_rejected():
    response = client.post(
        "/ask",
        headers={"X-RAG-Service-Token": VALID_TOKEN},
        json={"question": "How many tasks are done?", "project_id": -5}
    )
    assert response.status_code == 422


def test_ask_oversized_question_rejected():
    long_question = "A" * 501
    response = client.post(
        "/ask",
        headers={"X-RAG-Service-Token": VALID_TOKEN},
        json={"question": long_question, "project_id": 1}
    )
    assert response.status_code == 422


def test_ask_empty_context_fallback():
    # When no chunks exist in DB or mock store, must return standard fallback without crashing
    response = client.post(
        "/ask",
        headers={"X-RAG-Service-Token": VALID_TOKEN},
        json={"question": "Why did Sprint 3 slip?", "project_id": 999}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["answer"] == "No relevant project information found for this question."
    assert data["citations"] == []


def test_ask_with_retrieved_context_success():
    # Register mock chunk for project 1
    register_mock_chunk({
        "id": 101,
        "project_id": 1,
        "source_type": "DecisionNote",
        "source_id": 42,
        "author": "Yassine Safar",
        "created_at": "2026-10-15",
        "content": "Decision made to migrate from JWT fallback secrets to environment secrets.",
        "chunk_index": 0
    })

    response = client.post(
        "/ask",
        headers={"X-RAG-Service-Token": VALID_TOKEN},
        json={"question": "What decision was made about secrets?", "project_id": 1}
    )
    assert response.status_code == 200
    data = response.json()
    assert "answer" in data
    assert len(data["citations"]) == 1
    assert data["citations"][0]["source_type"] == "DecisionNote"
    assert data["citations"][0]["source_id"] == 42
    assert data["citations"][0]["author"] == "Yassine Safar"
