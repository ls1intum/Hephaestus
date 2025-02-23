from typing import Dict, Optional
from datetime import datetime, timedelta
from fastapi import HTTPException

class RequestThrottler:
    def __init__(self, rate_limit: int = 100, time_window: int = 60):
        """Initialize throttler with rate limit per time window (in seconds)"""
        self.rate_limit = rate_limit
        self.time_window = time_window
        self.requests: Dict[str, list] = {}
    
    def _cleanup_old_requests(self, client_id: str) -> None:
        """Remove requests outside the current time window"""
        if client_id not in self.requests:
            return
        
        current_time = datetime.now()
        self.requests[client_id] = [
            req_time for req_time in self.requests[client_id]
            if current_time - req_time < timedelta(seconds=self.time_window)
        ]
    
    def check_rate_limit(self, client_id: str) -> None:
        """Check if request is within rate limit"""
        self._cleanup_old_requests(client_id)
        
        if client_id not in self.requests:
            self.requests[client_id] = []
        
        current_requests = len(self.requests[client_id])
        if current_requests >= self.rate_limit:
            raise HTTPException(
                status_code=429,
                detail="Rate limit exceeded. Please try again later."
            )
        
        self.requests[client_id].append(datetime.now())

# Global throttler instance
request_throttler = RequestThrottler()