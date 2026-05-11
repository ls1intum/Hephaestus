// Mentor tools: factories, definitions, registry, merger, context.
// Each *.tool.ts is the single source of truth for its metadata; merger.ts overlays
// prompt-config descriptions onto local executors.

export { createGetActivitySummaryTool } from "./activity-summary.tool";
export { createGetAssignedWorkTool } from "./assigned-work.tool";
export { createDocumentTool } from "./document-create.tool";
export { updateDocumentTool } from "./document-update.tool";
export { createGetDocumentsTool } from "./documents.tool";
export { createGetFeedbackReceivedTool } from "./feedback.tool";
export { createGetIssuesTool } from "./issues.tool";
export { createGetPullRequestsTool } from "./pull-requests.tool";
export { createGetReviewsGivenTool } from "./reviews.tool";
export { createGetSessionHistoryTool } from "./session.tool";

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

export const mentorToolDefinitions = [
	getActivitySummaryDefinition,
	getPullRequestsDefinition,
	getIssuesDefinition,
	getAssignedWorkDefinition,
	getFeedbackReceivedDefinition,
	getReviewsGivenDefinition,
	getSessionHistoryDefinition,
	getDocumentsDefinition,
	createDocumentDefinition,
	updateDocumentDefinition,
] as const;

export { buildIssueUrl, buildPrUrl, getWorkspaceRepoIds, type ToolContext } from "./context";
export { extractToolConfig, type MergedToolConfig, overrideToolDescriptions } from "./merger";
export {
	ACTIVITY_TOOL_NAMES,
	type ActivityToolName,
	type ActivityTools,
	createActivityTools,
} from "./registry";
