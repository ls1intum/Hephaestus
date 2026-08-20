import { useMutation, useQuery } from "@tanstack/react-query";
import { createFileRoute, Link, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { ListChecks } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	adoptAreaMutation,
	getPracticeDefinitionOptionsOptions,
	listAdoptablePracticesOptions,
	listAreasOptions,
	listPracticesOptions,
	previewAreaAdoptionOptions,
} from "@/api/@tanstack/react-query.gen";
import type { Practice, PracticeArea } from "@/api/types.gen";
import { CatalogAreaAdoptionDialog } from "@/components/admin/practice-adoption/CatalogAreaAdoptionDialog";
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
import { problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/")({
	head: workspaceAdminHead("Practices"),
	validateSearch: practiceSearchSchema,
	search: { middlewares: [retainSearchParams(PRACTICE_SEARCH_PARAMS)] },
	component: PracticeCatalogRoute,
});

function PracticeCatalogRoute() {
	const { workspaceSlug } = Route.useParams();
	const { focus, library } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const [deletingArea, setDeletingArea] = useState<PracticeArea | null>(null);
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);
	const [reviewingAreaSlug, setReviewingAreaSlug] = useState<string | null>(null);
	const catalog = usePracticeCatalogMutations(workspaceSlug);

	const areasQuery = useQuery({
		...listAreasOptions({ path: { workspaceSlug } }),
	});
	const practicesQuery = useQuery({
		...listPracticesOptions({ path: { workspaceSlug } }),
	});
	const definitionOptionsQuery = useQuery({
		...getPracticeDefinitionOptionsOptions({ path: { workspaceSlug } }),
	});
	const catalogQuery = useQuery({
		...listAdoptablePracticesOptions({ path: { workspaceSlug } }),
		enabled: library === true,
	});
	const areaPreviewQuery = useQuery({
		...previewAreaAdoptionOptions({ path: { workspaceSlug, slug: reviewingAreaSlug ?? "" } }),
		enabled: reviewingAreaSlug !== null,
	});
	const adoptCatalogArea = useMutation({
		...adoptAreaMutation(),
		onSuccess: async (result) => {
			setReviewingAreaSlug(null);
			await Promise.all([areasQuery.refetch(), practicesQuery.refetch(), catalogQuery.refetch()]);
			const changes = [
				result.added.length > 0 && `${result.added.length} added`,
				result.moved.length > 0 && `${result.moved.length} moved`,
			].filter(Boolean);
			toast.success("Area updated", { description: changes.join(", ") });
		},
		onError: (error) => {
			void areaPreviewQuery.refetch();
			toast.error(
				problemStatusOf(error) === 412
					? "The library changed before the area was added. Review the current contents."
					: "Couldn't add the area. Nothing was changed.",
			);
		},
	});

	return (
		<PageLayout>
			<PageHeader
				icon={<ListChecks />}
				title="Practice setup"
				description={
					<>
						Organize this workspace's practices and add suggestions from the instance catalog. The
						autonomy — whether each practice is reviewed, and how far its reviews go on their own —
						is set on{" "}
						<Link
							to="/w/$workspaceSlug/admin/practices/review"
							params={{ workspaceSlug }}
							search={{}}
							className="font-medium underline underline-offset-4 hover:text-foreground"
						>
							Review
						</Link>
						.
					</>
				}
			/>
			{areasQuery.isPending || practicesQuery.isPending || definitionOptionsQuery.isPending ? (
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			) : areasQuery.isError || practicesQuery.isError || definitionOptionsQuery.isError ? (
				<QueryErrorAlert
					error={areasQuery.error ?? practicesQuery.error ?? definitionOptionsQuery.error}
					title="Couldn't load practices"
					onRetry={() => {
						areasQuery.refetch();
						practicesQuery.refetch();
						definitionOptionsQuery.refetch();
					}}
				/>
			) : (
				<PracticeCatalog
					workspaceSlug={workspaceSlug}
					areas={areasQuery.data}
					practices={practicesQuery.data}
					definitionOptions={definitionOptionsQuery.data}
					pending={{
						areaSlugs: catalog.pendingAreaSlugs,
						practiceSlugs: catalog.pendingPracticeSlugs,
						areaStructure: catalog.areaStructurePending,
						blockedMoveDestinationSlugs: catalog.blockedMoveDestinationSlugs,
						blockedPracticeOrderBuckets: catalog.blockedPracticeOrderBuckets,
						creatingArea: catalog.createArea.isPending,
					}}
					focusFilter={focus ?? "ALL"}
					catalogPractices={catalogQuery.data}
					catalogUnavailable={catalogQuery.isError}
					onRetryCatalog={() => catalogQuery.refetch()}
					showLibrary={library === true}
					onReviewCatalogArea={setReviewingAreaSlug}
					onShowLibraryChange={(showLibrary) =>
						navigate({
							search: (previous) => ({ ...previous, library: showLibrary || undefined }),
						})
					}
					onFocusFilterChange={(next: FocusFilter) =>
						navigate({
							search: (previous) => ({
								...previous,
								focus: next === "ALL" ? undefined : next,
							}),
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
					onDeletePractice={setDeletingPractice}
					onPlacePractice={(practiceSlug, areaSlug, position) =>
						catalog.placePractice.mutate({
							path: { workspaceSlug, practiceSlug },
							body: { areaSlug: areaSlug ?? undefined, position },
						})
					}
				/>
			)}

			<CatalogAreaAdoptionDialog
				definitionOptions={
					definitionOptionsQuery.data ?? { sourceContractVersion: "", workTypes: [] }
				}
				open={reviewingAreaSlug !== null}
				preview={areaPreviewQuery.data}
				isLoading={areaPreviewQuery.isPending}
				isError={areaPreviewQuery.isError}
				isPending={adoptCatalogArea.isPending}
				onOpenChange={(open) => {
					if (!open) setReviewingAreaSlug(null);
				}}
				onRetry={() => areaPreviewQuery.refetch()}
				onConfirm={() => {
					if (!reviewingAreaSlug || !areaPreviewQuery.data) return;
					adoptCatalogArea.mutate({
						path: { workspaceSlug, slug: reviewingAreaSlug },
						headers: { "If-Match": areaPreviewQuery.data.etag },
					});
				}}
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
							Choose whether to keep this area's practices in the workspace or delete them with the
							area. Deleting practices also permanently deletes their observations.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter className="sm:grid sm:grid-cols-3">
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="outline"
							disabled={catalog.deleteArea.isPending}
							onClick={() => {
								if (!deletingArea) return;
								catalog.deleteArea.mutate(
									{ path: { workspaceSlug, areaSlug: deletingArea.slug } },
									{ onSuccess: () => setDeletingArea(null) },
								);
							}}
						>
							{catalog.deleteArea.isPending ? "Deleting…" : "Keep practices unassigned"}
						</AlertDialogAction>
						<AlertDialogAction
							variant="destructive"
							disabled={catalog.deleteArea.isPending}
							onClick={() => {
								if (!deletingArea) return;
								catalog.deleteArea.mutate(
									{
										path: { workspaceSlug, areaSlug: deletingArea.slug },
										query: { deletePractices: true },
									},
									{ onSuccess: () => setDeletingArea(null) },
								);
							}}
						>
							{catalog.deleteArea.isPending ? "Deleting…" : "Delete area and practices"}
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
							This permanently deletes the practice and its observations. This can't be undone.
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
