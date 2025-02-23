from typing import Any, Callable, Dict
from functools import wraps
from time import time
from collections import defaultdict
import logging

logger = logging.getLogger(__name__)

class PerformanceMonitor:
    def __init__(self):
        self.metrics = defaultdict(lambda: {
            'count': 0,
            'total_time': 0,
            'avg_time': 0,
            'max_time': 0
        })
    
    def update_metrics(self, operation: str, duration: float) -> None:
        """Update performance metrics for an operation"""
        metrics = self.metrics[operation]
        metrics['count'] += 1
        metrics['total_time'] += duration
        metrics['avg_time'] = metrics['total_time'] / metrics['count']
        metrics['max_time'] = max(metrics['max_time'], duration)
    
    def get_metrics(self) -> Dict[str, Any]:
        """Get current performance metrics"""
        return dict(self.metrics)

# Global monitor instance
performance_monitor = PerformanceMonitor()

def monitor_performance(operation: str):
    """Decorator to monitor function performance"""
    def decorator(func: Callable):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            start_time = time()
            try:
                result = await func(*args, **kwargs)
                duration = time() - start_time
                performance_monitor.update_metrics(operation, duration)
                if duration > 5:  # Log slow operations
                    logger.warning(f"Slow operation detected: {operation} took {duration:.2f}s")
                return result
            except Exception as e:
                duration = time() - start_time
                logger.error(f"Error in {operation}: {str(e)}. Duration: {duration:.2f}s")
                raise
        return wrapper
    return decorator