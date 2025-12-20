/**
 * Utilities for extracting and formatting conversation context from AI SDK messages.
 *
 * These utilities help document generation tools access the conversation history
 * including tool call results which contain the actual user activity data.
 */

import type { ModelMessage } from "ai";

/**
 * Maximum characters of conversation context to include.
 * This prevents context window blowouts on long conversations.
 * ~4 chars per token, so 12000 chars ≈ 3000 tokens - reasonable for context.
 */
const MAX_CONTEXT_CHARS = 12000;

// ─────────────────────────────────────────────────────────────────────────────
// Tool Output Summarizers
// Each tool gets its own simple summarizer function
// ─────────────────────────────────────────────────────────────────────────────

type ToolValue = Record<string, unknown>;

function summarizeActivitySummary(v: ToolValue): string | null {
	if (!v.thisWeek) {
		return null;
	}
	const week = v.thisWeek as Record<string, number>;
	const insights = v.insights as string[] | undefined;
	const parts: string[] = [];

	if (week.prsMerged) {
		parts.push(`${week.prsMerged} PRs merged`);
	}
	if (week.prsOpen) {
		parts.push(`${week.prsOpen} PRs open`);
	}
	if (week.reviewsGiven) {
		parts.push(`${week.reviewsGiven} reviews given`);
	}
	if (insights && insights.length > 0) {
		parts.push(`Insights: ${insights.slice(0, 2).join("; ")}`);
	}

	return parts.join(", ") || null;
}

function summarizePullRequests(v: ToolValue): string | null {
	if (!Array.isArray(v.pullRequests)) {
		return null;
	}
	const prs = v.pullRequests as Array<{ number: number; title: string; state: string }>;
	if (prs.length === 0) {
		return "No PRs found";
	}
	return prs
		.slice(0, 5)
		.map((pr) => `#${pr.number} ${pr.title} (${pr.state})`)
		.join("; ");
}

function summarizeIssues(v: ToolValue): string | null {
	if (!Array.isArray(v.issues)) {
		return null;
	}
	const issues = v.issues as Array<{ number: number; title: string }>;
	if (issues.length === 0) {
		return "No issues found";
	}
	return issues
		.slice(0, 5)
		.map((i) => `#${i.number} ${i.title}`)
		.join("; ");
}

function summarizeFeedbackReceived(v: ToolValue): string | null {
	if (!v.summary) {
		return null;
	}
	const summary = v.summary as Record<string, number>;
	const parts: string[] = [];
	if (summary.approved) {
		parts.push(`${summary.approved} approvals`);
	}
	if (summary.changesRequested) {
		parts.push(`${summary.changesRequested} change requests`);
	}
	if (summary.commented) {
		parts.push(`${summary.commented} comments`);
	}
	return parts.join(", ") || "No feedback data";
}

function summarizeAssignedWork(v: ToolValue): string | null {
	const parts: string[] = [];
	if (Array.isArray(v.assignedIssues) && v.assignedIssues.length > 0) {
		parts.push(`${v.assignedIssues.length} assigned issues`);
	}
	if (Array.isArray(v.pendingReviewRequests) && v.pendingReviewRequests.length > 0) {
		parts.push(`${v.pendingReviewRequests.length} pending review requests`);
	}
	return parts.join(", ") || null;
}

/** Map of tool names to their summarizer functions */
const TOOL_SUMMARIZERS: Record<string, (v: ToolValue) => string | null> = {
	getActivitySummary: summarizeActivitySummary,
	getPullRequests: summarizePullRequests,
	getIssues: summarizeIssues,
	getFeedbackReceived: summarizeFeedbackReceived,
	getAssignedWork: summarizeAssignedWork,
};

/**
 * Summarizes tool output for document context.
 * Uses dedicated summarizers per tool to keep complexity low.
 */
function summarizeToolOutput(toolName: string, value: unknown): string | null {
	if (!value || typeof value !== "object") {
		return null;
	}

	const summarizer = TOOL_SUMMARIZERS[toolName];
	if (summarizer) {
		return summarizer(value as ToolValue);
	}

	// Default: note that tool was called but don't dump data
	return "(data available)";
}

// ─────────────────────────────────────────────────────────────────────────────
// Message Content Extractors
// Split by role for lower complexity
// ─────────────────────────────────────────────────────────────────────────────

/** Extract text from an array of message parts */
function extractTextParts(content: Array<{ type: string; text?: string }>): string {
	return content
		.filter((part) => part.type === "text" && part.text)
		.map((part) => part.text as string)
		.join(" ");
}

/** Extract content from a user message */
function extractUserContent(msg: ModelMessage): string | null {
	if (typeof msg.content === "string") {
		return `**User:** ${msg.content}`;
	}
	if (Array.isArray(msg.content)) {
		const text = extractTextParts(msg.content as Array<{ type: string; text?: string }>);
		return text ? `**User:** ${text}` : null;
	}
	return null;
}

/** Extract content from an assistant message */
function extractAssistantContent(msg: ModelMessage): string | null {
	if (typeof msg.content === "string") {
		return `**Assistant:** ${msg.content}`;
	}
	if (!Array.isArray(msg.content)) {
		return null;
	}

	const parts: string[] = [];
	for (const part of msg.content) {
		if (part.type === "text") {
			const textPart = part as { type: "text"; text: string };
			parts.push(textPart.text);
		} else if (part.type === "tool-call") {
			const toolCall = part as { type: "tool-call"; toolName: string };
			parts.push(`[Called tool: ${toolCall.toolName}]`);
		}
	}

	const combined = parts.join(" ").trim();
	return combined ? `**Assistant:** ${combined}` : null;
}

/** Extract content from a tool result message */
function extractToolContent(msg: ModelMessage): string | null {
	if (!Array.isArray(msg.content)) {
		return null;
	}

	const results: string[] = [];
	for (const part of msg.content) {
		if (part.type !== "tool-result") {
			continue;
		}

		const toolResult = part as {
			type: "tool-result";
			toolName: string;
			output: { type: string; value: unknown };
		};

		const output = toolResult.output;
		if (output?.type === "json" && output.value) {
			const summary = summarizeToolOutput(toolResult.toolName, output.value);
			if (summary) {
				results.push(`**Tool Result (${toolResult.toolName}):** ${summary}`);
			}
		}
	}

	return results.length > 0 ? results.join("\n") : null;
}

/**
 * Extracts human-readable text from a model message, including tool results.
 * Delegates to role-specific extractors to keep complexity manageable.
 */
function extractMessageContent(msg: ModelMessage): string | null {
	switch (msg.role) {
		case "user":
			return extractUserContent(msg);
		case "assistant":
			return extractAssistantContent(msg);
		case "tool":
			return extractToolContent(msg);
		default:
			return null;
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats conversation messages into context for document generation.
 *
 * Key design decisions:
 * - Includes tool results (the actual activity data)
 * - Truncates to prevent context blowout
 * - Maintains conversation order for coherence
 * - Uses markdown formatting for clarity
 */
export function formatConversationForDocument(messages: ModelMessage[]): string {
	if (messages.length === 0) {
		return "";
	}

	const parts: string[] = [];
	let totalChars = 0;

	for (const msg of messages) {
		const content = extractMessageContent(msg);
		if (!content) {
			continue;
		}

		// Check if adding this would exceed limit
		if (totalChars + content.length > MAX_CONTEXT_CHARS) {
			parts.push("... (earlier conversation truncated for brevity)");
			break;
		}

		parts.push(content);
		totalChars += content.length;
	}

	return parts.join("\n\n");
}
