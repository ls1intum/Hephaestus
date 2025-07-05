import requests
import json
from typing import Annotated, List, Dict, Any
from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.logger import logger
from app.db.service import IssueDatabaseService
from app.mentor.state import MentorState

logger = logger.getChild(__name__)


@tool
def get_weather(latitude: float, longitude: float) -> dict:
    """Get the current weather at a specific location.

    Args:
        latitude: The latitude coordinate of the location
        longitude: The longitude coordinate of the location

    Returns:
        A dictionary containing current weather information including temperature,
        humidity, wind speed, and weather conditions.
    """
    try:
        url = f"https://api.open-meteo.com/v1/forecast?latitude={latitude}&longitude={longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,relative_humidity_2m,precipitation_probability&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

        response = requests.get(url, timeout=10)
        response.raise_for_status()
        data = response.json()

        # Extract and format the most relevant information
        current = data.get("current", {})
        daily = data.get("daily", {})

        formatted_weather = {
            "location": {
                "latitude": latitude,
                "longitude": longitude,
                "timezone": data.get("timezone", "Unknown"),
            },
            "current": {
                "temperature": current.get("temperature_2m"),
                "temperature_unit": data.get("current_units", {}).get(
                    "temperature_2m", "°C"
                ),
                "feels_like": current.get("apparent_temperature"),
                "humidity": current.get("relative_humidity_2m"),
                "wind_speed": current.get("wind_speed_10m"),
                "wind_direction": current.get("wind_direction_10m"),
                "pressure": current.get("pressure_msl"),
                "cloud_cover": current.get("cloud_cover"),
                "precipitation": current.get("precipitation", 0),
                "weather_code": current.get("weather_code"),
                "is_day": current.get("is_day", 1) == 1,
            },
            "daily": {
                "sunrise": daily.get("sunrise", [None])[0],
                "sunset": daily.get("sunset", [None])[0],
                "temperature_max": daily.get("temperature_2m_max", [None])[0],
                "temperature_min": daily.get("temperature_2m_min", [None])[0],
            },
            "timestamp": current.get("time"),
        }

        return formatted_weather

    except requests.RequestException as e:
        logger.error(f"Error fetching weather data: {e}")
        return {"error": f"Failed to fetch weather data: {str(e)}"}
    except Exception as e:
        logger.error(f"Unexpected error in get_weather: {e}")
        return {"error": f"Unexpected error: {str(e)}"}


@tool
def get_issues(state: Annotated[MentorState, InjectedState], limit: int = 20) -> List[Dict[str, Any]]:
    """Get issues (not pull requests) assigned to the user.
    
    Args:
        limit: Maximum number of issues to return (default: 20, max: 50)
    
    Returns:
        List of issues with their details.
    """
    logger.debug("Fetching issues assigned to user with ID: %s", state.get("user_id"))
    try:
        user_id = state.get("user_id")
        if not user_id:
            logger.error("User ID not found in state.")
            return []
            
        limit = min(limit, 50)  # Cap at 50 for performance
        issues = IssueDatabaseService.get_issues_assigned_to_user(user_id, limit)
        logger.debug("Retrieved %d issues from database", len(issues))

        formatted_issues = []
        for issue in issues:
            formatted_issue = {
                "id": issue.id,
                "number": issue.number,
                "title": issue.title,
                "state": issue.state,
                "body": issue.body,
                "html_url": issue.html_url,
                "created_at": issue.created_at.isoformat() if issue.created_at else None,
                "updated_at": issue.updated_at.isoformat() if issue.updated_at else None,
                "closed_at": issue.closed_at.isoformat() if issue.closed_at else None,
                "comments_count": issue.comments_count,
                "is_locked": issue.is_locked,
                "repository_id": issue.repository_id,
                "issue_type": issue.issue_type,
            }
            formatted_issues.append(formatted_issue)

        return formatted_issues

    except Exception as e:
        logger.error(f"Error fetching user issues: {e}")
        return []


@tool
def get_pull_requests(state: Annotated[MentorState, InjectedState], limit: int = 20) -> List[Dict[str, Any]]:
    """Get pull requests assigned to the user.
    
    Args:
        limit: Maximum number of pull requests to return (default: 20, max: 50)
    
    Returns:
        List of pull requests with their details.
    """
    logger.debug("Fetching pull requests assigned to user with ID: %s", state.get("user_id"))
    try:
        user_id = state.get("user_id")
        if not user_id:
            logger.error("User ID not found in state.")
            return []
            
        limit = min(limit, 50)  # Cap at 50 for performance
        pull_requests = IssueDatabaseService.get_pull_requests_assigned_to_user(user_id, limit)
        logger.debug("Retrieved %d pull requests from database", len(pull_requests))

        formatted_prs = []
        for pr in pull_requests:
            formatted_pr = {
                "id": pr.id,
                "number": pr.number,
                "title": pr.title,
                "state": pr.state,
                "body": pr.body,
                "html_url": pr.html_url,
                "created_at": pr.created_at.isoformat() if pr.created_at else None,
                "updated_at": pr.updated_at.isoformat() if pr.updated_at else None,
                "closed_at": pr.closed_at.isoformat() if pr.closed_at else None,
                "merged_at": pr.merged_at.isoformat() if pr.merged_at else None,
                "comments_count": pr.comments_count,
                "is_locked": pr.is_locked,
                "repository_id": pr.repository_id,
                "additions": pr.additions,
                "deletions": pr.deletions,
                "changed_files": pr.changed_files,
                "commits": pr.commits,
                "is_draft": pr.is_draft,
                "is_mergeable": pr.is_mergeable,
                "is_merged": pr.is_merged,
                "mergeable_state": pr.mergeable_state,
                "bad_practice_summary": pr.bad_practice_summary,
                "last_detection_time": pr.last_detection_time.isoformat() if pr.last_detection_time else None,
            }
            formatted_prs.append(formatted_pr)

        return formatted_prs

    except Exception as e:
        logger.error(f"Error fetching user pull requests: {e}")
        return []


@tool
def get_issue_details(issue_ids: List[int]) -> List[Dict[str, Any]]:
    """Get detailed information for specific issues by their IDs.
    
    Args:
        issue_ids: List of issue IDs to retrieve details for
    
    Returns:
        List of issues with detailed information.
    """
    logger.debug("Fetching details for issue IDs: %s", issue_ids)
    try:
        if not issue_ids:
            return []
            
        # Limit to reasonable batch size
        issue_ids = issue_ids[:20]
        issues = IssueDatabaseService.get_issues_by_ids(issue_ids)
        logger.debug("Retrieved %d issues from database", len(issues))

        formatted_issues = []
        for issue in issues:
            formatted_issue = {
                "id": issue.id,
                "number": issue.number,
                "title": issue.title,
                "state": issue.state,
                "body": issue.body,
                "html_url": issue.html_url,
                "created_at": issue.created_at.isoformat() if issue.created_at else None,
                "updated_at": issue.updated_at.isoformat() if issue.updated_at else None,
                "closed_at": issue.closed_at.isoformat() if issue.closed_at else None,
                "comments_count": issue.comments_count,
                "is_locked": issue.is_locked,
                "repository_id": issue.repository_id,
                "issue_type": issue.issue_type,
            }
            formatted_issues.append(formatted_issue)

        return formatted_issues

    except Exception as e:
        logger.error(f"Error fetching issue details: {e}")
        return []


@tool
def get_pull_request_details(pr_ids: List[int]) -> List[Dict[str, Any]]:
    """Get detailed information for specific pull requests by their IDs.
    
    Args:
        pr_ids: List of pull request IDs to retrieve details for
    
    Returns:
        List of pull requests with detailed information.
    """
    logger.debug("Fetching details for pull request IDs: %s", pr_ids)
    try:
        if not pr_ids:
            return []
            
        # Limit to reasonable batch size
        pr_ids = pr_ids[:20]
        pull_requests = IssueDatabaseService.get_pull_requests_by_ids(pr_ids)
        logger.debug("Retrieved %d pull requests from database", len(pull_requests))

        formatted_prs = []
        for pr in pull_requests:
            formatted_pr = {
                "id": pr.id,
                "number": pr.number,
                "title": pr.title,
                "state": pr.state,
                "body": pr.body,
                "html_url": pr.html_url,
                "created_at": pr.created_at.isoformat() if pr.created_at else None,
                "updated_at": pr.updated_at.isoformat() if pr.updated_at else None,
                "closed_at": pr.closed_at.isoformat() if pr.closed_at else None,
                "merged_at": pr.merged_at.isoformat() if pr.merged_at else None,
                "comments_count": pr.comments_count,
                "is_locked": pr.is_locked,
                "repository_id": pr.repository_id,
                "additions": pr.additions,
                "deletions": pr.deletions,
                "changed_files": pr.changed_files,
                "commits": pr.commits,
                "is_draft": pr.is_draft,
                "is_mergeable": pr.is_mergeable,
                "is_merged": pr.is_merged,
                "mergeable_state": pr.mergeable_state,
                "bad_practice_summary": pr.bad_practice_summary,
                "last_detection_time": pr.last_detection_time.isoformat() if pr.last_detection_time else None,
            }
            formatted_prs.append(formatted_pr)

        return formatted_prs

    except Exception as e:
        logger.error(f"Error fetching pull request details: {e}")
        return []


@tool
def get_pull_request_bad_practices(pr_id: int) -> Dict[str, Any]:
    """Get bad practices detected for a specific pull request.
    
    Args:
        pr_id: The ID of the pull request
    
    Returns:
        Dictionary containing bad practices information including summary and individual practices.
    """
    logger.debug("Fetching bad practices for pull request ID: %s", pr_id)
    try:
        bad_practices = IssueDatabaseService.get_pull_request_bad_practices(pr_id)
        
        # Get PR details for context
        pr_details = IssueDatabaseService.get_pull_requests_by_ids([pr_id])
        pr_summary = ""
        if pr_details:
            pr_summary = pr_details[0].bad_practice_summary or ""
        
        logger.debug("Retrieved %d bad practices for PR %d", len(bad_practices), pr_id)
        
        return {
            "pull_request_id": pr_id,
            "summary": pr_summary,
            "bad_practices": bad_practices,
            "total_count": len(bad_practices),
        }

    except Exception as e:
        logger.error(f"Error fetching bad practices for PR {pr_id}: {e}")
        return {
            "pull_request_id": pr_id,
            "summary": "",
            "bad_practices": [],
            "total_count": 0,
            "error": str(e)
        }
