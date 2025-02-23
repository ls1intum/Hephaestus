from fastapi import APIRouter, HTTPException, Header, Depends
from typing import Dict, Any, Optional
from functools import wraps

from ..analysis.analyzer import analyzer
from ..analysis.models import (
    AnalysisRequest,
    AnalysisResponse,
    InsightRequest
)
from ..analysis.infrastructure import throttler, monitor

router = APIRouter(
    prefix='/analysis',
    tags=['analysis']
)

def handle_analysis_errors(func):
    """Decorator for consistent error handling in analysis endpoints"""
    @wraps(func)
    async def wrapper(*args, **kwargs):
        try:
            return await func(*args, **kwargs)
        except HTTPException:
            raise
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))
    return wrapper

def check_client_throttling(x_client_id: Optional[str] = Header(None)):
    """Dependency for client request throttling"""
    throttler.check_rate_limit(x_client_id or 'anonymous')
    return x_client_id

@router.post('/review-pattern', response_model=Dict[str, Any])
@handle_analysis_errors
async def analyze_review_pattern(
    request: AnalysisRequest,
    client_id: str = Depends(check_client_throttling)
):
    """Analyze code review patterns"""
    return await analyzer.analyze_review_pattern(
        request.review_data.dict(),
        request.context
    )

@router.post('/generate-insights', response_model=AnalysisResponse)
@handle_analysis_errors
async def generate_insights(
    request: AnalysisRequest,
    client_id: str = Depends(check_client_throttling)
):
    """Generate comprehensive review insights"""
    # Perform all analyses
    pattern_result = await analyzer.analyze_review_pattern(
        request.review_data.dict(),
        request.context
    )
    quality_result = await analyzer.assess_comment_quality(
        request.review_data.comments,
        request.context
    )
    learning_result = await analyzer.detect_learning_opportunities(
        request.review_data.dict(),
        request.context
    )
    
    # Combine results for recommendations
    analysis_results = {
        'pattern_analysis': pattern_result['pattern_analysis'],
        'quality_assessment': quality_result['quality_assessment'],
        'learning_opportunities': learning_result['learning_opportunities']
    }
    
    recommendations = await analyzer.generate_recommendations(
        analysis_results,
        request.context
    )
    
    return AnalysisResponse(
        pattern_analysis=pattern_result['pattern_analysis'],
        quality_assessment=quality_result['quality_assessment'],
        learning_opportunities=learning_result['learning_opportunities'],
        recommendations=recommendations['recommendations']
    )

@router.get('/recommendations')
@handle_analysis_errors
async def get_recommendations(
    analysis_id: str,
    client_id: str = Depends(check_client_throttling)
):
    """Get stored recommendations for a specific analysis"""
    # TODO: Implement recommendation storage and retrieval
    raise HTTPException(status_code=501, detail='Not implemented')

@router.get('/metrics')
async def get_performance_metrics():
    """Get current performance metrics"""
    return monitor.get_metrics()