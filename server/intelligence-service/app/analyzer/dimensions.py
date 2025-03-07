from pydantic import BaseModel, Field


class DimensionAssessment(BaseModel):
    """Assessment of a specific dimension of review quality"""
    justification: str = Field(description="Justification for the score")
    improvement: str = Field(description="Suggestion for improvement")
    score: int = Field(description="Score from 1-10")


class ReviewQualityAssessment(BaseModel):
    """A comprehensive assessment of a code review comment quality"""
    specificity: DimensionAssessment = Field(description="How specific is the feedback to the code")
    constructiveness: DimensionAssessment = Field(description="How well it suggests improvements")
    actionability: DimensionAssessment = Field(description="How clear what needs to be changed")
    educational_value: DimensionAssessment = Field(description="How well it teaches concepts")
    technical_depth: DimensionAssessment = Field(description="Technical value of the insight")
    relevance: DimensionAssessment = Field(description="Relevance to important code aspects")
    clarity: DimensionAssessment = Field(description="Clarity and understandability")
    overall_quality: DimensionAssessment = Field(description="Overall assessment")
    summary: str = Field(description="Summary assessment of the review quality")
    recommendations: list[str] = Field(description="List of specific recommendations for improvement")