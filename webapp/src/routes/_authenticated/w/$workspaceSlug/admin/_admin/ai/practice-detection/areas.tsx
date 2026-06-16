import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	createAreaMutation,
	deleteAreaMutation,
	listAreasOptions,
	listAreasQueryKey,
	listPracticesOptions,
	listPracticesQueryKey,
	reorderAreasMutation,
	updateAreaMutation,
} from "@/api/@tanstack/react-query.gen";
import { generateSlug } from "@/components/admin/practices/constants";
import { PracticeAreasPanel } from "@/components/admin/practices/PracticeAreasPanel";
import { Spinner } from "@/components/ui/spinner";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection/areas",
)({
	component: PracticeAreasContainer,
});

function PracticeAreasContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	// All areas (including inactive) so an admin can re-activate; practices for the per-area counts.
	const areasQuery = useQuery({
		...listAreasOptions({ path: { workspaceSlug: slug } }),
		enabled: !!workspaceSlug,
	});
	const practicesQuery = useQuery({
		...listPracticesOptions({ path: { workspaceSlug: slug } }),
		enabled: !!workspaceSlug,
	});

	const invalidate = () => {
		queryClient.invalidateQueries({
			queryKey: listAreasQueryKey({ path: { workspaceSlug: slug } }),
		});
		queryClient.invalidateQueries({
			queryKey: listPracticesQueryKey({ path: { workspaceSlug: slug } }),
		});
	};

	const createArea = useMutation({
		...createAreaMutation(),
		onSuccess: () => {
			invalidate();
			toast.success("Area created");
		},
		onError: (error) => {
			const status =
				typeof error === "object" && error !== null && "status" in error
					? (error as { status: number }).status
					: undefined;
			toast.error(
				status === 409 ? "A area with that name already exists" : "Failed to create area",
			);
		},
	});
	const updateArea = useMutation({
		...updateAreaMutation(),
		onSuccess: () => invalidate(),
		onError: () => toast.error("Failed to update area"),
	});
	const deleteArea = useMutation({
		...deleteAreaMutation(),
		onSuccess: () => {
			invalidate();
			toast.success("Area deleted");
		},
		onError: () => toast.error("Failed to delete area"),
	});
	const reorderAreas = useMutation({
		...reorderAreasMutation(),
		onSuccess: () => invalidate(),
		onError: () => toast.error("Failed to reorder areas"),
	});

	if (!workspaceSlug || areasQuery.isLoading || practicesQuery.isLoading) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	const isMutating =
		createArea.isPending || updateArea.isPending || deleteArea.isPending || reorderAreas.isPending;

	return (
		<PracticeAreasPanel
			areas={areasQuery.data ?? []}
			practices={practicesQuery.data ?? []}
			isMutating={isMutating}
			onCreate={(name) =>
				createArea.mutate({
					path: { workspaceSlug: slug },
					body: { slug: generateSlug(name), name },
				})
			}
			onRename={(areaSlug, name) =>
				updateArea.mutate({ path: { workspaceSlug: slug, areaSlug }, body: { name } })
			}
			onToggleActive={(areaSlug, active) =>
				updateArea.mutate({ path: { workspaceSlug: slug, areaSlug }, body: { active } })
			}
			onDelete={(areaSlug) => deleteArea.mutate({ path: { workspaceSlug: slug, areaSlug } })}
			onReorder={(orderedSlugs) =>
				reorderAreas.mutate({ path: { workspaceSlug: slug }, body: { orderedSlugs } })
			}
		/>
	);
}
