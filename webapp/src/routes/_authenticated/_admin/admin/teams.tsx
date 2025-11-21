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
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/_admin/admin/teams")({
	component: AdminTeamsContainer,
});

function AdminTeamsContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug, isLoading: isWorkspaceLoading } =
		useActiveWorkspaceSlug();

	// Query for teams data
	const teamsQuery = useQuery(getAllTeamsOptions({}));

	// Mutations
	const updateTeamVisibility = useMutation({
		...updateTeamVisibilityMutation(),
		onMutate: async (vars: Options<UpdateTeamVisibilityData>) => {
			await queryClient.cancelQueries({ queryKey: getAllTeamsQueryKey() });
			const key = getAllTeamsQueryKey();
			const prev = queryClient.getQueryData<TeamInfo[]>(key);
			const teamId = vars.path?.id;
			const hidden =
				typeof vars.body === "boolean" ? vars.body : vars.query?.hidden;
			if (prev && typeof teamId === "number" && typeof hidden === "boolean") {
				const next = prev.map((t) => (t.id === teamId ? { ...t, hidden } : t));
				queryClient.setQueryData(key, next);
			}
			return { prev } as { prev: TeamInfo[] | undefined };
		},
		onError: (_err, _vars, ctx) => {
			const key = getAllTeamsQueryKey();
			if (ctx?.prev) {
				queryClient.setQueryData(key, ctx.prev);
			}
		},
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: getAllTeamsQueryKey() });
		},
	});

	const addLabelToTeam = useMutation({
		...addLabelToTeamMutation(),
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: getAllTeamsQueryKey() });
		},
	});

	const removeLabelFromTeam = useMutation({
		...removeLabelFromTeamMutation(),
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: getAllTeamsQueryKey() });
		},
	});

	const updateRepositoryVisibility = useMutation({
		...updateRepositoryVisibilityMutation(),
		onMutate: async (vars: Options<UpdateRepositoryVisibilityData>) => {
			await queryClient.cancelQueries({ queryKey: getAllTeamsQueryKey() });
			const key = getAllTeamsQueryKey();
			const prev = queryClient.getQueryData<TeamInfo[]>(key);
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
				queryClient.setQueryData(key, next);
			}
			return { prev } as { prev: TeamInfo[] | undefined };
		},
		onError: (_err, _vars, ctx) => {
			const key = getAllTeamsQueryKey();
			if (ctx?.prev) {
				queryClient.setQueryData(key, ctx.prev);
			}
		},
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: getAllTeamsQueryKey() });
		},
	});

	// Handler functions
	const handleHideTeam = async (teamId: number, hidden: boolean) => {
		await updateTeamVisibility.mutateAsync({
			path: { id: teamId },
			body: hidden,
			query: { hidden },
		});
	};

	const handleAddLabelToTeam = async (
		teamId: number,
		repositoryId: number,
		label: string,
	) => {
		if (!workspaceSlug) {
			return;
		}
		await addLabelToTeam.mutateAsync({
			path: { workspaceSlug, teamId, repositoryId, label },
		});
	};

	const handleRemoveLabelFromTeam = async (teamId: number, labelId: number) => {
		if (!workspaceSlug) {
			return;
		}
		await removeLabelFromTeam.mutateAsync({
			path: { workspaceSlug, teamId, labelId },
		});
	};

	const handleToggleRepositoryVisibility = async (
		teamId: number,
		repositoryId: number,
		hidden: boolean,
	) => {
		await updateRepositoryVisibility.mutateAsync({
			path: { teamId, repositoryId },
			body: hidden,
			query: { hiddenFromContributions: hidden },
		});
	};

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
