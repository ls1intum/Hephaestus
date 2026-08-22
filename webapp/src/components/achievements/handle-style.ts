import type React from "react";

/**
 * Shared style for the invisible centered Handles every skill-tree node renders, for both source and
 * target.
 *
 * It lives apart from `skill-tree-shared.ts` because that module is the node/edge type registry and
 * therefore imports the node components — a node importing this constant from there would close an
 * import cycle, and a registry evaluated mid-cycle is how `nodeTypes` ends up holding `undefined`.
 */
export const CENTERED_HANDLE_STYLE = {
	top: "50%",
	bottom: "auto",
	left: "50%",
	transform: "translate(-50%, -50%)",
} as const satisfies React.CSSProperties;
