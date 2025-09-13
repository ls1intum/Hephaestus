import { Settings } from "lucide-react";
import { useMemo } from "react";
import type { LabelInfo, RepositoryInfo, TeamInfo } from "@/api/types.gen";
import { GithubBadge } from "@/components/shared/GithubBadge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import {
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { RepositoryLabelsToggle } from "./RepositoryLabelsToggle";

export interface RepositoryCardProps {
	repository: RepositoryInfo;
	team: TeamInfo;
	catalogLabels: LabelInfo[];
	onAddLabel?: (
		teamId: number,
		repositoryId: number,
		label: string,
	) => Promise<void>;
	onRemoveLabel?: (teamId: number, labelId: number) => Promise<void>;
}

export function RepositoryCard({
	repository,
	team,
	catalogLabels,
	onAddLabel,
	onRemoveLabel,
}: RepositoryCardProps) {
	const repoLabels = useMemo(() => {
		const byName = new Map<string, LabelInfo>();
		for (const label of team.labels ?? []) {
			if (label.repository?.id !== repository.id) continue;
			const key = (label.name ?? "").toLowerCase();
			if (key && !byName.has(key)) byName.set(key, label);
		}
		return [...byName.values()];
	}, [team.labels, repository.id]);

	const filteredRepoLabels = useMemo(
		() => [...repoLabels].sort((a, b) => a.name.localeCompare(b.name)),
		[repoLabels],
	);

	return (
		<Card
			className={cn(
				"flex flex-col border-border/50 gap-0",
				team.hidden ? "opacity-40" : "",
			)}
		>
			<CardHeader>
				<div className="flex items-start justify-between">
					<div className="min-w-0 flex-1">
						<div className="flex items-center gap-2 min-w-0">
							<a
								href={repository.htmlUrl}
								target="_blank"
								rel="noopener noreferrer"
								className={cn(
									"text-sm font-medium hover:underline block truncate",
									team.hidden ? "text-muted-foreground" : "",
								)}
								title={repository.nameWithOwner}
							>
								{repository.nameWithOwner}
							</a>
						</div>
						{repository.description && (
							<p className="text-xs text-muted-foreground mt-1 line-clamp-2">
								{repository.description}
							</p>
						)}
					</div>
					<div className="flex items-center gap-1 ml-2 flex-shrink-0">
						<Popover>
							<PopoverTrigger asChild>
								<Button variant="ghost" size="sm" className="h-7 w-7 p-0">
									<Settings className="h-3 w-3" />
								</Button>
							</PopoverTrigger>
							<PopoverContent
								className="w-[32rem] max-w-[calc(100vw-2rem)] p-3 sm:p-4"
								align="end"
							>
								<RepositoryLabelsToggle
									team={team}
									repository={repository}
									catalogLabels={catalogLabels}
									onAddLabel={onAddLabel}
									onRemoveLabel={onRemoveLabel}
								/>
							</PopoverContent>
						</Popover>
					</div>
				</div>
			</CardHeader>
			<CardContent className="flex flex-col">
				{filteredRepoLabels.length > 0 && (
					<div className="flex flex-wrap gap-1 mt-2">
						{filteredRepoLabels.map((label) => (
							<GithubBadge
								key={`${label.name}-${label.repository?.id ?? ""}`}
								label={label.name}
								color={label.color}
								className="text-xs"
							/>
						))}
					</div>
				)}
			</CardContent>
		</Card>
	);
}

export default RepositoryCard;
