import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	bindGoalMutation,
	createPracticeMutation,
	listGoalsOptions,
	listPracticesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { CreatePracticeRequest } from "@/api/types.gen";
import { PracticeForm } from "@/components/admin/practices/PracticeForm";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection/catalog/new",
)({
	component: CreatePracticeContainer,
});

function CreatePracticeContainer() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();

	const goalsQuery = useQuery({
		...listGoalsOptions({
			path: { workspaceSlug: workspaceSlug ?? "" },
			query: { activeOnly: true },
		}),
		enabled: !!workspaceSlug,
	});

	const createPractice = useMutation(createPracticeMutation());
	const bindGoal = useMutation(bindGoalMutation());

	const handleSubmit = async (data: CreatePracticeRequest, goalSlug: string | null) => {
		if (!workspaceSlug) return;
		try {
			await createPractice.mutateAsync({ path: { workspaceSlug }, body: data });
			// Goal binding is a separate endpoint, so it runs after the practice exists.
			if (goalSlug) {
				await bindGoal.mutateAsync({
					path: { workspaceSlug, practiceSlug: data.slug },
					body: { goalSlug },
				});
			}
			queryClient.invalidateQueries({
				queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
			});
			toast.success("Practice created successfully");
			navigate({ to: ".." });
		} catch (error) {
			const status =
				typeof error === "object" && error !== null && "status" in error
					? (error as { status: number }).status
					: undefined;
			toast.error(
				status === 409
					? "A practice with this slug already exists in this workspace"
					: "Failed to create practice",
			);
		}
	};

	const handleCancel = () => {
		navigate({ to: ".." });
	};

	return (
		<PracticeForm
			mode="create"
			goals={goalsQuery.data ?? []}
			onSubmit={handleSubmit}
			onCancel={handleCancel}
			isPending={createPractice.isPending || bindGoal.isPending}
		/>
	);
}
