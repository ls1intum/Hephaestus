import asyncio
import requests
from uuid import uuid4
from typing import Annotated, List, Dict, Any, Literal
from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from langchain_core.runnables import RunnableConfig
from langchain.chat_models import init_chat_model
from app.settings import settings

from app.logger import logger
from app.db.service import IssueDatabaseService
from app.mentor.state import MentorState
from app.mentor.models import (
    CreateDocumentInput,
    UpdateDocumentInput,
    GetWeatherInput,
    CreateDocumentOutput,
    UpdateDocumentOutput,
    GetWeatherOutput,
)
from typing import Any, Optional
from langchain_core.runnables import RunnableConfig
from langchain_core.callbacks.manager import adispatch_custom_event


logger = logger.getChild(__name__)

model = init_chat_model(settings.MODEL_NAME, reasoning={"summary": "auto"})

# Helper function to emit data events
async def emit_data(
    *,
    config: RunnableConfig,
    type_name: str,
    data: Any,
    transient: bool = True,
    id: Optional[str] = None,
) -> None:
    """Emit a custom event named data-<type_name> with a standardized payload.

    Payload shape: {"data": <data>, "transient": <bool>, (optional) "id": <str>}
    """
    payload: dict[str, Any] = {"data": data, "transient": transient}
    # Careful, this will replace the data part of with the last id equal to id
    if id is not None:
        payload["id"] = id
    await adispatch_custom_event(f"data-{type_name}", payload, config=config)


@tool("getWeather", args_schema=GetWeatherInput)
def get_weather(latitude: float, longitude: float) -> Dict[str, Any]:
    """Get the current weather at a specific location.

    Args:
        latitude: The latitude coordinate of the location
        longitude: The longitude coordinate of the location
    """
    try:
        url = (
            "https://api.open-meteo.com/v1/forecast?"
            f"latitude={latitude}&longitude={longitude}"
            "&timeformat=iso8601"
            "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"
            "&hourly=temperature_2m"
            "&daily=sunrise,sunset"
            "&forecast_days=3"
            "&timezone=auto"
        )
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        data = response.json() or {}

        # Map selected fields to our output shape
        output = GetWeatherOutput(
            latitude=latitude,
            longitude=longitude,
            generationtime_ms=data.get("generationtime_ms"),
            utc_offset_seconds=data.get("utc_offset_seconds"),
            timezone=data.get("timezone"),
            timezone_abbreviation=data.get("timezone_abbreviation"),
            elevation=data.get("elevation"),
            current_units=data.get("current_units"),
            current=data.get("current"),
            hourly_units=data.get("hourly_units"),
            hourly=data.get("hourly"),
            daily_units=data.get("daily_units"),
            daily=data.get("daily"),
        )
        # Return a JSON string to ensure ToolMessage.content is valid JSON
        return output.model_dump(exclude_none=True)
    except requests.RequestException as e:
        logger.warning("getWeather: Weather API error: %s", e)
        # Return an empty JSON object string on error
        return {}
    except Exception as e:
        logger.exception("getWeather: unexpected error: %s", e)
        return {}


@tool("createDocument", args_schema=CreateDocumentInput)
async def create_document(
    title: str,
    kind: Literal["text"],
    *,
    config: RunnableConfig,
) -> Dict[str, Any]:
    """Create a document for a writing or content creation activities. This tool will call other functions that will generate the contents of the document based on the title and kind."""

    document_id = str(uuid4())

    await emit_data(config=config, type_name="kind", data=kind, transient=True)
    await emit_data(config=config, type_name="id", data=document_id, transient=True)
    await emit_data(config=config, type_name="title", data=title, transient=True)
    await emit_data(config=config, type_name="clear", data=None, transient=True)

    msgs = [
        {"role": "system", "content": "Write a short markdown intro for the doc."},
        {"role": "user", "content": f"Title: {title}"}
    ]
    async for chunk in model.astream(msgs, config=config):
        textDelta = chunk.text()
        if textDelta:
            await emit_data(config=config, type_name="textDelta", data=textDelta, transient=True)

    await emit_data(config=config, type_name="finish", data=None, transient=True)

    return {"id": document_id, "title": title, "kind": kind, "content": "A document was created and is now visible to the user."}


@tool("updateDocument", args_schema=UpdateDocumentInput)
async def update_document(
    id: str,
    title: str,
    content: str,
    kind: Literal["text"],
    *,
    config: RunnableConfig,
) -> Dict[str, Any]:
    """Signal update of a document. The app server performs the actual write.

    Returns an acknowledgement envelope; the server streams the concrete result.
    """
    try:
        # Clear preview (transient)
        await emit_data(config=config, type_name="clear", data=None, transient=True)

        # Ask inner LLM to propose updates; stream tokens will be forwarded via config
        prompt = (
            f"Update the {kind} document '{title}'. "
            "Generate revised content based on the user's edits and suggestions."
        )
        msgs = [
            {"role": "system", "content": "Generate concise markdown patch sections."},
            {"role": "user", "content": prompt},
        ]
        try:
            await _inner_llm.ainvoke(msgs, config=config)
        except Exception:
            logger.debug("Inner LLM call in updateDocument did not stream or failed; continuing")

        return {
            "accepted": True,
            "input": {"id": id, "title": title, "content": content, "kind": kind},
        }
    except Exception as e:
        logger.exception("update_document: unexpected error: %s", e)
        return {"accepted": False, "error": str(e)}


@tool
def get_issues(
    state: Annotated[MentorState, InjectedState], limit: int = 20
) -> List[Dict[str, Any]]:
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
                "created_at": (
                    issue.created_at.isoformat() if issue.created_at else None
                ),
                "updated_at": (
                    issue.updated_at.isoformat() if issue.updated_at else None
                ),
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
def get_pull_requests(
    state: Annotated[MentorState, InjectedState], limit: int = 20
) -> List[Dict[str, Any]]:
    """Get pull requests assigned to the user.

    Args:
        limit: Maximum number of pull requests to return (default: 20, max: 50)

    Returns:
        List of pull requests with their details.
    """
    logger.debug(
        "Fetching pull requests assigned to user with ID: %s", state.get("user_id")
    )
    try:
        user_id = state.get("user_id")
        if not user_id:
            logger.error("User ID not found in state.")
            return []

        limit = min(limit, 50)  # Cap at 50 for performance
        pull_requests = IssueDatabaseService.get_pull_requests_assigned_to_user(
            user_id, limit
        )
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
                "last_detection_time": (
                    pr.last_detection_time.isoformat()
                    if pr.last_detection_time
                    else None
                ),
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
                "created_at": (
                    issue.created_at.isoformat() if issue.created_at else None
                ),
                "updated_at": (
                    issue.updated_at.isoformat() if issue.updated_at else None
                ),
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
                "last_detection_time": (
                    pr.last_detection_time.isoformat()
                    if pr.last_detection_time
                    else None
                ),
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
            "error": str(e),
        }
