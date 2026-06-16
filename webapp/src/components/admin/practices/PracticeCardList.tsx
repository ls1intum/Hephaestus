import { Link } from "@tanstack/react-router";
import { ClipboardList } from "lucide-react";
import type { Practice, PracticeArea } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { PracticeCard } from "./PracticeCard";

interface PracticeCardListProps {
	workspaceSlug: string;
	practices: Practice[];
	goals: PracticeArea[];
	isLoading: boolean;
	togglingPractices: Set<string>;
	onDelete: (practice: Practice) => void;
	onSetActive: (slug: string, active: boolean) => void;
}

/** Active practices first, then alphabetical — applied within each goal section. */
function sortPractices(practices: Practice[]): Practice[] {
	return [...practices].sort((a, b) => {
		if (a.active !== b.active) return a.active ? -1 : 1;
		return a.name.localeCompare(b.name);
	});
}

export function PracticeCardList({
	workspaceSlug,
	practices,
	goals,
	isLoading,
	togglingPractices,
	onDelete,
	onSetActive,
}: PracticeCardListProps) {
	if (isLoading) {
		return (
			<div
				className="flex flex-col items-center justify-center h-64 space-y-2"
				role="status"
				aria-label="Loading practices"
			>
				<Spinner className="h-8 w-8" />
				<p className="text-sm text-muted-foreground">Loading practices...</p>
			</div>
		);
	}

	if (practices.length === 0) {
		return (
			<div className="flex flex-col items-center justify-center h-64 space-y-3">
				<ClipboardList className="h-10 w-10 text-muted-foreground" aria-hidden="true" />
				<div className="text-center">
					<p className="text-sm font-medium">No practices configured</p>
					<p className="text-xs text-muted-foreground mt-1">
						Get started by creating your first practice definition
					</p>
				</div>
				<Button
					variant="outline"
					size="sm"
					render={
						<Link
							to="/w/$workspaceSlug/admin/ai/practice-detection/catalog/new"
							params={{ workspaceSlug }}
						/>
					}
					className="mt-2"
				>
					Create Practice
				</Button>
			</div>
		);
	}

	// Group practices by goal so the catalog reads by learning objective, not as a flat wall.
	const byGoal = new Map<string, Practice[]>();
	for (const practice of practices) {
		const key = practice.goalSlug ?? "__unassigned__";
		const bucket = byGoal.get(key);
		if (bucket) bucket.push(practice);
		else byGoal.set(key, [practice]);
	}

	const goalSections = [...goals]
		.sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name))
		.map((goal) => ({
			key: goal.slug,
			title: goal.name,
			items: sortPractices(byGoal.get(goal.slug) ?? []),
		}))
		.filter((section) => section.items.length > 0);

	const unassigned = sortPractices(byGoal.get("__unassigned__") ?? []);
	const sections = [
		...goalSections,
		...(unassigned.length > 0
			? [{ key: "__unassigned__", title: "Unassigned", items: unassigned }]
			: []),
	];

	return (
		<div className="space-y-8">
			{sections.map((section) => (
				<section key={section.key} className="space-y-3">
					<div className="flex items-baseline gap-2 border-b pb-1.5">
						<h2 className="text-sm font-semibold tracking-tight">{section.title}</h2>
						<span className="text-xs text-muted-foreground">{section.items.length}</span>
					</div>
					{section.items.map((practice) => (
						<PracticeCard
							key={practice.slug}
							workspaceSlug={workspaceSlug}
							practice={practice}
							isToggling={togglingPractices.has(practice.slug)}
							onDelete={onDelete}
							onSetActive={onSetActive}
						/>
					))}
				</section>
			))}
			<p className="text-sm text-muted-foreground pt-1" role="status">
				{practices.length} practice{practices.length === 1 ? "" : "s"}
			</p>
		</div>
	);
}
