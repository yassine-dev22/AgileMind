from app.chain import build_citations, run_rag_chain
from app.retrieval import clear_mock_chunks, register_mock_chunk, retrieve_relevant_chunks


def setup_function():
    clear_mock_chunks()


def teardown_function():
    clear_mock_chunks()


def test_project_isolation():
    """
    Critical security test: Chunks belonging to Project 1 must NEVER be returned for Project 2.
    """
    register_mock_chunk({
        "id": 1,
        "project_id": 1,
        "source_type": "DecisionNote",
        "source_id": 10,
        "author": "Alice",
        "created_at": "2026-10-01",
        "content": "Confidential decision for Project 1.",
        "chunk_index": 0
    })
    register_mock_chunk({
        "id": 2,
        "project_id": 2,
        "source_type": "DecisionNote",
        "source_id": 20,
        "author": "Bob",
        "created_at": "2026-10-02",
        "content": "Confidential decision for Project 2.",
        "chunk_index": 0
    })

    # Query for project 1
    chunks_p1 = retrieve_relevant_chunks("confidential decision", project_id=1)
    assert len(chunks_p1) == 1
    assert chunks_p1[0]["project_id"] == 1
    assert chunks_p1[0]["source_id"] == 10

    # Query for project 2
    chunks_p2 = retrieve_relevant_chunks("confidential decision", project_id=2)
    assert len(chunks_p2) == 1
    assert chunks_p2[0]["project_id"] == 2
    assert chunks_p2[0]["source_id"] == 20

    # Query for nonexistent project 99
    chunks_p99 = retrieve_relevant_chunks("confidential decision", project_id=99)
    assert len(chunks_p99) == 0


def test_citations_builder_and_deduplication():
    chunks = [
        {
            "source_type": "Task",
            "source_id": 55,
            "author": "Developer",
            "created_at": "2026-10-10",
            "chunk_index": 0
        },
        # Duplicate chunk from same source and chunk_index
        {
            "source_type": "Task",
            "source_id": 55,
            "author": "Developer",
            "created_at": "2026-10-10",
            "chunk_index": 0
        },
        {
            "source_type": "Sprint",
            "source_id": 2,
            "author": "Scrum Master",
            "created_at": "2026-10-12",
            "chunk_index": 0
        }
    ]

    citations = build_citations(chunks)
    assert len(citations) == 2
    assert citations[0].source_type == "Task"
    assert citations[0].source_id == 55
    assert citations[1].source_type == "Sprint"
    assert citations[1].source_id == 2


def test_empty_chain_response():
    response = run_rag_chain("What was the outcome of Sprint 1?", chunks=[])
    assert response.answer == "No relevant project information found for this question."
    assert response.citations == []
