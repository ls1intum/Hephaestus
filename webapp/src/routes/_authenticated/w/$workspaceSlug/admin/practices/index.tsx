import { useQuery } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { ListChecks } from "lucide-react";
import { useState } from "react";
import {
	getPracticeEvidenceOptionsOptions,
	listAreasOptions,
	listPracticesOptions,
} from "@/api/@tanstack/react-query.gen";
import type { Practice, PracticeArea } from "@/api/types.gen";
import { generateSlug } from "@/components/admin/practice-catalog/constants";
import { type FocusFilter, PracticeCatalog } from "@/components/admin/practices/PracticeCatalog";
import {
	PRACTICE_SEARCH_PARAMS,
	practiceSearchSchema,
} from "@/components/admin/practices/practice-search";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
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

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/")({
	head: workspaceAdminHead("Practices"),
	validateSearch: practiceSearchSchema,
	search: { middlewares: [retainSearchParams(PRACTICE_SEARCH_PARAMS)] },
	component: PracticeCatalogRoute,
});

function PracticeCatalogRoute() {
	const { workspaceSlug } = Route.useParams();
	const { focus } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const [deletingArea, setDeletingArea] = useState<PracticeArea | null>(null);
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);
	const catalog = usePracticeCatalogMutations(workspaceSlug);

	const areasQuery = useQuery({
		...listAreasOptions({ path: { workspaceSlug } }),
	});
	const practicesQuery = useQuery({
		...listPracticesOptions({ path: { workspaceSlug } }),
	});
	const evidenceOptionsQuery = useQuery({
		...getPracticeEvidenceOptionsOptions({ path: { workspaceSlug } }),
	});

	return (
		<PageLayout>
			<PageHeader
				icon={<ListChecks />}
				title="Practices"
				description="Choose the practices Hephaestus uses for new reviews in this workspace. Changes affect only this workspace."
			/>
			{areasQuery.isPending || practicesQuery.isPending || evidenceOptionsQuery.isPending ? (
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			) : areasQuery.isError || practicesQuery.isError || evidenceOptionsQuery.isError ? (
				<QueryErrorAlert
					error={areasQuery.error ?? practicesQuery.error ?? evidenceOptionsQuery.error}
					title="Couldn't load practices"
					onRetry={() => {
						areasQuery.refetch();
						practicesQuery.refetch();
						evidenceOptionsQuery.refetch();
					}}
				/>
			) : (
				<PracticeCatalog
					workspaceSlug={workspaceSlug}
					areas={areasQuery.data}
					practices={practicesQuery.data}
					evidenceOptions={evidenceOptionsQuery.data}
					pending={{
						areaSlugs: catalog.pendingAreaSlugs,
						practiceSlugs: catalog.pendingPracticeSlugs,
						areaStructure: catalog.areaStructurePending,
						blockedMoveDestinationSlugs: catalog.blockedMoveDestinationSlugs,
						blockedPracticeOrderBuckets: catalog.blockedPracticeOrderBuckets,
						creatingArea: catalog.createArea.isPending,
					}}
					focusFilter={focus ?? "ALL"}
					onFocusFilterChange={(next: FocusFilter) =>
						navigate({
							search: { focus: next === "ALL" ? undefined : next },
						})
					}
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
					onSetAreaDashboardVisibility={(areaSlug, visibleInPracticeDashboards) =>
						catalog.updateArea.mutate({
							path: { workspaceSlug, areaSlug },
							body: { visibleInPracticeDashboards },
						})
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
					onSetPracticeUsedInNewReviews={(practiceSlug, usedInNewReviews) =>
						catalog.setUsedInNewReviews.mutate({
							path: { workspaceSlug, practiceSlug },
							body: { usedInNewReviews },
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
			)}

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
						<AlertDialogCancel>Cancel</AlertDialogCancel>
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
							This permanently deletes the practice and its findings. This can't be undone.
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
		</PageLayout>
	);
}
