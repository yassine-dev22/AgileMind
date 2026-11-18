import logging
from typing import Generator
from app.config import get_settings

logger = logging.getLogger("rag_service.database")

try:
    import psycopg2
    from psycopg2.extras import RealDictCursor
except ImportError:
    psycopg2 = None
    RealDictCursor = None


def get_db_connection():
    """
    Creates and returns a raw psycopg2 connection.
    Enables RealDictCursor by default for dict-like row access.
    """
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed. Install psycopg2-binary to connect to PostgreSQL.")
    settings = get_settings()
    conn = psycopg2.connect(
        settings.database_url,
        cursor_factory=RealDictCursor,
        connect_timeout=5
    )
    return conn


def check_db_health() -> bool:
    """Checks if the PostgreSQL database is reachable."""
    if psycopg2 is None:
        return False
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            cur.execute("SELECT 1;")
            result = cur.fetchone()
        conn.close()
        return result is not None
    except Exception as e:
        logger.warning(f"Database health check failed: {e}")
        return False
