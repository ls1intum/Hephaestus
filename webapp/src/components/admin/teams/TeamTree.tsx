import type { LabelInfo, TeamInfo } from "@/api/types.gen";
import { RepositoryCard } from "./RepositoryCard";
import { TeamCard } from "./TeamCard";

/** A nested team's heading sits one rank below its parent's, and stops at the last rank that exists. */
const HEADING_LEVELS = [2, 3, 4, 5, 6] as const;

type HeadingLevel = (typeof HEADING_LEVELS)[number];

const nestedHeadingLevel = (level: HeadingLevel): HeadingLevel =>
	HEADING_LEVELS[HEADING_LEVELS.indexOf(level) + 1] ?? level;

export interface TeamTreeProps {
	team: TeamInfo;
	childrenMap: Map<number, TeamInfo[]>;
	displaySet: Set<number>;
	headingLevel?: HeadingLevel;
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
	headingLevel = 2,
	onToggleVisibility,
	onToggleRepositoryVisibility,
	onAddLabel,
	onRemoveLabel,
	getCatalogLabels,
}: TeamTreeProps) {
	const children = (childrenMap.get(team.id) ?? []).filter((c) => displaySet.has(c.id));
	return (
		<TeamCard
			team={team}
			memberCount={(team.members ?? []).length}
			headingLevel={headingLevel}
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
							headingLevel={nestedHeadingLevel(headingLevel)}
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
