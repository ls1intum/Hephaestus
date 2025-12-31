import { Search, Users } from "lucide-react";
import { useMemo, useState } from "react";
import type { LabelInfo, TeamInfo } from "@/api/types.gen";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { computeMemberCounts, type MemberCountMode } from "@/lib/team-hierarchy";
import { MemberCountModeToggle } from "./teams/MemberCountModeToggle";
import { TeamTree } from "./teams/TeamTree";

export interface TeamsTableProps {
	teams: TeamInfo[];
	isLoading?: boolean;
	onHideTeam: (teamId: number, hidden: boolean) => Promise<void>;
	onToggleRepositoryVisibility: (
		teamId: number,
		repositoryId: number,
		hidden: boolean,
	) => Promise<void>;
	onAddLabelToTeam?: (teamId: number, repositoryId: number, label: string) => Promise<void>;
	onRemoveLabelFromTeam?: (teamId: number, labelId: number) => Promise<void>;
}

export function AdminTeamsTable({
	teams,
	isLoading = false,
	onHideTeam,
	onToggleRepositoryVisibility,
	onAddLabelToTeam,
	onRemoveLabelFromTeam,
}: TeamsTableProps) {
	const [teamSearch, setTeamSearch] = useState("");
	const [countMode, setCountMode] = useState<MemberCountMode>("direct");

	// Build full team map (do NOT exclude hidden in admin view)
	const allTeamsById = useMemo(() => {
		const map = new Map<number, TeamInfo>();
		for (const t of teams) map.set(t.id, t);
		return map;
	}, [teams]);

	// Build children map using plain parentId relationships
	const childrenMap = useMemo(() => {
		const map = new Map<number, TeamInfo[]>();
		for (const t of teams) {
			const pid = t.parentId;
			if (pid !== undefined && allTeamsById.has(pid)) {
				const arr = map.get(pid) ?? [];
				arr.push(t);
				map.set(pid, arr);
			}
		}
		// sort children
		for (const [k, arr] of map.entries()) {
			arr.sort((a, b) => a.name.localeCompare(b.name));
			map.set(k, arr);
		}
		return map;
	}, [teams, allTeamsById]);

	// Roots are teams without a valid parent in our dataset
	const rootsAll = useMemo(
		() =>
			[...teams]
				.filter((t) => t.parentId === undefined || !allTeamsById.has(t.parentId))
				.sort((a, b) => a.name.localeCompare(b.name)),
		[teams, allTeamsById],
	);

	// Compute display set based on name search: include a node if it or any descendant matches
	const displaySet = useMemo(() => {
		const search = teamSearch.trim().toLowerCase();
		if (!search) return new Set<number>(teams.map((t) => t.id));
		const result = new Set<number>();
		const memo = new Map<number, boolean>();
		const matches = (t: TeamInfo): boolean => t.name.toLowerCase().includes(search);
		const hasMatchInSubtree = (t: TeamInfo): boolean => {
			const cached = memo.get(t.id);
			if (cached !== undefined) return cached;
			if (matches(t)) {
				memo.set(t.id, true);
				return true;
			}
			const children = childrenMap.get(t.id) ?? [];
			for (const c of children) {
				if (hasMatchInSubtree(c)) {
					memo.set(t.id, true);
					return true;
				}
			}
			memo.set(t.id, false);
			return false;
		};
		for (const r of rootsAll) {
			const traverse = (node: TeamInfo) => {
				if (hasMatchInSubtree(node)) {
					result.add(node.id);
					for (const c of childrenMap.get(node.id) ?? []) traverse(c);
				}
			};
			traverse(r);
		}
		return result;
	}, [teamSearch, teams, childrenMap, rootsAll]);

	// Repository-wide label catalog by repoId, using repository.labels provided in RepositoryInfoDTO
	const repoLabelCatalog = useMemo(() => {
		// Deduplicate by normalized label name, not by id, since the same repo appears under multiple teams
		// and backend DTOs might produce different ids for identical labels.
		const map = new Map<number, Map<string, LabelInfo>>();
		for (const t of teams) {
			for (const repo of t.repositories ?? []) {
				const byName = map.get(repo.id) ?? new Map<string, LabelInfo>();
				for (const lbl of repo.labels ?? []) {
					const key = (lbl.name ?? "").toLowerCase();
					if (key && !byName.has(key)) byName.set(key, lbl);
				}
				map.set(repo.id, byName);
			}
		}
		return map;
	}, [teams]);

	const getCatalogLabels = (repoId: number): LabelInfo[] => {
		const byName = repoLabelCatalog.get(repoId);
		if (!byName) return [];
		return [...byName.values()].sort((a, b) => a.name.localeCompare(b.name));
	};

	// Compute member counts for both direct and rollup modes
	const memberCounts = useMemo(() => {
		// Convert TeamInfo[] to HierarchyTeam[] by mapping members
		const hierarchyTeams = teams.map((t) => ({
			id: t.id,
			parentId: t.parentId,
			members: (t.members ?? []).map((m) => ({ id: m.id })),
		}));
		return computeMemberCounts(hierarchyTeams);
	}, [teams]);

	if (isLoading) {
		return (
			<div className="space-y-4">
				<div className="flex items-center justify-between">
					<Skeleton className="h-10 w-64" />
					<Skeleton className="h-10 w-32" />
				</div>
				<div className="space-y-4">
					{["a", "b", "c", "d"].map((id) => (
						<Skeleton key={`loading-${id}`} className="h-32" />
					))}
				</div>
			</div>
		);
	}

	return (
		<div className="space-y-6">
			<div className="flex flex-col sm:flex-row gap-4 sm:items-end sm:justify-between">
				<div className="relative w-full sm:max-w-md">
					<Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground h-4 w-4" />
					<Input
						placeholder="Search teams..."
						value={teamSearch}
						onChange={(e) => setTeamSearch(e.target.value)}
						className="pl-10"
					/>
				</div>
				<MemberCountModeToggle mode={countMode} onModeChange={setCountMode} />
			</div>

			{rootsAll.filter((t) => displaySet.has(t.id)).length === 0 ? (
				<div className="text-center py-12">
					<Users className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
					<h3 className="text-lg font-medium mb-2">No teams found</h3>
					<p className="text-muted-foreground">
						{teamSearch ? "Try different search terms." : "No teams available."}
					</p>
				</div>
			) : (
				<div className="space-y-4">
					{rootsAll
						.filter((t) => displaySet.has(t.id))
						.map((team) => (
							<TeamTree
								key={team.id}
								team={team}
								childrenMap={childrenMap}
								displaySet={displaySet}
								memberCounts={memberCounts}
								countMode={countMode}
								onToggleVisibility={(teamId, hidden) => onHideTeam(teamId, hidden)}
								onToggleRepositoryVisibility={onToggleRepositoryVisibility}
								onAddLabel={onAddLabelToTeam}
								onRemoveLabel={onRemoveLabelFromTeam}
								getCatalogLabels={getCatalogLabels}
							/>
						))}
				</div>
			)}
		</div>
	);
}
