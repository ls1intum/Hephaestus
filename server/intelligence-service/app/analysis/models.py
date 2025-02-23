from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field

class ReviewData(BaseModel):
    """Model for code review data"""
    review_id: str
    comments: List[str]
    author: str
    reviewers: List[str]
    created_at: str
    updated_at: str
    metadata: Optional[Dict[str, Any]] = Field(default_factory=dict)

class AnalysisRequest(BaseModel):
    """Model for analysis request"""
    review_data: ReviewData
    context: Optional[str] = ''

class AnalysisResponse(BaseModel):
    """Model for analysis response"""
    pattern_analysis: Optional[str] = None
    quality_assessment: Optional[str] = None
    learning_opportunities: Optional[str] = None
    recommendations: Optional[str] = None

class InsightRequest(BaseModel):
    """Model for generating insights"""
    analysis_results: Dict[str, Any]
    context: Optional[str] = ''