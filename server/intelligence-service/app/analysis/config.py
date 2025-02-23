from pydantic_settings import BaseSettings
from typing import Dict

class AnalysisSettings(BaseSettings):
    """Configuration settings for analysis components"""
    # Cache settings
    CACHE_TTL_SECONDS: int = 3600
    CACHE_MAX_SIZE: int = 100

    # Rate limiting
    RATE_LIMIT_REQUESTS: int = 100
    RATE_LIMIT_WINDOW: int = 60  # seconds

    # Performance thresholds
    SLOW_OPERATION_THRESHOLD: float = 5.0  # seconds

    # Prompt templates directory
    PROMPTS_DIR: str = "prompts"

    # Analysis types
    ANALYSIS_TYPES: Dict[str, str] = {
        "review_pattern": "review_pattern.txt",
        "comment_quality": "comment_quality.txt",
        "learning_opportunities": "learning_opportunities.txt",
        "recommendations": "recommendations.txt"
    }

    class Config:
        env_prefix = "ANALYSIS_"

settings = AnalysisSettings()