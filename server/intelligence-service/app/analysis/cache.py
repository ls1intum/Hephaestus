from typing import Any, Optional
from datetime import datetime, timedelta
from cachetools import TTLCache

class AnalysisCache:
    def __init__(self, ttl_seconds: int = 3600, maxsize: int = 100):
        """Initialize cache with TTL and max size"""
        self.cache = TTLCache(maxsize=maxsize, ttl=ttl_seconds)
    
    def get_cache_key(self, analysis_type: str, data: Any) -> str:
        """Generate a cache key based on analysis type and data"""
        # In production, implement a more sophisticated hashing mechanism
        return f"{analysis_type}:{hash(str(data))}"
    
    def get(self, analysis_type: str, data: Any) -> Optional[Any]:
        """Get cached analysis result"""
        cache_key = self.get_cache_key(analysis_type, data)
        return self.cache.get(cache_key)
    
    def set(self, analysis_type: str, data: Any, result: Any) -> None:
        """Cache analysis result"""
        cache_key = self.get_cache_key(analysis_type, data)
        self.cache[cache_key] = result

# Global cache instance
analysis_cache = AnalysisCache()