import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import { z } from "zod";

import {
	assignMemberRoleMutation,
	getAllTeamsOptions,
	getOwnMembershipQueryKey,
	getUsersWithTeamsQueryKey,
	listMembershipsInfiniteOptions,
	listMembershipsInfiniteQueryKey,
	setMemberHiddenMutation,
} from "@/api/@tanstack/react-query.gen";
import type { WorkspaceMembership } from "@/api/types.gen";
import {
	WORKSPACE_ROLE_LABELS,
	workspaceMemberRows,
} from "@/components/admin/members/workspace-members";
import { WorkspaceMembersPage } from "@/components/admin/members/WorkspaceMembersPage";
import type { WorkspaceMembersTableView } from "@/components/admin/members/WorkspaceMembersTable";
import { accountName } from "@/components/admin/users/account-labels";
import { NoWorkspace } from "@/components/common/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";
import { hasText } from "@/lib/text";

/** The server's largest page: the table filters and sorts every member, so it reads them all. */
const MEMBER_PAGE_SIZE = 100;

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/members")({
	head: workspaceAdminHead("Members"),
	validateSearch: z.object({
		q: z.string().max(200).optional().catch(undefined),
		team: z.string().optional().catch(undefined),
		sort: z.enum(["name", "username"]).optional().catch(undefined),
		desc: z
			.union([z.boolean(), z.enum(["true", "false"]).transform((value) => value === "true")])
			.optional()
			.catch(undefined),
		page: z.coerce.number().int().min(0).optional().catch(undefined),
		size: z.coerce
			.number()
			.refine((value) => [10, 20, 30, 40, 50].includes(value))
			.optional()
			.catch(undefined),
	}),
	component: AdminMembersContainer,
});

function AdminMembersContainer() {
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });
	const {
		workspaceSlug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();

	const membersQueryOptions = listMembershipsInfiniteOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
		query: { size: MEMBER_PAGE_SIZE },
	});
	const membersQuery = useInfiniteQuery({
		...membersQueryOptions,
		initialPageParam: 0,
		getNextPageParam: (lastPage: WorkspaceMembership[], allPages: WorkspaceMembership[][]) =>
			lastPage.length < MEMBER_PAGE_SIZE ? undefined : allPages.length,
		enabled: Boolean(workspaceSlug),
	});
	const { hasNextPage, isFetchingNextPage, isError: membersFailed, fetchNextPage } = membersQuery;
	useEffect(() => {
		if (hasNextPage && !isFetchingNextPage && !membersFailed) {
			void fetchNextPage();
		}
	}, [hasNextPage, isFetchingNextPage, membersFailed, fetchNextPage]);

	const teamsQueryOptions = getAllTeamsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const {
		data: teamsData,
		isLoading: teamsLoading,
		error: teamsError,
		refetch: refetchTeams,
	} = useQuery({
		...teamsQueryOptions,
		enabled: Boolean(workspaceSlug) && (teamsQueryOptions.enabled ?? true),
	});

	const queryClient = useQueryClient();
	const membersKey = listMembershipsInfiniteQueryKey({
		path: { workspaceSlug: workspaceSlug ?? "" },
		query: { size: MEMBER_PAGE_SIZE },
	});
	const toggleHidden = useMutation({
		...setMemberHiddenMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: membersKey });
			// Workspace activity lists the same people and leaves hidden ones out.
			void queryClient.invalidateQueries({
				queryKey: getUsersWithTeamsQueryKey({ path: { workspaceSlug: workspaceSlug ?? "" } }),
			});
		},
		onError: (error) => {
			toast.error("We could not update visibility in workspace activity", {
				description: problemDetailOf(error),
			});
		},
	});
	const assignRole = useMutation({
		...assignMemberRoleMutation(),
		onSuccess: (updated) => {
			toast.success(
				`${accountName(updated.account)}’s role is now ${WORKSPACE_ROLE_LABELS[updated.role]}`,
			);
			void queryClient.invalidateQueries({ queryKey: membersKey });
			// The admin may have changed their own role, which the navigation and guards read.
			void queryClient.invalidateQueries({
				queryKey: getOwnMembershipQueryKey({ path: { workspaceSlug: workspaceSlug ?? "" } }),
			});
		},
		onError: (error) => {
			toast.error("We could not change the role", { description: problemDetailOf(error) });
		},
	});

	const handleToggleHidden = (accountId: string, hidden: boolean) => {
		if (!hasText(workspaceSlug)) {
			return;
		}
		toggleHidden.mutate({ path: { workspaceSlug, accountId }, body: { hidden } });
	};

	const handleRoleChange = (accountId: string, role: WorkspaceMembership["role"]) => {
		if (!hasText(workspaceSlug)) {
			return;
		}
		assignRole.mutate({ path: { workspaceSlug }, body: { accountId, role } });
	};

	const teams = [...(teamsData ?? [])].sort((a, b) => a.name.localeCompare(b.name));
	const members = workspaceMemberRows(membersQuery.data?.pages.flat() ?? [], teams);
	// Until every page is in, a count or an empty filter result would be a claim about part of the list.
	const isLoading = isWorkspaceLoading || membersQuery.isPending || hasNextPage || teamsLoading;
	const selectedTeam =
		hasText(search.team) && teams.some((team) => team.id.toString() === search.team)
			? search.team
			: "all";
	const view: WorkspaceMembersTableView = {
		q: search.q ?? "",
		team: selectedTeam,
		sort: search.sort ?? "name",
		desc: search.desc ?? false,
		page: search.page ?? 0,
		size: search.size ?? 10,
	};

	useEffect(() => {
		if (teamsData && !teamsError && hasText(search.team) && selectedTeam === "all") {
			void navigate({ search: (previous) => ({ ...previous, team: undefined }), replace: true });
		}
	}, [navigate, search.team, selectedTeam, teamsData, teamsError]);

	if (!hasText(workspaceSlug) && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	return (
		<WorkspaceMembersPage
			members={members}
			teams={teams}
			isLoading={isLoading || !hasText(workspaceSlug)}
			error={workspaceError ?? membersQuery.error ?? teamsError}
			onRetry={() => {
				void membersQuery.refetch();
				void refetchTeams();
			}}
			onToggleHidden={handleToggleHidden}
			onRoleChange={handleRoleChange}
			pendingRoleAccountId={assignRole.isPending ? assignRole.variables.body.accountId : undefined}
			view={view}
			onViewChange={(patch) => {
				void navigate({
					search: (previous) => {
						const next = {
							q: "",
							team: "all",
							sort: "name" as const,
							desc: false,
							page: 0,
							size: 10,
							...previous,
							...patch,
						};
						return {
							q: next.q || undefined,
							team: next.team === "all" ? undefined : next.team,
							sort: next.sort === "name" ? undefined : next.sort,
							desc: next.desc || undefined,
							page: next.page || undefined,
							size: next.size === 10 ? undefined : next.size,
						};
					},
					replace: true,
				});
			}}
			renderPageLink={(page, props) => (
				<Link
					to="."
					search={{
						q: view.q || undefined,
						team: view.team === "all" ? undefined : view.team,
						sort: view.sort === "name" ? undefined : view.sort,
						desc: view.desc || undefined,
						page: page || undefined,
						size: view.size === 10 ? undefined : view.size,
					}}
					{...props}
				/>
			)}
		/>
	);
}
