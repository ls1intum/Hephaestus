import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	createGoalMutation,
	deleteGoalMutation,
	listGoalsOptions,
	listGoalsQueryKey,
	listPracticesOptions,
	listPracticesQueryKey,
	reorderGoalsMutation,
	updateGoalMutation,
} from "@/api/@tanstack/react-query.gen";
import { generateSlug } from "@/components/admin/practices/constants";
import { PracticeGoalsPanel } from "@/components/admin/practices/PracticeGoalsPanel";
import { Spinner } from "@/components/ui/spinner";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection/goals",
)({
	component: PracticeGoalsContainer,
});

function PracticeGoalsContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	// All goals (including inactive) so an admin can re-activate; practices for the per-goal counts.
	const goalsQuery = useQuery({
		...listGoalsOptions({ path: { workspaceSlug: slug } }),
		enabled: !!workspaceSlug,
	});
	const practicesQuery = useQuery({
		...listPracticesOptions({ path: { workspaceSlug: slug } }),
		enabled: !!workspaceSlug,
	});

	const invalidate = () => {
		queryClient.invalidateQueries({
			queryKey: listGoalsQueryKey({ path: { workspaceSlug: slug } }),
		});
		queryClient.invalidateQueries({
			queryKey: listPracticesQueryKey({ path: { workspaceSlug: slug } }),
		});
	};

	const createGoal = useMutation({
		...createGoalMutation(),
		onSuccess: () => {
			invalidate();
			toast.success("Goal created");
		},
		onError: (error) => {
			const status =
				typeof error === "object" && error !== null && "status" in error
					? (error as { status: number }).status
					: undefined;
			toast.error(
				status === 409 ? "A goal with that name already exists" : "Failed to create goal",
			);
		},
	});
	const updateGoal = useMutation({
		...updateGoalMutation(),
		onSuccess: () => invalidate(),
		onError: () => toast.error("Failed to update goal"),
	});
	const deleteGoal = useMutation({
		...deleteGoalMutation(),
		onSuccess: () => {
			invalidate();
			toast.success("Goal deleted");
		},
		onError: () => toast.error("Failed to delete goal"),
	});
	const reorderGoals = useMutation({
		...reorderGoalsMutation(),
		onSuccess: () => invalidate(),
		onError: () => toast.error("Failed to reorder goals"),
	});

	if (!workspaceSlug || goalsQuery.isLoading || practicesQuery.isLoading) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	const isMutating =
		createGoal.isPending || updateGoal.isPending || deleteGoal.isPending || reorderGoals.isPending;

	return (
		<PracticeGoalsPanel
			goals={goalsQuery.data ?? []}
			practices={practicesQuery.data ?? []}
			isMutating={isMutating}
			onCreate={(name) =>
				createGoal.mutate({
					path: { workspaceSlug: slug },
					body: { slug: generateSlug(name), name },
				})
			}
			onRename={(goalSlug, name) =>
				updateGoal.mutate({ path: { workspaceSlug: slug, goalSlug }, body: { name } })
			}
			onToggleActive={(goalSlug, active) =>
				updateGoal.mutate({ path: { workspaceSlug: slug, goalSlug }, body: { active } })
			}
			onDelete={(goalSlug) => deleteGoal.mutate({ path: { workspaceSlug: slug, goalSlug } })}
			onReorder={(orderedSlugs) =>
				reorderGoals.mutate({ path: { workspaceSlug: slug }, body: { orderedSlugs } })
			}
		/>
	);
}
