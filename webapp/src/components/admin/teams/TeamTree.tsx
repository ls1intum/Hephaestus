import type { LabelInfo, TeamInfo } from "@/api/types.gen";
import { RepositoryCard } from "./RepositoryCard";
import { TeamCard } from "./TeamCard";

export interface TeamTreeProps {
	team: TeamInfo;
	childrenMap: Map<number, TeamInfo[]>;
	displaySet: Set<number>;
	onToggleVisibility: (teamId: number, hidden: boolean) => void | Promise<void>;
	onAddLabel?: (
		teamId: number,
		repositoryId: number,
		label: string,
	) => Promise<void>;
	onRemoveLabel?: (teamId: number, labelId: number) => Promise<void>;
	getCatalogLabels: (repoId: number) => LabelInfo[];
}

export function TeamTree({
	team,
	childrenMap,
	displaySet,
	onToggleVisibility,
	onAddLabel,
	onRemoveLabel,
	getCatalogLabels,
}: TeamTreeProps) {
	const children = (childrenMap.get(team.id) ?? []).filter((c) =>
		displaySet.has(c.id),
	);
	return (
		<TeamCard
			team={team}
			memberCount={(team.members ?? []).length}
			onToggleVisibility={(hidden) => onToggleVisibility(team.id, hidden)}
			getCatalogLabels={getCatalogLabels}
		>
			{(team.repositories ?? []).length > 0 ? (
				<div className="space-y-3">
					{[...(team.repositories ?? [])]
						.sort((a, b) => a.nameWithOwner.localeCompare(b.nameWithOwner))
						.map((repo) => (
							<RepositoryCard
								key={repo.id}
								repository={repo}
								team={team}
								catalogLabels={getCatalogLabels(repo.id)}
								onAddLabel={onAddLabel}
								onRemoveLabel={onRemoveLabel}
							/>
						))}
				</div>
			) : (
				<div className="text-center py-6 text-sm text-muted-foreground">
					No repositories assigned to this team
				</div>
			)}

			{children.length > 0 && (
				<div className="space-y-4 mt-4">
					{children.map((child) => (
						<TeamTree
							key={child.id}
							team={child}
							childrenMap={childrenMap}
							displaySet={displaySet}
							onToggleVisibility={onToggleVisibility}
							onAddLabel={onAddLabel}
							onRemoveLabel={onRemoveLabel}
							getCatalogLabels={getCatalogLabels}
						/>
					))}
				</div>
			)}
		</TeamCard>
	);
}

export default TeamTree;
