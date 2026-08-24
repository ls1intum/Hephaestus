import { useMutation, useQueries, useQuery } from "@tanstack/react-query";
import { createFileRoute, Link, retainSearchParams } from "@tanstack/react-router";
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
	listPracticeEvidenceOutcomesOptions,
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
import { PracticeForm } from "@/components/admin/practices/PracticeForm";
import { PracticeFormLevel } from "@/components/admin/practices/PracticeFormLevel";
import {
	PracticeDefinitionSkeleton,
	PracticeTreeSkeleton,
} from "@/components/admin/practices/PracticeSkeletons";
import {
	DETAIL_LEVEL_KINDS,
	GUARDED_LEVEL_KINDS,
	PRACTICE_SEARCH_PARAMS,
	practiceSetupSearchSchema,
} from "@/components/admin/practices/practice-search";
import { WorkspacePracticePanel } from "@/components/admin/practices/WorkspacePracticePanel";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import { detailStackKey, parseDetailStack } from "@/components/core/detail-drawer/detail-stack";
import { LevelCancel } from "@/components/core/detail-drawer/LevelCancel";
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
import { DrawerBody } from "@/components/ui/drawer";
import { practiceCatalogStructureScope } from "@/hooks/practice-catalog-cache";
import { usePracticeCatalogMutations } from "@/hooks/use-practice-catalog-mutations";
import { usePracticeEditor } from "@/hooks/use-practice-editor";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemStatusOf } from "@/lib/problem-detail";
import { useSearchState } from "@/lib/search-params";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/")({
	head: workspaceAdminHead("Practices"),
	validateSearch: practiceSetupSearchSchema,
	search: { middlewares: [retainSearchParams(PRACTICE_SEARCH_PARAMS)] },
	component: PracticeCatalogRoute,
});

function PracticeCatalogRoute() {
	const { workspaceSlug } = Route.useParams();
	const { focus, library, detail } = Route.useSearch();
	const setSearch = useSearchState();

	const [deletingArea, setDeletingArea] = useState<PracticeArea | null>(null);
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);
	const [staleLevelKey, setStaleLevelKey] = useState<string | null>(null);
	const catalog = usePracticeCatalogMutations(workspaceSlug);
	const editor = usePracticeEditor(workspaceSlug);

	// Every open level owns its own preview query, keyed by that level's slug. Sharing one query per
	// kind would let `?detail=practice:a&detail=practice:b` show a's definition while adding b.
	const detailStack = parseDetailStack(detail, DETAIL_LEVEL_KINDS);
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
	// How past reviews turned out is context beside the form, not something the editor waits for.
	const evidenceOutcomesQuery = useQuery({
		...listPracticeEvidenceOutcomesOptions({ path: { workspaceSlug } }),
	});
	const catalogQuery = useQuery({
		...listAdoptablePracticesOptions({ path: { workspaceSlug } }),
		enabled: library === true,
	});
	// `useQueries` cannot correlate a result with the entry that produced it, so each payload is
	// tagged: without it every read is a union, and a mis-ordered stack is a runtime `undefined.name`
	// rather than a type error.
	const levelQueries = useQueries({
		queries: detailStack.map((entry) => {
			if (entry.kind === "catalog-area") {
				return {
					...previewAreaAdoptionOptions({ path: { workspaceSlug, slug: entry.id } }),
					select: (data: CatalogAreaAdoptionPreview) => ({ kind: "catalog-area", data }) as const,
				};
			}
			if (entry.kind === "catalog-practice") {
				return {
					...previewPracticeAdoptionOptions({ path: { workspaceSlug, slug: entry.id } }),
					select: (data: CatalogPracticePreview) => ({ kind: "catalog-practice", data }) as const,
				};
			}
			if (entry.kind === "practice-new") {
				// oxlint-disable-next-line hephaestus/no-manual-query-key -- A practice that does not exist yet has nothing on the server to key against; this placeholder only keeps `levelQueries` the same length as the stack.
				return { queryKey: ["practice-new", entry.id], queryFn: () => null, staleTime: Infinity };
			}
			return {
				...getPracticeOptions({ path: { workspaceSlug, practiceSlug: entry.id } }),
				select: (data: Practice) => ({ kind: "practice", data }) as const,
			};
		}),
	});

	/** The groups the editor offers. A hidden group still holds practices but is not a destination. */
	const editableAreas = areasQuery.data?.filter((area) => area.visibleInPracticeDashboards);

	/**
	 * Reads a level's payload only when it is the kind the caller is rendering. One reader per kind
	 * rather than one that takes the kind: a `===` against a value typed by a type parameter narrows
	 * nothing, so a shared reader would have to assert back what the tag already proves.
	 */
	type LevelQuery = (typeof levelQueries)[number] | undefined;
	const areaAdoptionAt = (query: LevelQuery) => {
		const tagged = query?.data;
		return tagged?.kind === "catalog-area" ? tagged.data : undefined;
	};
	const practiceAdoptionAt = (query: LevelQuery) => {
		const tagged = query?.data;
		return tagged?.kind === "catalog-practice" ? tagged.data : undefined;
	};
	const workspacePracticeAt = (query: LevelQuery) => {
		const tagged = query?.data;
		return tagged?.kind === "practice" ? tagged.data : undefined;
	};

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
			toast.success("Group updated", { description: changes.join(", ") });
		},
		onError: () => toast.error("Couldn't add the area. Nothing was changed."),
	});
	const adoptCatalogPractice = useMutation({
		...adoptPracticeMutation(),
		scope: practiceCatalogStructureScope(workspaceSlug),
	});

	// Closes only the practice level, so the reader lands back in the catalog they were working through.
	const adoptReviewedPractice = async (depth: number) => {
		const entry = detailStack[depth];
		const query = levelQueries[depth];
		const preview = practiceAdoptionAt(query);
		if (!entry || !query || !preview) return;
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
				<PracticeTreeSkeleton areas={3} practicesPerArea={3} />
			) : areasQuery.isError || practicesQuery.isError || definitionOptionsQuery.isError ? (
				<QueryErrorAlert
					error={areasQuery.error ?? practicesQuery.error ?? definitionOptionsQuery.error}
					title="Couldn't load practices"
					onRetry={() => {
						void areasQuery.refetch();
						void practicesQuery.refetch();
						void definitionOptionsQuery.refetch();
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
					library={{
						open: library === true,
						onOpenChange: (open) =>
							void setSearch((previous) => ({ ...previous, library: open || undefined })),
						state: catalogQuery.isError
							? {
									status: "error",
									error: catalogQuery.error,
									onRetry: () => void catalogQuery.refetch(),
								}
							: catalogQuery.data
								? { status: "ready", practices: catalogQuery.data }
								: { status: "loading" },
					}}
					onFocusFilterChange={(next: FocusFilter) =>
						void setSearch((previous) => ({
							...previous,
							focus: next === "ALL" ? undefined : next,
						}))
					}
					onCreateArea={async ({ name, icon, color }) => {
						try {
							await catalog.createArea.mutateAsync({
								path: { workspaceSlug },
								// The picker only ever sets a value, so `null` means "not chosen" — omit it and the
								// server keeps seeding the chip from the slug.
								body: {
									slug: generateSlug(name),
									name,
									icon: icon ?? undefined,
									color: color ?? undefined,
								},
							});
							return true;
						} catch {
							return false;
						}
					}}
					onUpdateArea={async (areaSlug, { name, icon, color }) => {
						try {
							await catalog.updateArea.mutateAsync({
								path: { workspaceSlug, areaSlug },
								body: { name, icon: icon ?? undefined, color: color ?? undefined },
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
						setDeletingArea(areasQuery.data.find((area) => area.slug === areaSlug) ?? null)
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

			<DetailDrawerStack
				stack={detailStack}
				guardedKinds={GUARDED_LEVEL_KINDS}
				onClose={stackControls.close}
			>
				{(entry, level) => {
					// `levelQueries` is derived from the same stack this callback walks, so index and depth
					// agree. A level read before its query exists is a frame between two states rather than a
					// state of its own, and it reads as loading.
					const query = levelQueries[level.depth];
					const levelPending = query === undefined || query.isPending;
					const levelError = query?.isError === true ? query.error : undefined;
					const refetchLevel = () => void query?.refetch();
					if (entry.kind === "catalog-area") {
						const areaPreview = areaAdoptionAt(query);
						const adoptGroup = async () => {
							const preview = areaAdoptionAt(query);
							if (!query || !preview) return;
							setStaleLevelKey(null);
							try {
								await adoptCatalogArea.mutateAsync({
									path: { workspaceSlug, slug: entry.id },
									headers: { "If-Match": preview.etag },
								});
							} catch (error) {
								// Same failure as a practice's, so the same recovery: refresh in place.
								if (problemStatusOf(error) !== 412) return;
								const refreshed = await query.refetch();
								if (refreshed.isSuccess) setStaleLevelKey(detailStackKey(entry));
								else toast.error("The group plan changed but couldn't be refreshed");
							}
						};
						return (
							<AreaAdoptionPanel
								nested={level.nested}
								state={
									areaPreview === undefined || levelPending
										? { status: "loading" }
										: levelError !== undefined
											? { status: "error", error: levelError, onRetry: refetchLevel }
											: {
													status: "ready",
													preview: areaPreview,
													action:
														staleLevelKey === detailStackKey(entry)
															? "stale"
															: adoptCatalogArea.isPending
																? "adding"
																: "idle",
												}
								}
								onOpenPractice={(catalogSlug) =>
									stackControls.open({ kind: "catalog-practice", id: catalogSlug })
								}
								onConfirm={() => void adoptGroup()}
							/>
						);
					}
					if (entry.kind === "practice") {
						const workspacePractice = workspacePracticeAt(query);
						return (
							<WorkspacePracticePanel
								nested={level.nested}
								state={
									workspacePractice === undefined ||
									levelPending ||
									definitionOptionsQuery.isPending
										? { status: "loading" }
										: levelError !== undefined || definitionOptionsQuery.isError
											? {
													status: "error",
													error: levelError ?? definitionOptionsQuery.error,
													onRetry: () => {
														refetchLevel();
														void definitionOptionsQuery.refetch();
													},
												}
											: {
													status: "ready",
													practice: workspacePractice,
													definitionOptions: definitionOptionsQuery.data,
													areaName: areasQuery.data?.find(
														(area) => area.slug === workspacePractice.areaSlug,
													)?.name,
												}
								}
							/>
						);
					}
					if (entry.kind === "practice-edit" || entry.kind === "practice-new") {
						const creating = entry.kind === "practice-new";
						const editing = creating ? undefined : workspacePracticeAt(query);
						// The form saves and then leaves. It must reject on failure, or the unsaved-changes
						// guard lifts and the draft goes with the level.
						const saved = async (work: Promise<unknown>) => {
							await work;
							stackControls.close(level.depth);
						};
						return (
							<PracticeFormLevel nested={level.nested} creating={creating}>
								{editableAreas === undefined ||
								definitionOptionsQuery.data === undefined ||
								(!creating && editing === undefined) ? (
									<DrawerBody>
										<PracticeDefinitionSkeleton />
									</DrawerBody>
								) : (
									// Split on the loaded practice rather than on `creating`: the guard above has
									// already sent every other way of having none to the skeleton, so no practice
									// here means this level is writing a new one.
									<PracticeForm
										{...(editing === undefined
											? {
													mode: "create" as const,
													onSubmit: (data, areaSlug) => saved(editor.create(data, areaSlug)),
												}
											: {
													mode: "edit" as const,
													initialData: editing,
													onSubmit: (slug, data, areaSlug) =>
														saved(editor.update(slug, data, areaSlug)),
													evidenceOutcome: evidenceOutcomesQuery.data?.find(
														(outcome) => outcome.practiceSlug === entry.id,
													),
												})}
										workspaceSlug={workspaceSlug}
										areas={editableAreas}
										definitionOptions={definitionOptionsQuery.data}
										isPending={editor.isPending}
										cancel={<LevelCancel />}
									/>
								)}
							</PracticeFormLevel>
						);
					}
					const catalogPreview = practiceAdoptionAt(query);
					return (
						<PracticeAdoptionPanel
							nested={level.nested}
							state={
								catalogPreview === undefined || levelPending || definitionOptionsQuery.isPending
									? { status: "loading" }
									: levelError !== undefined || definitionOptionsQuery.isError
										? {
												status: "error",
												error: levelError ?? definitionOptionsQuery.error,
												onRetry: () => {
													refetchLevel();
													void definitionOptionsQuery.refetch();
												},
											}
										: {
												status: "ready",
												preview: catalogPreview,
												definitionOptions: definitionOptionsQuery.data,
												action:
													staleLevelKey === detailStackKey(entry)
														? "stale"
														: adoptCatalogPractice.isPending
															? "adding"
															: "idle",
											}
							}
							onAdopt={() => void adoptReviewedPractice(level.depth)}
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
							{catalog.deleteArea.isPending ? "Deleting…" : "Delete group and practices"}
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
