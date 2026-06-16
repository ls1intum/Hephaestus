import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	bindAreaMutation,
	getPracticeOptions,
	getPracticeQueryKey,
	listAreasOptions,
	listPracticesQueryKey,
	updatePracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { UpdatePracticeRequest } from "@/api/types.gen";
import { PracticeForm } from "@/components/admin/practices/PracticeForm";
import { Spinner } from "@/components/ui/spinner";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection/catalog/$practiceSlug",
)({
	component: EditPracticeContainer,
});

function EditPracticeContainer() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { practiceSlug } = Route.useParams();
	const { workspaceSlug } = useActiveWorkspaceSlug();

	const practiceQueryOptions = getPracticeOptions({
		path: { workspaceSlug: workspaceSlug ?? "", practiceSlug },
	});
	const { data: practice, isLoading } = useQuery({
		...practiceQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const areasQuery = useQuery({
		...listAreasOptions({
			path: { workspaceSlug: workspaceSlug ?? "" },
			query: { activeOnly: true },
		}),
		enabled: Boolean(workspaceSlug),
	});

	const updatePractice = useMutation(updatePracticeMutation());
	const bindArea = useMutation(bindAreaMutation());

	const handleSubmit = async (
		slug: string,
		data: UpdatePracticeRequest,
		areaSlug: string | null,
	) => {
		if (!workspaceSlug) return;
		try {
			await updatePractice.mutateAsync({ path: { workspaceSlug, practiceSlug: slug }, body: data });
			// Only re-bind when the selection actually changed (binding is a distinct endpoint).
			if ((practice?.areaSlug ?? null) !== areaSlug) {
				await bindArea.mutateAsync({
					path: { workspaceSlug, practiceSlug: slug },
					body: { areaSlug: areaSlug ?? undefined },
				});
			}
			queryClient.invalidateQueries({
				queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
			});
			queryClient.invalidateQueries({
				queryKey: getPracticeQueryKey({ path: { workspaceSlug, practiceSlug: slug } }),
			});
			toast.success("Practice updated successfully");
			navigate({ to: ".." });
		} catch {
			toast.error("Failed to update practice");
		}
	};

	const handleCancel = () => {
		navigate({ to: ".." });
	};

	// Wait for areas too: the area <Select> resolves its display label from the matching option, so
	// the options must exist before the bound value is set (otherwise it falls back to the raw slug).
	if (isLoading || !practice || !areasQuery.data) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	return (
		<PracticeForm
			mode="edit"
			initialData={practice}
			areas={areasQuery.data}
			onSubmit={handleSubmit}
			onCancel={handleCancel}
			isPending={updatePractice.isPending || bindArea.isPending}
		/>
	);
}
