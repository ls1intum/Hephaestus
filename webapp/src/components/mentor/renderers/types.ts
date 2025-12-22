import type React from "react";
import type { ChatMessage, ChatTools } from "@/lib/types";
export type ToolKey = keyof ChatTools;
export type ToolType<T extends ToolKey = ToolKey> = `tool-${T}`;

/**
 * All possible tool part states in AI SDK v6.
 * - 'input-streaming': Tool input is being streamed
 * - 'input-available': Tool input is complete, waiting for execution
 * - 'approval-requested': Tool needs user approval
 * - 'approval-responded': User responded to approval
 * - 'output-available': Tool has completed with output
 * - 'output-error': Tool execution failed
 * - 'output-denied': Tool execution was denied
 */
export type ToolPartState =
	| "input-streaming"
	| "input-available"
	| "approval-requested"
	| "approval-responded"
	| "output-available"
	| "output-error"
	| "output-denied";

export type ToolPart<T extends ToolKey = ToolKey> = {
	type: ToolType<T>;
	toolCallId: string;
	state: ToolPartState;
	input?: ChatTools[T]["input"];
	output?: ChatTools[T]["output"];
	errorText?: string;
};

export type PartRendererProps<T extends ToolKey = ToolKey> = {
	message: ChatMessage;
	part: ToolPart<T>;
	/** Layout variant for different contexts */
	variant?: "default" | "artifact";
};

export type PartRenderer<T extends ToolKey = ToolKey> = (
	props: PartRendererProps<T>,
) => React.ReactElement | null;

export type PartRendererMap = Partial<{
	[K in ToolKey as ToolType<K>]: PartRenderer<K>;
}>;
