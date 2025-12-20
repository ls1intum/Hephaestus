/**
 * Mentor Tools - Public API
 *
 * Re-exports all mentor tool factories for clean imports.
 * Tools are split by domain for maintainability (SOLID - Single Responsibility).
 *
 * @module
 */

// ─────────────────────────────────────────────────────────────────────────────
// Tool Registry (preferred for creating all tools at once)
// ─────────────────────────────────────────────────────────────────────────────
export {
	ACTIVITY_TOOL_NAMES,
	type ActivityToolName,
	type ActivityTools,
	createActivityTools,
} from "./registry";

// ─────────────────────────────────────────────────────────────────────────────
// Individual Tool Factories (for granular control)
// ─────────────────────────────────────────────────────────────────────────────

// Activity & Overview
export { createGetActivitySummaryTool } from "./activity-summary.tool";
export { createGetAssignedWorkTool } from "./assigned-work.tool";

// Context type for all tools
export {
	buildIssueUrl,
	buildPrUrl,
	buildReviewUrl,
	getWorkspaceRepoIds,
	type ToolContext,
} from "./context";

// Documents
export { createGetDocumentsTool } from "./documents.tool";

// Reviews & Feedback
export { createGetFeedbackReceivedTool } from "./feedback.tool";

// Issues
export { createGetIssuesTool } from "./issues.tool";

// Work Items
export { createGetPullRequestsTool } from "./pull-requests.tool";
export { createGetReviewsGivenTool } from "./reviews.tool";

// Session Management
export { createGetSessionHistoryTool } from "./session.tool";
