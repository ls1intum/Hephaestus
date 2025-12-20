/**
 * Tool Registry
 *
 * Centralized tool creation for the mentor agent.
 * This eliminates duplication between the chat handler and any future agents.
 *
 * Benefits:
 * 1. Single source of truth for tool configuration
 * 2. Consistent tool naming across the codebase
 * 3. Easy to add/remove tools
 * 4. Type-safe tool context injection
 */

import { createGetActivitySummaryTool } from "./activity-summary.tool";
import { createGetAssignedWorkTool } from "./assigned-work.tool";
import type { ToolContext } from "./context";
import { createGetDocumentsTool } from "./documents.tool";
import { createGetFeedbackReceivedTool } from "./feedback.tool";
import { createGetIssuesTool } from "./issues.tool";
import { createGetPullRequestsTool } from "./pull-requests.tool";
import { createGetReviewsGivenTool } from "./reviews.tool";
import { createGetSessionHistoryTool } from "./session.tool";

/**
 * Activity tools for the mentor - all parallel-safe, user context auto-injected.
 *
 * Tool categories:
 * - Overview: getActivitySummary (call first for context)
 * - Work Items: getPullRequests, getIssues
 * - Responsibilities: getAssignedWork
 * - Feedback: getFeedbackReceived, getReviewsGiven
 * - Continuity: getSessionHistory, getDocuments
 */
export function createActivityTools(ctx: ToolContext) {
	return {
		// High-level overview (call first)
		getActivitySummary: createGetActivitySummaryTool(ctx),

		// Detailed data retrieval
		getPullRequests: createGetPullRequestsTool(ctx),
		getIssues: createGetIssuesTool(ctx),

		// Assigned work & responsibilities (Forethought phase)
		getAssignedWork: createGetAssignedWorkTool(ctx),

		// Feedback & reviews (Reflection phase)
		getFeedbackReceived: createGetFeedbackReceivedTool(ctx),
		getReviewsGiven: createGetReviewsGivenTool(ctx),

		// Session continuity (Self-Regulation phase)
		getSessionHistory: createGetSessionHistoryTool(ctx),
		getDocuments: createGetDocumentsTool(ctx),
	} as const;
}

/**
 * Type representing all activity tools.
 * Use this for type inference in handlers.
 */
export type ActivityTools = ReturnType<typeof createActivityTools>;

/**
 * Tool names for external reference (e.g., tests, documentation).
 */
export const ACTIVITY_TOOL_NAMES = [
	"getActivitySummary",
	"getPullRequests",
	"getIssues",
	"getAssignedWork",
	"getFeedbackReceived",
	"getReviewsGiven",
	"getSessionHistory",
	"getDocuments",
] as const;

export type ActivityToolName = (typeof ACTIVITY_TOOL_NAMES)[number];
