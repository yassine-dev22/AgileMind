import logging
from typing import Any, Dict, List, Optional
from app.config import get_settings
from app.database import get_db_connection
from app.embeddings import get_embedding

logger = logging.getLogger("rag_service.retrieval")

# Optional in-memory mock store for testing without live PostgreSQL
_MOCK_CHUNKS: List[Dict[str, Any]] = []


def register_mock_chunk(chunk: Dict[str, Any]):
    """Registers an in-memory chunk for test isolation."""
    _MOCK_CHUNKS.append(chunk)


def clear_mock_chunks():
    """Clears in-memory test chunks."""
    _MOCK_CHUNKS.clear()


def retrieve_relevant_chunks(
    question: str,
    project_id: int,
    top_k: Optional[int] = None
) -> List[Dict[str, Any]]:
    """
    Retrieves the top-k relevant document chunks for a project using pgvector cosine similarity.
    Enforces strict project_id isolation in the SQL query.
    """
    settings = get_settings()
    k = top_k if top_k is not None else settings.top_k

    # Generate query embedding
    query_vector = get_embedding(question)

    # 1. First check if in-memory test chunks exist (useful for mock/unit tests)
    if _MOCK_CHUNKS:
        project_chunks = [c for c in _MOCK_CHUNKS if c.get("project_id") == project_id]
        return project_chunks[:k]

    # 2. Query PostgreSQL pgvector with parameterized SQL
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            # Check if document_chunks table exists
            cur.execute("""
                SELECT EXISTS (
                    SELECT FROM information_schema.tables 
                    WHERE table_name = 'document_chunks'
                );
            """)
            table_exists = cur.fetchone()["exists"]
            if not table_exists:
                logger.warning("Table 'document_chunks' does not exist yet in database.")
                conn.close()
                return []

            # Parameterized cosine similarity query strictly bounded to project_id
            vector_str = "[" + ",".join(str(v) for v in query_vector) + "]"
            sql = """
                SELECT 
                    id,
                    project_id,
                    source_type,
                    source_id,
                    author,
                    created_at,
                    content,
                    chunk_index,
                    1 - (embedding <=> %s::vector) AS similarity
                FROM document_chunks
                WHERE project_id = %s
                ORDER BY embedding <=> %s::vector ASC
                LIMIT %s;
            """
            cur.execute(sql, (vector_str, project_id, vector_str, k))
            rows = cur.fetchall()

        conn.close()
        return [dict(r) for r in rows]

    except Exception as e:
        logger.warning(f"Vector retrieval query failed ({e}); returning empty context.")
        return []
