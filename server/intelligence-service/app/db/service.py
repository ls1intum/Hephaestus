"""
Database service for the Hephaestus intelligence service.

Simple service for accessing issue and pull request data from the application-server database.
"""

from typing import List, Optional, Dict, Any
from sqlalchemy import select, desc, and_

from .models_gen import Issue, User, Repository, Pullrequestbadpractice, BadPracticeDetection
from .config import DatabaseSession


class IssueDatabaseService:
    """Database service for accessing issue and pull request data."""

    @staticmethod
    def get_issues_assigned_to_user(user_id: int, limit: int = 50) -> List[Issue]:
        """Get issues (not pull requests) assigned to a specific user, ordered by creation time."""
        with DatabaseSession() as session:
            return list(
                session.execute(
                    select(Issue)
                    .where(
                        and_(
                            Issue.author_id == user_id,
                            Issue.issue_type == "ISSUE",   
                        )
                    )
                    .order_by(desc(Issue.created_at))
                    .limit(limit)
                ).scalars()
            )

    @staticmethod
    def get_pull_requests_assigned_to_user(user_id: int, limit: int = 50) -> List[Issue]:
        """Get pull requests assigned to a specific user, ordered by creation time."""
        with DatabaseSession() as session:
            return list(
                session.execute(
                    select(Issue)
                    .where(
                        and_(
                            Issue.author_id == user_id,
                            Issue.issue_type == "PULL_REQUEST",
                        )
                    )
                    .order_by(desc(Issue.created_at))
                    .limit(limit)
                ).scalars()
            )

    @staticmethod
    def get_issues_by_ids(issue_ids: List[int]) -> List[Issue]:
        """Get issues by their IDs (not pull requests)."""
        with DatabaseSession() as session:
            return list(
                session.execute(
                    select(Issue)
                    .where(
                        and_(
                            Issue.id.in_(issue_ids),
                            Issue.has_pull_request == False
                        )
                    )
                    .order_by(desc(Issue.created_at))
                ).scalars()
            )

    @staticmethod
    def get_pull_requests_by_ids(pr_ids: List[int]) -> List[Issue]:
        """Get pull requests by their IDs."""
        with DatabaseSession() as session:
            return list(
                session.execute(
                    select(Issue)
                    .where(
                        and_(
                            Issue.id.in_(pr_ids),
                            Issue.has_pull_request == True
                        )
                    )
                    .order_by(desc(Issue.created_at))
                ).scalars()
            )

    @staticmethod
    def get_pull_request_bad_practices(pr_id: int) -> List[Dict[str, Any]]:
        """Get bad practices detected for a specific pull request."""
        with DatabaseSession() as session:
            # Get the most recent detection for this PR
            latest_detection = session.execute(
                select(BadPracticeDetection)
                .where(BadPracticeDetection.pullrequest_id == pr_id)
                .order_by(desc(BadPracticeDetection.detection_time))
                .limit(1)
            ).scalar_one_or_none()
            
            if not latest_detection:
                return []
            
            # Get bad practices for this detection
            bad_practices = session.execute(
                select(Pullrequestbadpractice)
                .where(Pullrequestbadpractice.bad_practice_detection_id == latest_detection.id)
                .order_by(Pullrequestbadpractice.detection_time)
            ).scalars().all()
            
            result = []
            for bp in bad_practices:
                result.append({
                    "id": bp.id,
                    "title": bp.title,
                    "description": bp.description,
                    "state": bp.state,
                    "user_state": bp.user_state,
                    "detection_time": bp.detection_time.isoformat() if bp.detection_time else None,
                    "last_update_time": bp.last_update_time.isoformat() if bp.last_update_time else None,
                    "detection_trace_id": bp.detection_trace_id,
                })
            
            return result

    @staticmethod
    def get_user_by_id(user_id: int) -> Optional[User]:
        """Get a user by ID."""
        with DatabaseSession() as session:
            return session.execute(
                select(User).where(User.id == user_id)
            ).scalar_one_or_none()

    @staticmethod
    def get_repository_by_id(repository_id: int) -> Optional[Repository]:
        """Get a repository by ID."""
        with DatabaseSession() as session:
            return session.execute(
                select(Repository).where(Repository.id == repository_id)
            ).scalar_one_or_none()
