import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
	addLabelToTeamMutation,
	getAllTeamsOptions,
	getAllTeamsQueryKey,
	removeLabelFromTeamMutation,
	updateTeamVisibilityMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Options } from "@/api/sdk.gen";
import type { TeamInfo, UpdateTeamVisibilityData } from "@/api/types.gen";
import { AdminTeamsTable } from "@/components/admin/AdminTeamsTable";

export const Route = createFileRoute("/_authenticated/_admin/admin/teams")({
	component: AdminTeamsContainer,
});

function AdminTeamsContainer() {
	const queryClient = useQueryClient();

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
			const hidden = vars.body;
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

	// Handler functions
	const handleHideTeam = async (teamId: number, hidden: boolean) => {
		await updateTeamVisibility.mutateAsync({
			path: { id: teamId },
			body: hidden,
		});
	};

	const handleAddLabelToTeam = async (
		teamId: number,
		repositoryId: number,
		label: string,
	) => {
		await addLabelToTeam.mutateAsync({
			path: { teamId, repositoryId, label },
		});
	};

	const handleRemoveLabelFromTeam = async (teamId: number, labelId: number) => {
		await removeLabelFromTeam.mutateAsync({
			path: { teamId, labelId },
		});
	};

	return (
		<AdminTeamsTable
			teams={teamsQuery.data || []}
			isLoading={teamsQuery.isLoading}
			onHideTeam={handleHideTeam}
			onAddLabelToTeam={handleAddLabelToTeam}
			onRemoveLabelFromTeam={handleRemoveLabelFromTeam}
		/>
	);
}
