import { Users } from "lucide-react";
import { useLayoutEffect, useMemo } from "react";
import type { TeamInfo } from "@/api/types.gen";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { type Contributor, ContributorGrid } from "@/components/shared/ContributorGrid";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export interface TeamsPageProps {
	teams: TeamInfo[];
	isLoading: boolean;
}

export function TeamsPage({ teams, isLoading }: TeamsPageProps) {
	const visibleTeams = useMemo(() => {
		return [...teams].filter((t) => !t.hidden);
	}, [teams]);

	const sortMembers = (team: TeamInfo) => {
		return [...(team.members ?? [])].sort((a, b) => a.name.localeCompare(b.name));
	};

	const allTeamsById = useMemo(() => {
		const map = new Map<number, TeamInfo>();
		for (const t of teams) map.set(t.id, t);
		return map;
	}, [teams]);

	const { roots, childrenMap } = useMemo(() => {
		const getVisibleAncestorParentId = (team: TeamInfo): number | undefined => {
			let pid = team.parentId;
			const guard = new Set<number>();
			while (pid !== undefined) {
				if (guard.has(pid)) return undefined;
				guard.add(pid);
				const parent = allTeamsById.get(pid);
				if (!parent) return undefined;
				if (!parent.hidden) return parent.id;
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

		for (const [k, arr] of map.entries()) {
			arr.sort((a, b) => a.name.localeCompare(b.name));
			map.set(k, arr);
		}

		const rootTeams = visibleTeams
			.filter((t) => getVisibleAncestorParentId(t) === undefined)
			.sort((a, b) => a.name.localeCompare(b.name));

		return { roots: rootTeams, childrenMap: map };
	}, [visibleTeams, allTeamsById]);

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
				(membersByTeamId.get(child.id) ?? new Set<number>()).forEach((id) => res.add(id));
				collect(child.id).forEach((id) => res.add(id));
			}
			memo.set(teamId, res);
			return res;
		};

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
			htmlUrl: member.htmlUrl,
		}));
	};

	const renderTeamNode = (team: TeamInfo, depth = 0) => {
		const children = childrenMap.get(team.id) ?? [];
		const filteredContributors = getFilteredContributors(team);
		const hasDescendantMembers = (descendantMemberIdsMap.get(team.id)?.size ?? 0) > 0;
		const maybeEmptyState =
			filteredContributors.length === 0 && !hasDescendantMembers ? (
				<p className="py-6 text-center text-sm text-muted-foreground">
					No members assigned to this team
				</p>
			) : undefined;
		const content = (
			<>
				<ContributorGrid
					contributors={filteredContributors}
					size="sm"
					layout="compact"
					emptyState={maybeEmptyState}
				/>
				{children.length > 0 && (
					<div className="space-y-5">
						{children.map((child) => renderTeamNode(child, depth + 1))}
					</div>
				)}
			</>
		);

		if (depth > 0) {
			return (
				<section
					key={team.id}
					id={`team-${team.id}`}
					className="min-w-0 space-y-4 border-l pl-3 sm:pl-4"
				>
					<h3 className="text-sm font-semibold">{team.name}</h3>
					{content}
				</section>
			);
		}

		return (
			<Card key={team.id} id={`team-${team.id}`}>
				<CardHeader>
					<CardTitle>
						<h2>{team.name}</h2>
					</CardTitle>
				</CardHeader>
				<CardContent className="min-w-0 space-y-5">{content}</CardContent>
			</Card>
		);
	};

	useLayoutEffect(() => {
		let observer: MutationObserver | null = null;

		const cleanupObserver = () => {
			observer?.disconnect();
			observer = null;
		};

		const scrollToHash = (): boolean => {
			const hash = window.location.hash;
			if (!hash) return false;
			const id = hash.slice(1);
			const el = document.getElementById(id);
			if (el) {
				el.scrollIntoView({ behavior: "smooth", block: "center" });
				return true;
			}
			requestAnimationFrame(() => {
				const elNext = document.getElementById(id);
				if (elNext) {
					elNext.scrollIntoView({ behavior: "smooth", block: "center" });
				}
			});
			return false;
		};

		const ensureScroll = () => {
			if (!window.location.hash) {
				cleanupObserver();
				return;
			}

			if (scrollToHash()) {
				cleanupObserver();
				return;
			}

			if (!observer) {
				observer = new MutationObserver(() => {
					if (scrollToHash()) {
						cleanupObserver();
					}
				});
				observer.observe(document.body, { childList: true, subtree: true });
			}
		};

		ensureScroll();
		window.addEventListener("hashchange", ensureScroll);
		return () => {
			cleanupObserver();
			window.removeEventListener("hashchange", ensureScroll);
		};
	}, []);

	return (
		<PageLayout>
			<PageHeader
				icon={<Users />}
				title="Teams"
				description="See contributors grouped by team and explore their activity."
			/>

			{isLoading ? (
				<div className="space-y-4">
					{["a", "b", "c"].map((id) => (
						<Card key={id}>
							<CardHeader>
								<Skeleton className="h-6 w-1/4" />
							</CardHeader>
							<CardContent>
								<ContributorGrid
									contributors={[]}
									isLoading
									size="sm"
									layout="compact"
									loadingSkeletonCount={4}
								/>
							</CardContent>
						</Card>
					))}
				</div>
			) : roots.length > 0 ? (
				<div className="space-y-4">{roots.map((team) => renderTeamNode(team))}</div>
			) : (
				<p className="py-8 text-center text-muted-foreground">No teams found</p>
			)}
		</PageLayout>
	);
}
