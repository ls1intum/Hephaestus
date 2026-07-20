import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	createAreaMutation,
	deleteAreaMutation,
	deletePracticeMutation,
	listAreasOptions,
	listAreasQueryKey,
	listPracticesOptions,
	listPracticesQueryKey,
	reorderAreasMutation,
	reorderPracticesMutation,
	setActiveMutation,
	updateAreaMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Practice } from "@/api/types.gen";
import { generateSlug } from "@/components/admin/practices/constants";
import { type FocusFilter, RubricTree } from "@/components/admin/practices/RubricTree";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Spinner } from "@/components/ui/spinner";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/")({
	component: RubricContainer,
});

function RubricContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	const [focusFilter, setFocusFilter] = useState<FocusFilter>("ALL");
	const [togglingPractices, setTogglingPractices] = useState<Set<string>>(new Set());
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);

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
			toast.success("Practice area created");
		},
		onError: (error) => {
			const status =
				typeof error === "object" && error !== null && "status" in error
					? (error as { status: number }).status
					: undefined;
			toast.error(
				status === 409
					? "A practice area with that name already exists"
					: "Failed to create practice area",
			);
		},
	});
	const updateArea = useMutation({
		...updateAreaMutation(),
		onSuccess: () => invalidate(),
		onError: () => toast.error("Failed to update practice area"),
	});
	const deleteArea = useMutation({
		...deleteAreaMutation(),
		onSuccess: () => {
			invalidate();
			toast.success("Practice area deleted");
		},
		onError: () => toast.error("Failed to delete practice area"),
	});
	const reorderAreas = useMutation({
		...reorderAreasMutation(),
		onSuccess: () => invalidate(),
		onError: () => toast.error("Failed to reorder practice areas"),
	});
	const reorderPractices = useMutation({
		...reorderPracticesMutation(),
		onSuccess: () => invalidate(),
		onError: () => toast.error("Failed to reorder practices"),
	});
	const deletePractice = useMutation({
		...deletePracticeMutation(),
		onSuccess: () => {
			invalidate();
			toast.success("Practice deleted");
		},
		onError: () => toast.error("Failed to delete practice"),
	});
	const setActive = useMutation({
		...setActiveMutation(),
		onSuccess: () => invalidate(),
		onError: (_e, variables) => toast.error(`Failed to toggle "${variables.path.practiceSlug}"`),
		onSettled: (_d, _e, variables) => {
			setTogglingPractices((prev) => {
				const next = new Set(prev);
				next.delete(variables.path.practiceSlug);
				return next;
			});
		},
	});

	if (!workspaceSlug || areasQuery.isLoading || practicesQuery.isLoading) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spinner className="size-8" />
			</div>
		);
	}

	const isMutating =
		createArea.isPending ||
		updateArea.isPending ||
		deleteArea.isPending ||
		reorderAreas.isPending ||
		reorderPractices.isPending;

	const handleSetPracticeActive = (practiceSlug: string, active: boolean) => {
		setTogglingPractices((prev) => new Set(prev).add(practiceSlug));
		setActive.mutate({ path: { workspaceSlug: slug, practiceSlug }, body: { active } });
	};

	return (
		<div className="container mx-auto max-w-5xl space-y-6 py-6">
			<header>
				<h1 className="text-3xl font-bold tracking-tight">Catalog</h1>
				<p className="text-muted-foreground">
					The practices this workspace evaluates, grouped into areas. Drag to reorder, toggle to
					enable, or open one to see its standard and the observations it produces.
				</p>
			</header>

			<RubricTree
				workspaceSlug={slug}
				areas={areasQuery.data ?? []}
				practices={practicesQuery.data ?? []}
				togglingPractices={togglingPractices}
				isMutating={isMutating}
				focusFilter={focusFilter}
				onFocusFilterChange={setFocusFilter}
				onCreateArea={(name) =>
					createArea.mutate({
						path: { workspaceSlug: slug },
						body: { slug: generateSlug(name), name },
					})
				}
				onRenameArea={(areaSlug, name) =>
					updateArea.mutate({ path: { workspaceSlug: slug, areaSlug }, body: { name } })
				}
				onToggleAreaActive={(areaSlug, active) =>
					updateArea.mutate({ path: { workspaceSlug: slug, areaSlug }, body: { active } })
				}
				onDeleteArea={(areaSlug) => deleteArea.mutate({ path: { workspaceSlug: slug, areaSlug } })}
				onReorderAreas={(orderedSlugs) =>
					reorderAreas.mutate({ path: { workspaceSlug: slug }, body: { orderedSlugs } })
				}
				onSetAreaVisual={(areaSlug, patch) =>
					updateArea.mutate({ path: { workspaceSlug: slug, areaSlug }, body: patch })
				}
				onSetPracticeActive={handleSetPracticeActive}
				onDeletePractice={setDeletingPractice}
				onReorderPractices={(areaSlug, orderedSlugs) =>
					reorderPractices.mutate({
						path: { workspaceSlug: slug },
						body: { areaSlug, orderedSlugs },
					})
				}
			/>

			<AlertDialog
				open={deletingPractice !== null}
				onOpenChange={(open) => {
					if (!open) setDeletingPractice(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Delete &ldquo;{deletingPractice?.name}&rdquo;?</AlertDialogTitle>
						<AlertDialogDescription>
							This permanently deletes the practice definition and its observations. This cannot be
							undone.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (deletingPractice)
									deletePractice
										.mutateAsync({
											path: { workspaceSlug: slug, practiceSlug: deletingPractice.slug },
										})
										.then(() => setDeletingPractice(null))
										.catch(() => {});
							}}
							disabled={deletePractice.isPending}
							className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
						>
							{deletePractice.isPending ? "Deleting…" : "Delete practice"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
