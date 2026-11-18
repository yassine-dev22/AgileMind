import logging
from typing import Any, Dict, List
from app.config import get_settings
from app.schemas import AskResponse, Citation

logger = logging.getLogger("rag_service.chain")

SYSTEM_PROMPT = """You are AgileMind Co-Pilot, an intelligent Agile assistant embedded in the project management platform.
Your task is to answer the user's question using ONLY the provided project context below.

STRICT RULES:
1. Base your answer strictly on the provided context items (decision notes, tasks, sprints, diagnostics).
2. Do NOT extrapolate, speculate, or make up facts not present in the context.
3. If the context does not contain enough information to answer the question, respond with:
   "No relevant project information found for this question."
4. Be concise, professional, and clear.
"""

EMPTY_CONTEXT_FALLBACK = "No relevant project information found for this question."


def build_citations(chunks: List[Dict[str, Any]]) -> List[Citation]:
    """
    Extracts and deduplicates citations from retrieved chunks.
    """
    seen = set()
    citations = []

    for chunk in chunks:
        source_type = chunk.get("source_type", "Unknown")
        source_id = chunk.get("source_id", 0)
        chunk_idx = chunk.get("chunk_index", 0)
        key = (source_type, source_id, chunk_idx)

        if key not in seen:
            seen.add(key)
            citations.append(
                Citation(
                    source_type=source_type,
                    source_id=int(source_id),
                    author=str(chunk.get("author")) if chunk.get("author") else None,
                    date=str(chunk.get("created_at")) if chunk.get("created_at") else None,
                    chunk_index=chunk_idx
                )
            )

    return citations


def run_rag_chain(question: str, chunks: List[Dict[str, Any]]) -> AskResponse:
    """
    Executes the RAG pipeline over retrieved context chunks.
    If chunks are empty, returns the standard empty response without calling any LLM.
    """
    # 1. Edge case handling: Empty retrieval (Sprint 1 & Sprint 3 requirement)
    if not chunks:
        return AskResponse(
            answer=EMPTY_CONTEXT_FALLBACK,
            citations=[]
        )

    citations = build_citations(chunks)

    # 2. Build context string from retrieved chunks
    context_blocks = []
    for i, c in enumerate(chunks, 1):
        st = c.get("source_type", "Document")
        sid = c.get("source_id", "")
        author = c.get("author", "Unknown")
        date = c.get("created_at", "")
        content = c.get("content", "").strip()
        context_blocks.append(f"[{i}] ({st} #{sid} by {author} on {date}):\n{content}")

    context_str = "\n\n".join(context_blocks)
    settings = get_settings()

    # 3. If Gemini API key is configured, call Gemini
    if settings.gemini_api_key:
        try:
            import google.generativeai as genai
            genai.configure(api_key=settings.gemini_api_key)
            model = genai.GenerativeModel(
                model_name=settings.llm_model,
                system_instruction=SYSTEM_PROMPT
            )
            prompt = f"Project Context:\n{context_str}\n\nUser Question:\n{question}"
            response = model.generate_content(
                prompt,
                generation_config={"temperature": settings.temperature}
            )
            text = response.text.strip() if response and response.text else EMPTY_CONTEXT_FALLBACK
            return AskResponse(answer=text, citations=citations)
        except Exception as e:
            logger.error(f"LLM generation failed: {e}")
            # Degraded fallback with citations
            return AskResponse(
                answer=f"Based on project records: {chunks[0].get('content', '')[:200]}...",
                citations=citations
            )

    # 4. Fallback when running offline or in tests without Gemini API key
    summary_text = (
        f"Based on the project records ({len(chunks)} relevant items found): "
        f"{chunks[0].get('content', '')}"
    )
    return AskResponse(answer=summary_text, citations=citations)
