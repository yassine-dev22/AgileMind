import os
from functools import lru_cache
from typing import Optional

try:
    from pydantic_settings import BaseSettings, SettingsConfigDict
except ImportError:
    from pydantic import BaseModel as BaseSettings  # type: ignore
    SettingsConfigDict = None  # type: ignore


class Settings(BaseSettings):
    rag_service_port: int = 8000
    rag_service_token: str = os.getenv("RAG_SERVICE_TOKEN", "agilemind-rag-secret-token")
    database_url: str = os.getenv(
        "DATABASE_URL",
        "postgresql://levelup:levelup_secret@localhost:55432/levelup"
    )
    gemini_api_key: Optional[str] = os.getenv("GEMINI_API_KEY", "")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "text-embedding-004")
    embedding_dimension: int = int(os.getenv("EMBEDDING_DIMENSION", "768"))
    llm_model: str = os.getenv("LLM_MODEL", "gemini-1.5-flash")
    top_k: int = int(os.getenv("TOP_K", "8"))
    temperature: float = float(os.getenv("TEMPERATURE", "0.2"))
    allow_mock_llm: bool = os.getenv("ALLOW_MOCK_LLM", "false").lower() in ("true", "1", "yes")

    if SettingsConfigDict:
        model_config = SettingsConfigDict(
            env_file=".env",
            env_file_encoding="utf-8",
            extra="ignore"
        )


@lru_cache()
def get_settings() -> Settings:
    return Settings()
