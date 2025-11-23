import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
	addLabelToTeamMutation,
	getAllTeamsOptions,
	getAllTeamsQueryKey,
	removeLabelFromTeamMutation,
	updateRepositoryVisibilityMutation,
	updateTeamVisibilityMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Options } from "@/api/sdk.gen";
import type {
	TeamInfo,
	UpdateRepositoryVisibilityData,
	UpdateTeamVisibilityData,
} from "@/api/types.gen";
import { AdminTeamsTable } from "@/components/admin/AdminTeamsTable";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/teams",
)({
	component: AdminTeamsContainer,
});

function AdminTeamsContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug, isLoading: isWorkspaceLoading } =
		useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);

	// Query for teams data
	const teamsQueryKey = getAllTeamsQueryKey({
		path: { workspaceSlug: slug },
	});
	const teamsQuery = useQuery({
		...getAllTeamsOptions({ path: { workspaceSlug: slug } }),
		enabled: hasWorkspace,
	});

	// Mutations
	const updateTeamVisibility = useMutation({
		...updateTeamVisibilityMutation(),
		onMutate: async (vars: Options<UpdateTeamVisibilityData>) => {
			await queryClient.cancelQueries({ queryKey: teamsQueryKey });
			const prev = queryClient.getQueryData<TeamInfo[]>(teamsQueryKey);
			const teamId = vars.path?.id;
			const hidden =
				typeof vars.body === "boolean" ? vars.body : vars.query?.hidden;
			if (prev && typeof teamId === "number" && typeof hidden === "boolean") {
				const next = prev.map((t) => (t.id === teamId ? { ...t, hidden } : t));
				queryClient.setQueryData(teamsQueryKey, next);
			}
			return { prev } as { prev: TeamInfo[] | undefined };
		},
		onError: (_err, _vars, ctx) => {
			if (ctx?.prev) {
				queryClient.setQueryData(teamsQueryKey, ctx.prev);
			}
		},
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey });
		},
	});

	const addLabelToTeam = useMutation({
		...addLabelToTeamMutation(),
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey });
		},
	});

	const removeLabelFromTeam = useMutation({
		...removeLabelFromTeamMutation(),
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey });
		},
	});

	const updateRepositoryVisibility = useMutation({
		...updateRepositoryVisibilityMutation(),
		onMutate: async (vars: Options<UpdateRepositoryVisibilityData>) => {
			await queryClient.cancelQueries({ queryKey: teamsQueryKey });
			const prev = queryClient.getQueryData<TeamInfo[]>(teamsQueryKey);
			const teamId = vars.path?.teamId;
			const repositoryId = vars.path?.repositoryId;
			const hidden =
				typeof vars.body === "boolean"
					? vars.body
					: vars.query?.hiddenFromContributions;
			if (
				prev &&
				typeof teamId === "number" &&
				typeof repositoryId === "number" &&
				typeof hidden === "boolean"
			) {
				const next = prev.map((team) => {
					if (team.id !== teamId) return team;
					return {
						...team,
						repositories: (team.repositories ?? []).map((repo) =>
							repo.id === repositoryId
								? { ...repo, hiddenFromContributions: hidden }
								: repo,
						),
					};
				});
				queryClient.setQueryData(teamsQueryKey, next);
			}
			return { prev } as { prev: TeamInfo[] | undefined };
		},
		onError: (_err, _vars, ctx) => {
			if (ctx?.prev) {
				queryClient.setQueryData(teamsQueryKey, ctx.prev);
			}
		},
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey });
		},
	});

	// Handler functions
	const handleHideTeam = async (teamId: number, hidden: boolean) => {
		if (!hasWorkspace) {
			return;
		}
		await updateTeamVisibility.mutateAsync({
			path: { workspaceSlug: slug, id: teamId },
			body: hidden,
			query: { hidden },
		});
	};

	const handleAddLabelToTeam = async (
		teamId: number,
		repositoryId: number,
		label: string,
	) => {
		if (!hasWorkspace) {
			return;
		}
		await addLabelToTeam.mutateAsync({
			path: { workspaceSlug: slug, teamId, repositoryId, label },
		});
	};

	const handleRemoveLabelFromTeam = async (teamId: number, labelId: number) => {
		if (!hasWorkspace) {
			return;
		}
		await removeLabelFromTeam.mutateAsync({
			path: { workspaceSlug: slug, teamId, labelId },
		});
	};

	const handleToggleRepositoryVisibility = async (
		teamId: number,
		repositoryId: number,
		hidden: boolean,
	) => {
		if (!hasWorkspace) {
			return;
		}
		await updateRepositoryVisibility.mutateAsync({
			path: { workspaceSlug: slug, teamId, repositoryId },
			body: hidden,
			query: { hiddenFromContributions: hidden },
		});
	};

	if (!hasWorkspace && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	return (
		<AdminTeamsTable
			teams={teamsQuery.data || []}
			isLoading={isWorkspaceLoading || teamsQuery.isLoading || !workspaceSlug}
			onHideTeam={handleHideTeam}
			onToggleRepositoryVisibility={handleToggleRepositoryVisibility}
			onAddLabelToTeam={handleAddLabelToTeam}
			onRemoveLabelFromTeam={handleRemoveLabelFromTeam}
		/>
	);
}
