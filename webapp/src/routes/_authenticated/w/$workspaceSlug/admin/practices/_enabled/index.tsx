import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { listAreasOptions, listPracticesOptions } from "@/api/@tanstack/react-query.gen";
import type { Practice, PracticeArea } from "@/api/types.gen";
import { generateSlug } from "@/components/admin/practices/constants";
import { type FocusFilter, PracticeCatalog } from "@/components/admin/practices/PracticeCatalog";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
import { usePracticeCatalogMutations } from "@/hooks/use-practice-catalog-mutations";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/_enabled/")({
	head: workspaceAdminHead("Practice catalog"),
	component: PracticeCatalogRoute,
});

function PracticeCatalogRoute() {
	const { workspaceSlug } = Route.useParams();

	const [focusFilter, setFocusFilter] = useState<FocusFilter>("ALL");
	const [deletingArea, setDeletingArea] = useState<PracticeArea | null>(null);
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);
	const catalog = usePracticeCatalogMutations(workspaceSlug);

	const areasQuery = useQuery({
		...listAreasOptions({ path: { workspaceSlug } }),
	});
	const practicesQuery = useQuery({
		...listPracticesOptions({ path: { workspaceSlug } }),
	});

	if (areasQuery.isPending || practicesQuery.isPending) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spinner className="size-8" />
			</div>
		);
	}
	if (areasQuery.isError || practicesQuery.isError) {
		return (
			<div className="mx-auto w-full max-w-5xl">
				<QueryErrorAlert
					error={areasQuery.error ?? practicesQuery.error}
					title="Couldn't load the practice catalog"
					onRetry={() => {
						areasQuery.refetch();
						practicesQuery.refetch();
					}}
				/>
			</div>
		);
	}

	return (
		<div className="mx-auto w-full max-w-5xl space-y-6">
			<header className="space-y-1">
				<h1 className="text-3xl font-bold tracking-tight">Practice catalog</h1>
				<p className="max-w-2xl text-muted-foreground">
					Organize the standards Hephaestus uses to review contributions.
				</p>
			</header>

			<PracticeCatalog
				workspaceSlug={workspaceSlug}
				areas={areasQuery.data}
				practices={practicesQuery.data}
				pending={{
					areaSlugs: catalog.pendingAreaSlugs,
					practiceSlugs: catalog.pendingPracticeSlugs,
					areaStructure: catalog.areaStructurePending,
					blockedMoveDestinationSlugs: catalog.blockedMoveDestinationSlugs,
					blockedPracticeOrderBuckets: catalog.blockedPracticeOrderBuckets,
					creatingArea: catalog.createArea.isPending,
				}}
				focusFilter={focusFilter}
				onFocusFilterChange={setFocusFilter}
				onCreateArea={async (name) => {
					try {
						await catalog.createArea.mutateAsync({
							path: { workspaceSlug },
							body: { slug: generateSlug(name), name },
						});
						return true;
					} catch {
						return false;
					}
				}}
				onRenameArea={async (areaSlug, name) => {
					try {
						await catalog.updateArea.mutateAsync({
							path: { workspaceSlug, areaSlug },
							body: { name },
						});
						return true;
					} catch {
						return false;
					}
				}}
				onToggleAreaActive={(areaSlug, active) =>
					catalog.updateArea.mutate({ path: { workspaceSlug, areaSlug }, body: { active } })
				}
				onDeleteArea={(areaSlug) =>
					setDeletingArea(areasQuery.data?.find((area) => area.slug === areaSlug) ?? null)
				}
				onReorderAreas={(orderedSlugs) =>
					catalog.reorderAreas.mutate({ path: { workspaceSlug }, body: { orderedSlugs } })
				}
				onSetAreaVisual={(areaSlug, patch) =>
					catalog.updateArea.mutate({ path: { workspaceSlug, areaSlug }, body: patch })
				}
				onSetPracticeActive={(practiceSlug, active) =>
					catalog.setActive.mutate({
						path: { workspaceSlug, practiceSlug },
						body: { active },
					})
				}
				onDeletePractice={setDeletingPractice}
				onPlacePractice={(practiceSlug, areaSlug, position) =>
					catalog.placePractice.mutate({
						path: { workspaceSlug, practiceSlug },
						body: { areaSlug: areaSlug ?? undefined, position },
					})
				}
			/>

			<AlertDialog
				open={deletingArea !== null}
				onOpenChange={(open) => {
					if (!open) setDeletingArea(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Delete “{deletingArea?.name}”?</AlertDialogTitle>
						<AlertDialogDescription>
							Practices in this area will move to Unassigned. The practices themselves won't be
							deleted.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Keep area</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							className="min-w-24"
							disabled={catalog.deleteArea.isPending}
							onClick={() => {
								if (!deletingArea) return;
								catalog.deleteArea.mutate(
									{ path: { workspaceSlug, areaSlug: deletingArea.slug } },
									{ onSuccess: () => setDeletingArea(null) },
								);
							}}
						>
							{catalog.deleteArea.isPending ? "Deleting…" : "Delete area"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

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
							variant="destructive"
							className="min-w-28"
							onClick={() => {
								if (deletingPractice)
									catalog.deletePractice.mutate(
										{
											path: { workspaceSlug, practiceSlug: deletingPractice.slug },
										},
										{ onSuccess: () => setDeletingPractice(null) },
									);
							}}
							disabled={catalog.deletePractice.isPending}
						>
							{catalog.deletePractice.isPending ? "Deleting…" : "Delete practice"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
