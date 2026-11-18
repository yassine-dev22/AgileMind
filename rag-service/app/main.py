import logging
from typing import Optional
from fastapi import Depends, FastAPI, Header, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.chain import run_rag_chain
from app.database import check_db_health
from app.retrieval import retrieve_relevant_chunks
from app.schemas import AskRequest, AskResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("rag_service")

app = FastAPI(
    title="AgileMind RAG Service",
    description="Microservice providing project-scoped vector retrieval and RAG question answering for AgileMind Co-Pilot.",
    version="1.0.0"
)

# CORS setup
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def verify_service_token(x_rag_service_token: Optional[str] = Header(None, alias="X-RAG-Service-Token")):
    """
    Validates shared secret token between Spring Boot and FastAPI.
    Rejects unauthorized requests without leaking internal details.
    """
    settings = get_settings()
    configured_token = settings.rag_service_token

    if configured_token and configured_token != "":
        if not x_rag_service_token or x_rag_service_token != configured_token:
            logger.warning("Rejected request due to missing or invalid X-RAG-Service-Token header.")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or missing service token header."
            )
    return x_rag_service_token


@app.get("/health", tags=["Monitoring"])
@app.get("/actuator/health", tags=["Monitoring"])
def health():
    """Health check endpoint for Kubernetes liveness/readiness probes."""
    db_ok = check_db_health()
    return {
        "status": "UP",
        "service": "agilemind-rag-service",
        "database": "UP" if db_ok else "DOWN"
    }


@app.post("/ask", response_model=AskResponse, tags=["RAG Co-Pilot"])
def ask(
    request: AskRequest,
    _token: Optional[str] = Depends(verify_service_token)
):
    """
    Primary RAG endpoint called by Spring Boot CopilotService.
    Retrieves project-scoped context chunks and generates a grounded response with citations.
    """
    logger.info(f"Received question for project_id={request.project_id}: '{request.question[:60]}...'")

    try:
        # Retrieve context chunks strictly filtered by project_id
        chunks = retrieve_relevant_chunks(
            question=request.question,
            project_id=request.project_id
        )

        # Run RAG chain to synthesize response with source citations
        response = run_rag_chain(
            question=request.question,
            chunks=chunks
        )
        return response

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error in /ask: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="The AI Co-Pilot service encountered an internal error."
        )


if __name__ == "__main__":
    import uvicorn
    settings = get_settings()
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.rag_service_port, reload=True)
