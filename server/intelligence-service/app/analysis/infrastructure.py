from typing import Any, Dict, Optional
from datetime import datetime, timedelta
from cachetools import TTLCache
from collections import defaultdict
from time import time
from fastapi import HTTPException

from .interfaces import CacheInterface, MonitorInterface, ThrottlerInterface
from .config import settings

class AnalysisCache(CacheInterface):
    """TTL-based cache implementation for analysis results"""
    def __init__(self):
        self.cache = TTLCache(
            maxsize=settings.CACHE_MAX_SIZE,
            ttl=settings.CACHE_TTL_SECONDS
        )

    def _get_cache_key(self, key: str, data: Any) -> str:
        return f"{key}:{hash(str(data))}"

    def get(self, key: str, data: Any) -> Optional[Any]:
        cache_key = self._get_cache_key(key, data)
        return self.cache.get(cache_key)

    def set(self, key: str, data: Any, result: Any) -> None:
        cache_key = self._get_cache_key(key, data)
        self.cache[cache_key] = result

class PerformanceMonitor(MonitorInterface):
    """Performance monitoring implementation"""
    def __init__(self):
        self.metrics = defaultdict(lambda: {
            'count': 0,
            'total_time': 0,
            'avg_time': 0,
            'max_time': 0
        })

    def update_metrics(self, operation: str, duration: float) -> None:
        metrics = self.metrics[operation]
        metrics['count'] += 1
        metrics['total_time'] += duration
        metrics['avg_time'] = metrics['total_time'] / metrics['count']
        metrics['max_time'] = max(metrics['max_time'], duration)

    def get_metrics(self) -> Dict[str, Any]:
        return dict(self.metrics)

class RequestThrottler(ThrottlerInterface):
    """Request throttling implementation"""
    def __init__(self):
        self.rate_limit = settings.RATE_LIMIT_REQUESTS
        self.time_window = settings.RATE_LIMIT_WINDOW
        self.requests: Dict[str, list] = {}

    def _cleanup_old_requests(self, client_id: str) -> None:
        if client_id not in self.requests:
            return

        current_time = datetime.now()
        self.requests[client_id] = [
            req_time for req_time in self.requests[client_id]
            if current_time - req_time < timedelta(seconds=self.time_window)
        ]

    def check_rate_limit(self, client_id: str) -> None:
        self._cleanup_old_requests(client_id)

        if client_id not in self.requests:
            self.requests[client_id] = []

        if len(self.requests[client_id]) >= self.rate_limit:
            raise HTTPException(
                status_code=429,
                detail="Rate limit exceeded. Please try again later."
            )

        self.requests[client_id].append(datetime.now())

# Infrastructure instances
cache = AnalysisCache()
monitor = PerformanceMonitor()
throttler = RequestThrottler()