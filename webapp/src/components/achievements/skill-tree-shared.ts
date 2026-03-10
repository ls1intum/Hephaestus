import type { EdgeTypes, NodeTypes } from "@xyflow/react";
import { AchievementEdge } from "@/components/achievements/AchievementEdge";
import { AchievementNode } from "@/components/achievements/AchievementNode";
import { AvatarNode } from "@/components/achievements/AvatarNode";
import { CategoryLabelNode } from "@/components/achievements/CategoryLabels";
import { EqualizerEdge } from "@/components/achievements/EqualizerEdge";
import { SynthwaveEdge } from "@/components/achievements/SynthwaveEdge";

/** Shared React Flow node type registry for the skill tree. */
export const nodeTypes: NodeTypes = {
	achievement: AchievementNode,
	avatar: AvatarNode,
	categoryLabel: CategoryLabelNode,
};

/** Shared React Flow edge type registry for the skill tree. */
export const edgeTypes: EdgeTypes = {
	achievement: AchievementEdge,
	synthwave: SynthwaveEdge,
	equalizer: EqualizerEdge,
};

/** Node origin: [0.5, 0.5] treats position as node center, matching the designer. */
export const NODE_ORIGIN: [number, number] = [0.5, 0.5];

/** Shared style for invisible centered Handles (used for both source and target). */
export const CENTERED_HANDLE_STYLE = {
	top: "50%",
	bottom: "auto",
	left: "50%",
	transform: "translate(-50%, -50%)",
} as const satisfies React.CSSProperties;

/** Theme subscription for useSyncExternalStore — watches both class toggle and prefers-color-scheme. */
export function subscribeToTheme(callback: () => void) {
	const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
	const observer = new MutationObserver(callback);
	observer.observe(document.documentElement, {
		attributes: true,
		attributeFilter: ["class"],
	});
	mediaQuery.addEventListener("change", callback);
	return () => {
		mediaQuery.removeEventListener("change", callback);
		observer.disconnect();
	};
}

/** Snapshot function for useSyncExternalStore — reads dark mode class. */
export function getIsDarkMode() {
	return document.documentElement.classList.contains("dark");
}
