import { ClipboardList } from "lucide-react";
import type { Practice } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { PracticeCard } from "./PracticeCard";

interface PracticeCardListProps {
	practices: Practice[];
	isLoading: boolean;
	togglingPractices: Set<string>;
	onEdit: (practice: Practice) => void;
	onDelete: (practice: Practice) => void;
	onSetActive: (slug: string, active: boolean) => void;
	onCreateClick: () => void;
}

export function PracticeCardList({
	practices,
	isLoading,
	togglingPractices,
	onEdit,
	onDelete,
	onSetActive,
	onCreateClick,
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
				<Button variant="outline" size="sm" onClick={onCreateClick} className="mt-2">
					Create Practice
				</Button>
			</div>
		);
	}

	// Sort: active first, then alphabetical within each group
	const sorted = [...practices].sort((a, b) => {
		if (a.active !== b.active) return a.active ? -1 : 1;
		return a.name.localeCompare(b.name);
	});

	return (
		<div className="space-y-3">
			{sorted.map((practice) => (
				<PracticeCard
					key={practice.slug}
					practice={practice}
					isToggling={togglingPractices.has(practice.slug)}
					onEdit={onEdit}
					onDelete={onDelete}
					onSetActive={onSetActive}
				/>
			))}
			<p className="text-sm text-muted-foreground pt-1" role="status">
				{practices.length} practice{practices.length === 1 ? "" : "s"}
			</p>
		</div>
	);
}
