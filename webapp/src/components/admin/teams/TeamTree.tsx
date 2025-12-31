import type { LabelInfo, TeamInfo } from "@/api/types.gen";
import type { MemberCountMode } from "@/lib/team-hierarchy";
import { RepositoryCard } from "./RepositoryCard";
import { TeamCard } from "./TeamCard";

export interface TeamTreeProps {
	team: TeamInfo;
	childrenMap: Map<number, TeamInfo[]>;
	displaySet: Set<number>;
	/** Precomputed member counts per team: { direct, rollup } */
	memberCounts?: Map<number, { direct: number; rollup: number }>;
	/** Current member count mode */
	countMode?: MemberCountMode;
	onToggleVisibility: (teamId: number, hidden: boolean) => void | Promise<void>;
	onToggleRepositoryVisibility: (
		teamId: number,
		repositoryId: number,
		hidden: boolean,
	) => void | Promise<void>;
	onAddLabel?: (teamId: number, repositoryId: number, label: string) => Promise<void>;
	onRemoveLabel?: (teamId: number, labelId: number) => Promise<void>;
	getCatalogLabels: (repoId: number) => LabelInfo[];
}

export function TeamTree({
	team,
	childrenMap,
	displaySet,
	memberCounts,
	countMode = "direct",
	onToggleVisibility,
	onToggleRepositoryVisibility,
	onAddLabel,
	onRemoveLabel,
	getCatalogLabels,
}: TeamTreeProps) {
	const children = (childrenMap.get(team.id) ?? []).filter((c) => displaySet.has(c.id));
	const counts = memberCounts?.get(team.id);
	const directCount = counts?.direct ?? (team.members ?? []).length;
	const rollupCount = counts?.rollup;
	return (
		<TeamCard
			team={team}
			memberCount={directCount}
			rollupMemberCount={rollupCount}
			countMode={countMode}
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
								onToggleVisibility={(hidden: boolean) =>
									onToggleRepositoryVisibility(team.id, repo.id, hidden)
								}
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
							memberCounts={memberCounts}
							countMode={countMode}
							onToggleVisibility={onToggleVisibility}
							onToggleRepositoryVisibility={onToggleRepositoryVisibility}
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
