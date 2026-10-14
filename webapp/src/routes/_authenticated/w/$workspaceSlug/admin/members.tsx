import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import { z } from "zod";

import {
	getAllTeamsOptions,
	getUsersWithTeamsOptions,
	getUsersWithTeamsQueryKey,
	updateMemberVisibilityMutation,
} from "@/api/@tanstack/react-query.gen";
import { adaptApiUserTeams } from "@/components/admin/members/user-teams";
import { WorkspaceMembersPage } from "@/components/admin/members/WorkspaceMembersPage";
import type { WorkspaceMembersTableView } from "@/components/admin/members/WorkspaceMembersTable";
import { NoWorkspace } from "@/components/common/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";
import { hasText } from "@/lib/text";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

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

	// The members are the page; the teams are only the filter's options, read and retried on their own.
	const usersQueryOptions = getUsersWithTeamsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const usersQuery = useQuery({
		...usersQueryOptions,
		enabled: Boolean(workspaceSlug) && (usersQueryOptions.enabled ?? true),
	});
	useQueryReadRefusal(usersQueryOptions.queryKey, usersQuery.isError, usersQuery.error);

	const teamsQueryOptions = getAllTeamsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const teamsQuery = useQuery({
		...teamsQueryOptions,
		enabled: Boolean(workspaceSlug) && (teamsQueryOptions.enabled ?? true),
	});
	useQueryReadRefusal(teamsQueryOptions.queryKey, teamsQuery.isError, teamsQuery.error);

	const queryClient = useQueryClient();
	const toggleHidden = useMutation({
		...updateMemberVisibilityMutation(),
		onSuccess: (_data, variables) => {
			void queryClient.invalidateQueries({
				queryKey: getUsersWithTeamsQueryKey({
					path: { workspaceSlug: variables.path.workspaceSlug },
				}),
			});
		},
		onError: (error, variables) => {
			toast.error("We could not update visibility in workspace activity", {
				description: `${problemDetailOf(error)} ${inWorkspace(variables.path.workspaceSlug)}`,
			});
		},
	});

	const handleToggleHidden = (userId: number, hidden: boolean) => {
		if (!hasText(workspaceSlug)) {
			return;
		}
		toggleHidden.mutate({
			path: { workspaceSlug, userId },
			query: { hidden },
		});
	};

	const users = (usersQuery.data?.map(adaptApiUserTeams) ?? [])
		.map((user) => ({
			...user,
			teams: [...user.teams].sort((a, b) => a.name.localeCompare(b.name)),
		}))
		.sort((a, b) => a.user.name.localeCompare(b.user.name));
	// A failed read offers no options: what it last returned is not what the reader may see now.
	const teams = teamsQuery.isError
		? []
		: [...(teamsQuery.data ?? [])].sort((a, b) => a.name.localeCompare(b.name));
	const isLoading = isWorkspaceLoading || usersQuery.isLoading;
	// Only a team list that loaded can say the address names no team; until then its team still
	// narrows the members, by the teams each member carries.
	const teamUnknown =
		hasText(search.team) &&
		teamsQuery.isSuccess &&
		!teamsQuery.isFetching &&
		!teams.some((team) => team.id.toString() === search.team);
	const selectedTeam = hasText(search.team) && !teamUnknown ? search.team : "all";
	const view: WorkspaceMembersTableView = {
		q: search.q ?? "",
		team: selectedTeam,
		sort: search.sort ?? "name",
		desc: search.desc ?? false,
		page: search.page ?? 0,
		size: search.size ?? 10,
	};

	useEffect(() => {
		if (teamUnknown) {
			void navigate({ search: (previous) => ({ ...previous, team: undefined }), replace: true });
		}
	}, [navigate, teamUnknown]);

	if (!hasText(workspaceSlug) && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	return (
		<WorkspaceMembersPage
			users={users}
			teams={teams}
			isLoading={isLoading || !hasText(workspaceSlug)}
			error={workspaceError ?? usersQuery.error}
			onRetry={() => {
				void usersQuery.refetch();
			}}
			teamsError={teamsQuery.error}
			teamsRetrying={teamsQuery.isFetching}
			onRetryTeams={() => {
				void teamsQuery.refetch();
			}}
			onToggleHidden={handleToggleHidden}
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

function inWorkspace(workspaceSlug: string): string {
	return `In workspace ${workspaceSlug}.`;
}
