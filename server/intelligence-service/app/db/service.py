"""
Database service for the Hephaestus intelligence service.

Simple service for accessing issue data from the application-server database.
"""

from typing import List, Optional
from sqlalchemy import select, desc

from .models_gen import Issue, User, Repository
from .config import DatabaseSession


class IssueDatabaseService:
    """Simple database service for accessing issue data."""

    @staticmethod
    def get_issues_assigned_to_user(user_id: int, limit: int = 50) -> List[Issue]:
        """Get issues assigned to a specific user, ordered by creation time."""
        with DatabaseSession() as session:
            return list(
                session.execute(
                    select(Issue)
                    .where(Issue.author_id == user_id)
                    .order_by(desc(Issue.created_at))
                    .limit(limit)
                ).scalars()
            )

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
