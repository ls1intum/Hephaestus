/**
 * Mentor Tools - Public API
 *
 * Re-exports all mentor tool factories and their Langfuse definitions.
 * Each tool file is the single source of truth for its metadata.
 *
 * Architecture:
 * - Each *.tool.ts exports BOTH the factory AND the Langfuse definition
 * - No separate definitions file - colocation is key
 * - registry.ts creates read tools with context injection
 * - merger.ts merges Langfuse descriptions with local executors
 *
 * Tool Categories:
 * - Read tools (getXxx): Query data, use ToolContext
 * - Write tools (createXxx, updateXxx): Mutate data, need dataStream
 *
 * @module
 */

// ─────────────────────────────────────────────────────────────────────────────
// Tool Factories (create tools with context injection)
// ─────────────────────────────────────────────────────────────────────────────

// Read tools (use ToolContext)
export { createGetActivitySummaryTool } from "./activity-summary.tool";
export { createGetAssignedWorkTool } from "./assigned-work.tool";
// Write tools (need dataStream + workspaceId + userId)
export { createDocumentTool } from "./document-create.tool";
export { updateDocumentTool } from "./document-update.tool";
export { createGetDocumentsTool } from "./documents.tool";
export { createGetFeedbackReceivedTool } from "./feedback.tool";
export { createGetIssuesTool } from "./issues.tool";
export { createGetPullRequestsTool } from "./pull-requests.tool";
export { createGetReviewsGivenTool } from "./reviews.tool";
export { createGetSessionHistoryTool } from "./session.tool";

// ─────────────────────────────────────────────────────────────────────────────
// Langfuse Tool Definitions (colocated with each tool)
// ─────────────────────────────────────────────────────────────────────────────

import { getActivitySummaryDefinition } from "./activity-summary.tool";
import { getAssignedWorkDefinition } from "./assigned-work.tool";
import { createDocumentDefinition } from "./document-create.tool";
import { updateDocumentDefinition } from "./document-update.tool";
import { getDocumentsDefinition } from "./documents.tool";
import { getFeedbackReceivedDefinition } from "./feedback.tool";
import { getIssuesDefinition } from "./issues.tool";
import { getPullRequestsDefinition } from "./pull-requests.tool";
import { getReviewsGivenDefinition } from "./reviews.tool";
import { getSessionHistoryDefinition } from "./session.tool";

// Re-export all definitions for direct access
export {
	createDocumentDefinition,
	getActivitySummaryDefinition,
	getAssignedWorkDefinition,
	getDocumentsDefinition,
	getFeedbackReceivedDefinition,
	getIssuesDefinition,
	getPullRequestsDefinition,
	getReviewsGivenDefinition,
	getSessionHistoryDefinition,
	updateDocumentDefinition,
};

/**
 * All mentor tool definitions for Langfuse prompt config.
 * Order follows mentor workflow:
 * 1. Activity overview (start here)
 * 2. Work items (details)
 * 3. Responsibilities (Forethought)
 * 4. Feedback (Reflection)
 * 5. Session continuity
 * 6. Document management
 */
export const mentorToolDefinitions = [
	// Activity overview (call first)
	getActivitySummaryDefinition,
	// Work items
	getPullRequestsDefinition,
	getIssuesDefinition,
	// Responsibilities (Forethought phase)
	getAssignedWorkDefinition,
	// Feedback (Reflection phase)
	getFeedbackReceivedDefinition,
	getReviewsGivenDefinition,
	// Session continuity
	getSessionHistoryDefinition,
	getDocumentsDefinition,
	// Document management
	createDocumentDefinition,
	updateDocumentDefinition,
] as const;

// ─────────────────────────────────────────────────────────────────────────────
// Tool Merger (combines Langfuse descriptions with local executors)
// ─────────────────────────────────────────────────────────────────────────────

export {
	extractToolConfig,
	type MergedToolConfig,
	overrideToolDescriptions,
} from "./merger";

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
// Context (shared by all tools)
// ─────────────────────────────────────────────────────────────────────────────

export {
	buildIssueUrl,
	buildPrUrl,
	buildReviewUrl,
	getWorkspaceRepoIds,
	type ToolContext,
} from "./context";
