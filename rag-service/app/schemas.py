import re
from typing import List, Optional
from pydantic import BaseModel, Field, field_validator


class AskRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=500, description="User question about the project")
    project_id: int = Field(..., gt=0, alias="projectId", description="Project ID scope")

    model_config = {
        "populate_by_name": True
    }

    @field_validator("question")
    @classmethod
    def sanitize_question(cls, value: str) -> str:
        # Strip control characters (including zero-width and unprintable chars)
        sanitized = re.sub(r"[\x00-\x1f\x7f-\x9f]", "", value).strip()
        if not sanitized:
            raise ValueError("question must contain non-empty printable text")
        if len(sanitized) > 500:
            raise ValueError("question cannot exceed 500 characters")
        return sanitized


class Citation(BaseModel):
    source_type: str = Field(..., description="Entity type: DecisionNote, Task, Sprint, etc.")
    source_id: int = Field(..., description="ID of the source entity")
    author: Optional[str] = Field(None, description="Author or contributor")
    date: Optional[str] = Field(None, description="Creation or update date")
    chunk_index: Optional[int] = Field(0, description="Index of the chunk in source document")


class AskResponse(BaseModel):
    answer: str = Field(..., description="Synthesized grounded answer from project context")
    citations: List[Citation] = Field(default_factory=list, description="Citations used in the answer")


class DocumentChunkCreate(BaseModel):
    project_id: int = Field(..., gt=0)
    source_type: str = Field(..., min_length=1)
    source_id: int = Field(..., gt=0)
    author: Optional[str] = None
    created_at: Optional[str] = None
    content: str = Field(..., min_length=1)
    chunk_index: int = Field(default=0, ge=0)
    embedding: Optional[List[float]] = None
