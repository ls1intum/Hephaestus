import { useMutation, useQueries, useQuery } from "@tanstack/react-query";
import { createFileRoute, Link, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { ListChecks } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	adoptAreaMutation,
	adoptPracticeMutation,
	getPracticeDefinitionOptionsOptions,
	getPracticeOptions,
	listAdoptablePracticesOptions,
	listAreasOptions,
	listPracticesOptions,
	previewAreaAdoptionOptions,
	previewPracticeAdoptionOptions,
} from "@/api/@tanstack/react-query.gen";
import type {
	CatalogAreaAdoptionPreview,
	CatalogPracticePreview,
	Practice,
	PracticeArea,
} from "@/api/types.gen";
import { AreaAdoptionPanel } from "@/components/admin/practice-adoption/AreaAdoptionPanel";
import { PracticeAdoptionPanel } from "@/components/admin/practice-adoption/PracticeAdoptionPanel";
import { generateSlug } from "@/components/admin/practice-catalog/constants";
import { type FocusFilter, PracticeCatalog } from "@/components/admin/practices/PracticeCatalog";
import {
	type DETAIL_LEVEL_KINDS,
	PRACTICE_SEARCH_PARAMS,
	practiceSetupSearchSchema,
} from "@/components/admin/practices/practice-search";
import { WorkspacePracticePanel } from "@/components/admin/practices/WorkspacePracticePanel";
import { LoadingBlock } from "@/components/common/LoadingBlock";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import { detailStackKey, parseDetailStack } from "@/components/core/detail-drawer/detail-stack";
import { useDetailStack } from "@/components/core/detail-drawer/use-detail-stack";
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
import { practiceCatalogStructureScope } from "@/hooks/practice-catalog-cache";
import { usePracticeCatalogMutations } from "@/hooks/use-practice-catalog-mutations";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/")({
	head: workspaceAdminHead("Practices"),
	validateSearch: practiceSetupSearchSchema,
	search: { middlewares: [retainSearchParams(PRACTICE_SEARCH_PARAMS)] },
	component: PracticeCatalogRoute,
});

function PracticeCatalogRoute() {
	const { workspaceSlug } = Route.useParams();
	const { focus, library, detail } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const [deletingArea, setDeletingArea] = useState<PracticeArea | null>(null);
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);
	const [staleLevelKey, setStaleLevelKey] = useState<string | null>(null);
	const catalog = usePracticeCatalogMutations(workspaceSlug);

	// Every open level owns its own preview query, keyed by that level's slug. Sharing one query per
	// kind would let `?detail=practice:a&detail=practice:b` show a's definition while adding b.
	const detailStack = parseDetailStack<(typeof DETAIL_LEVEL_KINDS)[number]>(detail);
	const stackControls = useDetailStack(detailStack);

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
	const levelQueries = useQueries({
		queries: detailStack.map((entry) => {
			if (entry.kind === "catalog-area") {
				return previewAreaAdoptionOptions({ path: { workspaceSlug, slug: entry.id } });
			}
			if (entry.kind === "catalog-practice") {
				return previewPracticeAdoptionOptions({ path: { workspaceSlug, slug: entry.id } });
			}
			return getPracticeOptions({ path: { workspaceSlug, practiceSlug: entry.id } });
		}),
	});

	const refreshCatalog = () =>
		Promise.all([
			areasQuery.refetch(),
			practicesQuery.refetch(),
			catalogQuery.refetch(),
			// A practice added from inside an area drawer changes what the area behind it would do.
			...levelQueries
				.filter((_query, index) => detailStack[index]?.kind === "catalog-area")
				.map((q) => q.refetch()),
		]);
	const adoptCatalogArea = useMutation({
		...adoptAreaMutation(),
		onSuccess: async (result) => {
			stackControls.close(0);
			await refreshCatalog();
			const changes = [
				result.added.length > 0 && `${result.added.length} added`,
				result.moved.length > 0 && `${result.moved.length} moved`,
			].filter(Boolean);
			toast.success("Area updated", { description: changes.join(", ") });
		},
		onError: (error) => {
			toast.error(
				problemStatusOf(error) === 412
					? "The library changed before the area was added. Review the current contents."
					: "Couldn't add the area. Nothing was changed.",
			);
		},
	});
	const adoptCatalogPractice = useMutation({
		...adoptPracticeMutation(),
		scope: practiceCatalogStructureScope(workspaceSlug),
	});

	// Adding closes only the practice level, so an administrator lands back in the library they were
	// working through rather than in an edit form they did not ask for.
	const adoptReviewedPractice = async (depth: number) => {
		const entry = detailStack[depth];
		const query = levelQueries[depth];
		const preview = query?.data as CatalogPracticePreview | undefined;
		if (!entry || !preview) return;
		setStaleLevelKey(null);
		try {
			await adoptCatalogPractice.mutateAsync({
				path: { workspaceSlug, slug: entry.id },
				headers: { "If-Match": preview.etag },
			});
			stackControls.close(depth);
			await refreshCatalog();
			toast.success("Practice added", { description: preview.definition.name });
		} catch (error) {
			const status = problemStatusOf(error);
			if (status === 409) {
				await refreshCatalog();
				toast.info("This practice is already in the workspace");
				stackControls.close(depth);
				return;
			}
			if (status === 412) {
				const refreshed = await query.refetch();
				if (refreshed.isSuccess) setStaleLevelKey(detailStackKey(entry));
				else toast.error("The adoption preview changed but couldn't be refreshed");
				return;
			}
			toast.error("Couldn't add the practice");
		}
	};

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
				<LoadingBlock size="lg" label="Loading practices" />
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
					library={
						catalogQuery.isError
							? {
									status: "error",
									error: catalogQuery.error,
									onRetry: () => catalogQuery.refetch(),
								}
							: catalogQuery.data
								? { status: "ready", practices: catalogQuery.data }
								: { status: "loading" }
					}
					showLibrary={library === true}
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

			<DetailDrawerStack stack={detailStack} onClose={stackControls.close}>
				{(entry, level) => {
					const query = levelQueries[level.depth];
					if (entry.kind === "catalog-area") {
						return (
							<AreaAdoptionPanel
								nested={level.nested}
								state={
									query.isPending
										? { status: "loading" }
										: query.isError
											? { status: "error", error: query.error, onRetry: () => query.refetch() }
											: {
													status: "ready",
													preview: query.data as CatalogAreaAdoptionPreview,
													adding: adoptCatalogArea.isPending,
												}
								}
								onOpenPractice={(catalogSlug) =>
									stackControls.open({ kind: "catalog-practice", id: catalogSlug })
								}
								onConfirm={() => {
									const preview = query.data as CatalogAreaAdoptionPreview | undefined;
									if (!preview) return;
									adoptCatalogArea.mutate({
										path: { workspaceSlug, slug: entry.id },
										headers: { "If-Match": preview.etag },
									});
								}}
							/>
						);
					}
					if (entry.kind === "practice") {
						return (
							<WorkspacePracticePanel
								workspaceSlug={workspaceSlug}
								nested={level.nested}
								state={
									query.isPending || definitionOptionsQuery.isPending
										? { status: "loading" }
										: query.isError || definitionOptionsQuery.isError
											? {
													status: "error",
													error: query.error ?? definitionOptionsQuery.error,
													onRetry: () => {
														query.refetch();
														definitionOptionsQuery.refetch();
													},
												}
											: {
													status: "ready",
													practice: query.data as Practice,
													definitionOptions: definitionOptionsQuery.data,
													areaName: areasQuery.data?.find(
														(area) => area.slug === (query.data as Practice).areaSlug,
													)?.name,
												}
								}
							/>
						);
					}
					return (
						<PracticeAdoptionPanel
							nested={level.nested}
							state={
								query.isPending || definitionOptionsQuery.isPending
									? { status: "loading" }
									: query.isError || definitionOptionsQuery.isError
										? {
												status: "error",
												error: query.error ?? definitionOptionsQuery.error,
												onRetry: () => {
													query.refetch();
													definitionOptionsQuery.refetch();
												},
											}
										: {
												status: "ready",
												preview: query.data as CatalogPracticePreview,
												definitionOptions: definitionOptionsQuery.data,
												action:
													staleLevelKey === detailStackKey(entry)
														? "stale"
														: adoptCatalogPractice.isPending
															? "adding"
															: "idle",
											}
							}
							onAdopt={() => adoptReviewedPractice(level.depth)}
						/>
					);
				}}
			</DetailDrawerStack>

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
