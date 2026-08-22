import { Search, Users } from "lucide-react";
import { useMemo } from "react";
import type { LabelInfo, TeamInfo } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { TeamTree } from "./teams/TeamTree";

export interface TeamsTableProps {
	teams: TeamInfo[];
	isLoading?: boolean;
	error?: unknown;
	onRetry?: () => void;
	search: string;
	onSearchChange: (search: string) => void;
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
	error,
	onRetry,
	search,
	onSearchChange,
	onHideTeam,
	onToggleRepositoryVisibility,
	onAddLabelToTeam,
	onRemoveLabelFromTeam,
}: TeamsTableProps) {
	const allTeamsById = useMemo(() => {
		const map = new Map<number, TeamInfo>();
		for (const t of teams) map.set(t.id, t);
		return map;
	}, [teams]);

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
		for (const [k, arr] of map.entries()) {
			arr.sort((a, b) => a.name.localeCompare(b.name));
			map.set(k, arr);
		}
		return map;
	}, [teams, allTeamsById]);

	const rootsAll = useMemo(
		() =>
			[...teams]
				.filter((t) => t.parentId === undefined || !allTeamsById.has(t.parentId))
				.sort((a, b) => a.name.localeCompare(b.name)),
		[teams, allTeamsById],
	);

	const displaySet = useMemo(() => {
		const normalizedSearch = search.trim().toLowerCase();
		if (!normalizedSearch) return new Set<number>(teams.map((t) => t.id));
		const result = new Set<number>();
		const memo = new Map<number, boolean>();
		const matches = (t: TeamInfo): boolean => t.name.toLowerCase().includes(normalizedSearch);
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
	}, [search, teams, childrenMap, rootsAll]);

	const repoLabelCatalog = useMemo(() => {
		const map = new Map<number, Map<number, LabelInfo>>();
		for (const t of teams) {
			for (const repo of t.repositories) {
				const byId = map.get(repo.id) ?? new Map<number, LabelInfo>();
				for (const lbl of repo.labels ?? []) {
					byId.set(lbl.id, lbl);
				}
				map.set(repo.id, byId);
			}
		}
		return map;
	}, [teams]);

	const getCatalogLabels = (repoId: number): LabelInfo[] => {
		const byId = repoLabelCatalog.get(repoId);
		if (!byId) return [];
		return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name));
	};

	const header = (
		<PageHeader
			icon={<Users />}
			title="Teams"
			description="Manage which synced teams and repositories participate in this workspace."
		/>
	);

	if (error) {
		return (
			<PageLayout>
				{header}
				<QueryErrorAlert error={error} title="Couldn't load teams" onRetry={onRetry} />
			</PageLayout>
		);
	}

	if (isLoading) {
		return (
			<PageLayout>
				{header}
				<div className="flex items-center justify-between">
					<Skeleton className="h-10 w-64" />
					<Skeleton className="h-10 w-32" />
				</div>
				<div className="space-y-4">
					{["a", "b", "c", "d"].map((id) => (
						<Skeleton key={`loading-${id}`} className="h-32" />
					))}
				</div>
			</PageLayout>
		);
	}

	return (
		<PageLayout>
			{header}
			<div className="flex flex-col sm:flex-row gap-4 sm:items-center sm:justify-between">
				<div className="relative w-full sm:max-w-md">
					<Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground h-4 w-4" />
					<Input
						aria-label="Search teams"
						placeholder="Search teams..."
						value={search}
						onChange={(e) => onSearchChange(e.target.value)}
						className="pl-10"
					/>
				</div>
			</div>

			{rootsAll.filter((t) => displaySet.has(t.id)).length === 0 ? (
				<div className="text-center py-12">
					<Users className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
					<h2 className="text-lg font-medium mb-2">No teams found</h2>
					<p className="text-muted-foreground">
						{search ? "Try different search terms." : "No teams available."}
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
								onToggleVisibility={(teamId, hidden) => onHideTeam(teamId, hidden)}
								onToggleRepositoryVisibility={onToggleRepositoryVisibility}
								onAddLabel={onAddLabelToTeam}
								onRemoveLabel={onRemoveLabelFromTeam}
								getCatalogLabels={getCatalogLabels}
							/>
						))}
				</div>
			)}
		</PageLayout>
	);
}
