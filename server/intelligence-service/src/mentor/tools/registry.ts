// Activity-tool registry — bundles read-only mentor tools with ToolContext injection.
// Document write tools live in the chat handler (they need dataStream, not just ToolContext).

import { createGetActivitySummaryTool } from "./activity-summary.tool";
import { createGetAssignedWorkTool } from "./assigned-work.tool";
import type { ToolContext } from "./context";
import { createGetDocumentsTool } from "./documents.tool";
import { createGetFeedbackReceivedTool } from "./feedback.tool";
import { createGetIssuesTool } from "./issues.tool";
import { createGetPullRequestsTool } from "./pull-requests.tool";
import { createGetReviewsGivenTool } from "./reviews.tool";
import { createGetSessionHistoryTool } from "./session.tool";

/** Read-only activity tools, parallel-safe, ToolContext-injected. */
export function createActivityTools(ctx: ToolContext) {
	return {
		getActivitySummary: createGetActivitySummaryTool(ctx),
		getPullRequests: createGetPullRequestsTool(ctx),
		getIssues: createGetIssuesTool(ctx),
		getAssignedWork: createGetAssignedWorkTool(ctx),
		getFeedbackReceived: createGetFeedbackReceivedTool(ctx),
		getReviewsGiven: createGetReviewsGivenTool(ctx),
		getSessionHistory: createGetSessionHistoryTool(ctx),
		getDocuments: createGetDocumentsTool(ctx),
	} as const;
}

export type ActivityTools = ReturnType<typeof createActivityTools>;

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
