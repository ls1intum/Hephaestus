from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional

class AnalyzerInterface(ABC):
    """Interface for code review analysis operations"""
    @abstractmethod
    async def analyze_review_pattern(self, review_data: Dict[str, Any], context: str) -> Dict[str, Any]:
        pass

    @abstractmethod
    async def assess_comment_quality(self, comments: List[str], context: str) -> Dict[str, Any]:
        pass

    @abstractmethod
    async def detect_learning_opportunities(self, review_data: Dict[str, Any], context: str) -> Dict[str, Any]:
        pass

    @abstractmethod
    async def generate_recommendations(self, analysis_results: Dict[str, Any], context: str) -> Dict[str, Any]:
        pass

class CacheInterface(ABC):
    """Interface for caching operations"""
    @abstractmethod
    def get(self, key: str, data: Any) -> Optional[Any]:
        pass

    @abstractmethod
    def set(self, key: str, data: Any, result: Any) -> None:
        pass

class MonitorInterface(ABC):
    """Interface for performance monitoring"""
    @abstractmethod
    def update_metrics(self, operation: str, duration: float) -> None:
        pass

    @abstractmethod
    def get_metrics(self) -> Dict[str, Any]:
        pass

class ThrottlerInterface(ABC):
    """Interface for request throttling"""
    @abstractmethod
    def check_rate_limit(self, client_id: str) -> None:
        pass