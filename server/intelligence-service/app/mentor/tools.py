import requests
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
def get_user_issues(state: Annotated[MentorState, InjectedState]) -> List[Dict[str, Any]]:
    """Get issues assigned to the user."""
    logger.debug("Fetching issues assigned to user with ID: %s", state.get("user_id"))
    try:
        user_id = state.get("user_id")
        if not user_id:
            logger.error("User ID not found in state.")
            return []
        limit = state.get("limit", 50)
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
                "has_pull_request": issue.has_pull_request,
                "repository_id": issue.repository_id,
                "issue_type": issue.issue_type,
            }

            # Add pull request specific fields if it's a pull request
            if issue.has_pull_request:
                formatted_issue.update(
                    {
                        "additions": issue.additions,
                        "deletions": issue.deletions,
                        "changed_files": issue.changed_files,
                        "commits": issue.commits,
                        "is_draft": issue.is_draft,
                        "is_mergeable": issue.is_mergeable,
                        "is_merged": issue.is_merged,
                        "merged_at": issue.merged_at.isoformat() if issue.merged_at else None,
                        "mergeable_state": issue.mergeable_state,
                    }
                )

            formatted_issues.append(formatted_issue)

        return formatted_issues

    except Exception as e:
        logger.error(f"Error fetching user issues: {e}")
        return []
