import { useMemo, useLayoutEffect } from "react";
import type { TeamInfo } from "@/api/types.gen";
import {
	type Contributor,
	ContributorGrid,
} from "@/components/shared/ContributorGrid";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * TeamsPage component for displaying contributors grouped by teams
 * This is a purely presentational component that receives data via props
 */
export interface TeamsPageProps {
	teams: TeamInfo[];
	isLoading: boolean;
}

export function TeamsPage({ teams, isLoading }: TeamsPageProps) {
	// Filter out hidden teams (do not render private/hidden ones)
	const visibleTeams = useMemo(() => {
		return [...teams].filter((t) => !t.hidden);
	}, [teams]);

	// Helper function to sort team members alphabetically by name
	const sortMembers = (team: TeamInfo) => {
		return [...(team.members ?? [])].sort((a, b) =>
			a.name.localeCompare(b.name),
		);
	};

	// Note: We'll build contributors after filtering descendant duplicates

	// Index of all teams (including hidden) to traverse ancestry chains
	const allTeamsById = useMemo(() => {
		const map = new Map<number, TeamInfo>();
		for (const t of teams) map.set(t.id, t);
		return map;
	}, [teams]);

	// Build a parent -> children mapping to create a tree
	const { roots, childrenMap } = useMemo(() => {
		// Find nearest visible ancestor for a visible team; skip hidden teams in between
		const getVisibleAncestorParentId = (team: TeamInfo): number | undefined => {
			let pid = team.parentId;
			const guard = new Set<number>();
			while (pid !== undefined) {
				if (guard.has(pid)) return undefined; // safety against cycles
				guard.add(pid);
				const parent = allTeamsById.get(pid);
				if (!parent) return undefined; // unknown ancestor -> treat as root
				if (!parent.hidden) return parent.id; // nearest visible ancestor
				pid = parent.parentId;
			}
			return undefined;
		};

		const map = new Map<number, TeamInfo[]>();
		visibleTeams.forEach((t) => {
			const effectiveParentId = getVisibleAncestorParentId(t);
			if (effectiveParentId !== undefined) {
				const arr = map.get(effectiveParentId) ?? [];
				arr.push(t);
				map.set(effectiveParentId, arr);
			}
		});

		// sort children for stable output
		for (const [k, arr] of map.entries()) {
			arr.sort((a, b) => a.name.localeCompare(b.name));
			map.set(k, arr);
		}

		const rootTeams = visibleTeams
			.filter((t) => getVisibleAncestorParentId(t) === undefined)
			.sort((a, b) => a.name.localeCompare(b.name));

		return { roots: rootTeams, childrenMap: map };
	}, [visibleTeams, allTeamsById]);

	// Build maps to filter out parent members that exist in any visible descendant subteam
	const membersByTeamId = useMemo(() => {
		const m = new Map<number, Set<number>>();
		visibleTeams.forEach((t) => {
			const ids = new Set<number>((t.members ?? []).map((mm) => mm.id));
			m.set(t.id, ids);
		});
		return m;
	}, [visibleTeams]);

	const descendantMemberIdsMap = useMemo(() => {
		const memo = new Map<number, Set<number>>();

		const collect = (teamId: number): Set<number> => {
			const cached = memo.get(teamId);
			if (cached !== undefined) return cached;
			const children = childrenMap.get(teamId) ?? [];
			const res = new Set<number>();
			for (const child of children) {
				// child's own members
				(membersByTeamId.get(child.id) ?? new Set<number>()).forEach((id) =>
					res.add(id),
				);
				// descendant members
				collect(child.id).forEach((id) => res.add(id));
			}
			memo.set(teamId, res);
			return res;
		};

		// prime map for all visible teams
		visibleTeams.forEach((t) => collect(t.id));
		return memo;
	}, [childrenMap, membersByTeamId, visibleTeams]);

	const getFilteredContributors = (team: TeamInfo): Contributor[] => {
		const exclude = descendantMemberIdsMap.get(team.id) ?? new Set<number>();
		const filtered = sortMembers(team).filter((m) => !exclude.has(m.id));
		return filtered.map((member) => ({
			id: member.id,
			login: member.login,
			name: member.name,
			avatarUrl: member.avatarUrl,
			htmlUrl: `https://github.com/${member.login}`,
		}));
	};

	const renderTeamNode = (team: TeamInfo, depth = 0) => {
		const children = childrenMap.get(team.id) ?? [];
		const filteredContributors = getFilteredContributors(team);
		const hasDescendantMembers =
			(descendantMemberIdsMap.get(team.id)?.size ?? 0) > 0;
		const emptyStateNode = (
			<div className="py-8 text-center">
				<p className="text-muted-foreground">
					No members assigned to this team
				</p>
			</div>
		);
		const maybeEmptyState =
			filteredContributors.length === 0 && !hasDescendantMembers
				? emptyStateNode
				: undefined;
		return (
			<Card key={team.id} id={`team-${team.id}`} className="flex flex-col gap-3">
				<CardHeader>
					<CardTitle>{team.name}</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<ContributorGrid
						contributors={filteredContributors}
						size="sm"
						layout="compact"
						emptyState={maybeEmptyState}
					/>
					{children.length > 0 &&
						children.map((child) => renderTeamNode(child, depth + 1))}
				</CardContent>
			</Card>
		);
	};

  useLayoutEffect(() => {
    const scrollToHash = () => {
      const hash = window.location.hash;
      if (!hash) return;
      const id = hash.slice(1);
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: "smooth", block: "center" });
        return true;
      }
      // one-frame fallback
      requestAnimationFrame(() => {
        const elNext = document.getElementById(id);
        if (elNext) elNext.scrollIntoView({ behavior: "smooth", block: "center" });
      });
    };

    // try immediately
    if (scrollToHash()) return;

    // observe DOM until the target appears (then disconnect)
    const observer = new MutationObserver(() => {
      if (scrollToHash()) observer.disconnect();
    });
    observer.observe(document.body, { childList: true, subtree: true });

    // also handle manual hash change
    window.addEventListener("hashchange", scrollToHash);
    return () => {
      observer.disconnect();
      window.removeEventListener("hashchange", scrollToHash);
    }
  }, []); // no `roots` dependency - observer handles late DOM insertion

	return (
		<>
			<h2 className="text-2xl font-bold mb-2">Team Contributors</h2>
			<p className="text-muted-foreground text-sm mb-4">
				Overview of contributors across different teams
			</p>

			{!isLoading && (
				<>
					<div className="space-y-4">
						{roots.map((team) => renderTeamNode(team))}
					</div>

					{roots.length === 0 && (
						<div className="py-8 text-center">
							<p className="text-muted-foreground">No teams found</p>
						</div>
					)}
				</>
			)}

			{isLoading &&
				Array(3)
					.fill(null)
					.map((_, teamIndex) => (
						<Card
							// biome-ignore lint/suspicious/noArrayIndexKey: Static array
							key={`loading-team-${teamIndex}`}
							className="flex flex-col mb-8 gap-3"
						>
							<CardHeader>
								<Skeleton className="h-6 w-1/4" />
							</CardHeader>
							<CardContent>
								<ContributorGrid
									contributors={[]}
									isLoading={true}
									size="sm"
									layout="compact"
									loadingSkeletonCount={4}
								/>
							</CardContent>
						</Card>
					))}
		</>
	);
}
