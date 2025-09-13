import { useMemo } from "react";
import type { LabelInfo, RepositoryInfo, TeamInfo } from "@/api/types.gen";
import { GithubBadge } from "@/components/shared/GithubBadge";
import { cn } from "@/lib/utils";

export interface RepositoryLabelsToggleProps {
	team: TeamInfo;
	repository: RepositoryInfo;
	catalogLabels: LabelInfo[];
	onAddLabel?: (
		teamId: number,
		repositoryId: number,
		label: string,
	) => Promise<void>;
	onRemoveLabel?: (teamId: number, labelId: number) => Promise<void>;
}

export function RepositoryLabelsToggle({
	team,
	repository,
	catalogLabels,
	onAddLabel,
	onRemoveLabel,
}: RepositoryLabelsToggleProps) {
	const activeByName = useMemo(() => {
		const map = new Map<string, LabelInfo>();
		for (const l of team.labels ?? []) {
			if (l.repository?.id !== repository.id) continue;
			const key = (l.name ?? "").toLowerCase();
			if (key && !map.has(key)) map.set(key, l);
		}
		return map;
	}, [team.labels, repository.id]);

	const shown = useMemo(() => {
		return [...catalogLabels].sort((a, b) => a.name.localeCompare(b.name));
	}, [catalogLabels]);

	const handleToggle = async (label: LabelInfo) => {
		const key = (label.name ?? "").toLowerCase();
		const active = activeByName.get(key);
		if (active) {
			await onRemoveLabel?.(team.id, active.id);
		} else {
			await onAddLabel?.(team.id, repository.id, label.name);
		}
	};

	return (
		<div className="space-y-1.5">
			<h4 className="font-medium text-sm">Labels</h4>
			<p className="text-xs text-muted-foreground">
				Selecting labels limits this team's contribution metrics to items tagged
				with any of the selected labels for this repository.
			</p>
			{shown.length > 0 ? (
				<div className="flex flex-wrap gap-1.5">
					{shown.map((label) => {
						const isActive = activeByName.has((label.name ?? "").toLowerCase());
						return (
							<button
								type="button"
								key={`${label.id}-${label.name}`}
								onClick={() => handleToggle(label)}
								title={
									isActive
										? "Click to remove from team"
										: "Click to add to team"
								}
							>
								<GithubBadge
									label={label.name}
									color={label.color}
									className={cn(
										"text-[11px]",
										isActive
											? "opacity-100"
											: "opacity-30 hover:ring-2 hover:ring-primary/50 hover:ring-offset-1",
									)}
								/>
							</button>
						);
					})}
				</div>
			) : (
				<p className="text-xs text-muted-foreground">
					No labels available for this repository.
				</p>
			)}
		</div>
	);
}

export default RepositoryLabelsToggle;
