import hashlib
import logging
import math
from typing import List
from app.config import get_settings

logger = logging.getLogger("rag_service.embeddings")

try:
    import google.generativeai as genai
except ImportError:
    genai = None


def generate_local_fallback_embedding(text: str, dimension: int = 768) -> List[float]:
    """
    Deterministic normalized pseudo-embedding for testing or offline demonstration.
    Generates a unit-norm vector based on sha256 hash buckets.
    """
    cleaned = text.lower().strip()
    vector = [0.0] * dimension
    words = cleaned.split()
    if not words:
        words = ["empty"]

    for word in words:
        h = int(hashlib.sha256(word.encode("utf-8")).hexdigest(), 16)
        for i in range(dimension):
            bit = (h >> (i % 64)) & 1
            vector[i] += 1.0 if bit else -1.0

    # L2 normalize
    norm = math.sqrt(sum(x * x for x in vector))
    if norm > 0:
        vector = [x / norm for x in vector]
    return vector


def get_embedding(text: str) -> List[float]:
    """
    Computes text embedding using Google text-embedding-004 if API key configured,
    otherwise gracefully falls back to deterministic local embedding.
    """
    settings = get_settings()
    dimension = settings.embedding_dimension

    if settings.gemini_api_key and genai is not None:
        try:
            genai.configure(api_key=settings.gemini_api_key)
            result = genai.embed_content(
                model=f"models/{settings.embedding_model}",
                content=text,
                task_type="retrieval_query"
            )
            embedding = result.get("embedding", [])
            if embedding and len(embedding) > 0:
                return embedding
        except Exception as e:
            logger.warning(f"Google embedding API call failed ({e}), falling back to local embedding.")

    # Local fallback
    return generate_local_fallback_embedding(text, dimension=dimension)
