from typing import List, Dict, Any
from pathlib import Path
from functools import wraps
from time import time
from langchain.prompts import PromptTemplate
from langchain.chains import LLMChain

from ..model import model
from .interfaces import AnalyzerInterface
from .infrastructure import cache, monitor
from .config import settings

def cached_analysis(analysis_type: str):
    """Decorator for caching analysis results"""
    def decorator(func):
        @wraps(func)
        async def wrapper(self, *args, **kwargs):
            # Try cache first
            data = args[0] if args else kwargs.get('review_data') or kwargs.get('comments')
            cached_result = cache.get(analysis_type, data)
            if cached_result:
                return {f"{analysis_type}": cached_result}

            # Perform analysis
            start_time = time()
            result = await func(self, *args, **kwargs)
            duration = time() - start_time

            # Update metrics
            monitor.update_metrics(func.__name__, duration)

            # Cache result
            cache.set(analysis_type, data, result[analysis_type])
            return result

        return wrapper
    return decorator

class ReviewAnalyzer(AnalyzerInterface):
    """Analyzes code reviews using LLM-based chains"""
    def __init__(self):
        self._init_chains()

    def _init_chains(self) -> None:
        """Initialize LLM chains for different analysis types"""
        self.chains = {}
        prompts_dir = Path(__file__).parent / settings.PROMPTS_DIR

        for analysis_type, template_file in settings.ANALYSIS_TYPES.items():
            with open(prompts_dir / template_file) as f:
                template = f.read()
                input_vars = ['context']
                if analysis_type != 'recommendations':
                    input_vars.append(f"{analysis_type.split('_')[0]}_data")
                else:
                    input_vars.append('analysis_results')

                prompt = PromptTemplate(
                    template=template,
                    input_variables=input_vars
                )
                self.chains[analysis_type] = LLMChain(llm=model, prompt=prompt)

    @cached_analysis('pattern_analysis')
    async def analyze_review_pattern(self, review_data: Dict[str, Any], context: str) -> Dict[str, Any]:
        """Analyze code review patterns"""
        result = await self.chains['review_pattern'].arun(
            context=context,
            review_data=review_data
        )
        return {'pattern_analysis': result}

    @cached_analysis('quality_assessment')
    async def assess_comment_quality(self, comments: List[str], context: str) -> Dict[str, Any]:
        """Assess the quality of review comments"""
        result = await self.chains['comment_quality'].arun(
            context=context,
            comments=comments
        )
        return {'quality_assessment': result}

    @cached_analysis('learning_opportunities')
    async def detect_learning_opportunities(self, review_data: Dict[str, Any], context: str) -> Dict[str, Any]:
        """Detect learning opportunities from review data"""
        result = await self.chains['learning_opportunities'].arun(
            context=context,
            review_data=review_data
        )
        return {'learning_opportunities': result}

    @cached_analysis('recommendations')
    async def generate_recommendations(self, analysis_results: Dict[str, Any], context: str) -> Dict[str, Any]:
        """Generate recommendations based on analysis results"""
        result = await self.chains['recommendations'].arun(
            context=context,
            analysis_results=analysis_results
        )
        return {'recommendations': result}

# Global analyzer instance
analyzer = ReviewAnalyzer()