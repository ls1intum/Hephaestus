import type React from "react";

/**
 * Kept out of `skill-tree-shared.ts` because that registry imports the node components — a node
 * importing it back would close the cycle and leave `nodeTypes` holding `undefined`.
 */
export const CENTERED_HANDLE_STYLE = {
	top: "50%",
	bottom: "auto",
	left: "50%",
	transform: "translate(-50%, -50%)",
} as const satisfies React.CSSProperties;
