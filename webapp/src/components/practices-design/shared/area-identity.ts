import {
	BookOpen,
	Braces,
	CircleDot,
	FlaskConical,
	Gauge,
	GitCommitHorizontal,
	GitPullRequestArrow,
	LifeBuoy,
	type LucideIcon,
	MessageSquareText,
	Package,
	ShieldCheck,
	Users,
} from "lucide-react";
import type { PracticeAreaId } from "./types";

export interface AreaIdentity {
	id: PracticeAreaId;
	name: string;
	icon: LucideIcon;
	/** Foreground tone for the area icon, from the app's chart token ramp. */
	iconClassName: string;
	/** Soft background tint matching the icon tone, for chips and card headers. */
	tintClassName: string;
}

/**
 * Visual identity per practice area: one icon, one hue. All three candidates share this map
 * so an area looks the same everywhere and never needs a paragraph to introduce itself.
 * Hues cycle through the five chart tokens so both themes stay cohesive with the rest of
 * the app; the icon is the primary identity, the hue is a secondary grouping cue.
 */
export const PRACTICE_AREAS: readonly AreaIdentity[] = [
	{
		id: "constructive-code-review",
		name: "Code review",
		icon: MessageSquareText,
		iconClassName: "text-chart-1",
		tintClassName: "bg-chart-1/10",
	},
	{
		id: "testing",
		name: "Testing",
		icon: FlaskConical,
		iconClassName: "text-chart-2",
		tintClassName: "bg-chart-2/10",
	},
	{
		id: "security",
		name: "Security",
		icon: ShieldCheck,
		iconClassName: "text-chart-3",
		tintClassName: "bg-chart-3/10",
	},
	{
		id: "error-handling",
		name: "Error handling",
		icon: LifeBuoy,
		iconClassName: "text-chart-4",
		tintClassName: "bg-chart-4/10",
	},
	{
		id: "documentation",
		name: "Documentation",
		icon: BookOpen,
		iconClassName: "text-chart-5",
		tintClassName: "bg-chart-5/10",
	},
	{
		id: "issue-craft",
		name: "Issue craft",
		icon: CircleDot,
		iconClassName: "text-chart-1",
		tintClassName: "bg-chart-1/10",
	},
	{
		id: "commit-hygiene",
		name: "Commit hygiene",
		icon: GitCommitHorizontal,
		iconClassName: "text-chart-2",
		tintClassName: "bg-chart-2/10",
	},
	{
		id: "pr-craft",
		name: "Pull request craft",
		icon: GitPullRequestArrow,
		iconClassName: "text-chart-3",
		tintClassName: "bg-chart-3/10",
	},
	{
		id: "collaboration",
		name: "Collaboration",
		icon: Users,
		iconClassName: "text-chart-4",
		tintClassName: "bg-chart-4/10",
	},
	{
		id: "code-clarity",
		name: "Code clarity",
		icon: Braces,
		iconClassName: "text-chart-5",
		tintClassName: "bg-chart-5/10",
	},
	{
		id: "performance-awareness",
		name: "Performance",
		icon: Gauge,
		iconClassName: "text-chart-1",
		tintClassName: "bg-chart-1/10",
	},
	{
		id: "dependency-care",
		name: "Dependency care",
		icon: Package,
		iconClassName: "text-chart-2",
		tintClassName: "bg-chart-2/10",
	},
];

const AREA_BY_ID = new Map(PRACTICE_AREAS.map((area) => [area.id, area]));

export function getArea(id: PracticeAreaId): AreaIdentity {
	const area = AREA_BY_ID.get(id);
	if (!area) throw new Error(`Unknown practice area: ${id}`);
	return area;
}
