import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	bindAreaMutation,
	createPracticeMutation,
	listAreasOptions,
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

	const areasQuery = useQuery({
		...listAreasOptions({
			path: { workspaceSlug: workspaceSlug ?? "" },
			query: { activeOnly: true },
		}),
		enabled: !!workspaceSlug,
	});

	const createPractice = useMutation(createPracticeMutation());
	const bindArea = useMutation(bindAreaMutation());

	const handleSubmit = async (data: CreatePracticeRequest, areaSlug: string | null) => {
		if (!workspaceSlug) return;
		try {
			await createPractice.mutateAsync({ path: { workspaceSlug }, body: data });
			// Area binding is a separate endpoint, so it runs after the practice exists.
			if (areaSlug) {
				await bindArea.mutateAsync({
					path: { workspaceSlug, practiceSlug: data.slug },
					body: { areaSlug },
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
			areas={areasQuery.data ?? []}
			onSubmit={handleSubmit}
			onCancel={handleCancel}
			isPending={createPractice.isPending || bindArea.isPending}
		/>
	);
}
