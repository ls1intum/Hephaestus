import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	createAreaMutation,
	deleteAreaMutation,
	deletePracticeMutation,
	getPracticeQueryKey,
	listAreasQueryKey,
	listPracticesQueryKey,
	placePracticeMutation,
	reorderAreasMutation,
	setActiveMutation,
	updateAreaMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Practice, PracticeArea } from "@/api/types.gen";
import {
	applyDisplayOrder,
	applyPracticePlacements,
	patchArea,
	patchPractice,
	placePractice,
	practiceCatalogStructureScope,
	practicePlacementSnapshot,
	removeArea,
	selectAreaPatch,
	unassignPractices,
	upsertArea,
} from "@/hooks/practice-catalog-cache";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";

const UNASSIGNED = "__unassigned__";

export function usePracticeCatalogMutations(workspaceSlug: string) {
	const queryClient = useQueryClient();
	const areasQueryKey = listAreasQueryKey({ path: { workspaceSlug } });
	const practicesQueryKey = listPracticesQueryKey({ path: { workspaceSlug } });
	const areaMutationKey = ["practice-catalog", workspaceSlug, "areas"] as const;
	const practiceMutationKey = ["practice-catalog", workspaceSlug, "practices"] as const;
	const structuralScope = practiceCatalogStructureScope(workspaceSlug);
	const applyPlacementCaches = (
		placements: Array<Pick<Practice, "areaSlug" | "displayOrder" | "slug">>,
	) => {
		queryClient.setQueryData<Practice[]>(practicesQueryKey, (practices = []) =>
			applyPracticePlacements(practices, placements),
		);
		for (const placement of placements) {
			queryClient.setQueryData<Practice>(
				getPracticeQueryKey({
					path: { workspaceSlug, practiceSlug: placement.slug },
				}),
				(practice) =>
					practice
						? {
								...practice,
								areaSlug: placement.areaSlug,
								displayOrder: placement.displayOrder,
							}
						: practice,
			);
		}
	};

	const invalidateAreasAfterLastWrite = () => {
		if (queryClient.isMutating({ mutationKey: areaMutationKey }) === 1) {
			void queryClient.invalidateQueries({ queryKey: areasQueryKey });
		}
	};
	const invalidatePracticesAfterLastWrite = () => {
		if (queryClient.isMutating({ mutationKey: practiceMutationKey }) === 1) {
			void queryClient.invalidateQueries({ queryKey: practicesQueryKey });
		}
	};

	const createArea = useMutation({
		...filedUnder(areaMutationKey, createAreaMutation()),
		scope: structuralScope,
		onMutate: async () => {
			await queryClient.cancelQueries({ queryKey: areasQueryKey });
		},
		onSuccess: (created) => {
			queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas) =>
				areas ? upsertArea(areas, created) : areas,
			);
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
		onSettled: invalidateAreasAfterLastWrite,
	});

	const updateArea = useMutation({
		...filedUnder(areaMutationKey, updateAreaMutation()),
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: areasQueryKey });
			const previous = queryClient
				.getQueryData<PracticeArea[]>(areasQueryKey)
				?.find((area) => area.slug === variables.path.areaSlug);
			queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas = []) =>
				patchArea(areas, variables.path.areaSlug, variables.body),
			);
			return { previous };
		},
		onError: (_error, variables, context) => {
			const previous = context?.previous;
			if (previous) {
				queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas = []) =>
					patchArea(areas, previous.slug, selectAreaPatch(previous, variables.body)),
				);
			}
			toast.error("Failed to update practice area");
		},
		onSuccess: (updated, variables) => {
			queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas = []) =>
				patchArea(areas, updated.slug, selectAreaPatch(updated, variables.body)),
			);
		},
		onSettled: invalidateAreasAfterLastWrite,
	});

	const deleteArea = useMutation({
		...filedUnder(areaMutationKey, deleteAreaMutation()),
		scope: structuralScope,
		onMutate: async () => {
			await Promise.all([
				queryClient.cancelQueries({ queryKey: areasQueryKey }),
				queryClient.cancelQueries({ queryKey: practicesQueryKey }),
			]);
		},
		onSuccess: (_data, variables) => {
			const slug = variables.path.areaSlug;
			queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas = []) =>
				removeArea(areas, slug),
			);
			const updated = unassignPractices(
				queryClient.getQueryData<Practice[]>(practicesQueryKey) ?? [],
				slug,
			);
			applyPlacementCaches(
				updated.map(({ areaSlug, displayOrder, slug: practiceSlug }) => ({
					areaSlug,
					displayOrder,
					slug: practiceSlug,
				})),
			);
			toast.success("Practice area deleted");
		},
		onError: () => toast.error("Failed to delete practice area"),
		onSettled: () => {
			invalidateAreasAfterLastWrite();
			if (queryClient.isMutating({ mutationKey: practiceMutationKey }) === 0) {
				void queryClient.invalidateQueries({ queryKey: practicesQueryKey });
			}
		},
	});

	const reorderAreas = useMutation({
		...filedUnder(areaMutationKey, reorderAreasMutation()),
		scope: structuralScope,
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: areasQueryKey });
			const previousOrder = queryClient
				.getQueryData<PracticeArea[]>(areasQueryKey)
				?.slice()
				.sort((a, b) => a.displayOrder - b.displayOrder)
				.map((area) => area.slug);
			queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas = []) =>
				applyDisplayOrder(areas, variables.body.orderedSlugs),
			);
			return { previousOrder };
		},
		onError: (_error, _variables, context) => {
			const previousOrder = context?.previousOrder;
			if (previousOrder) {
				queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas = []) =>
					applyDisplayOrder(areas, previousOrder),
				);
			}
			toast.error("Failed to reorder practice areas");
		},
		onSuccess: (updated) => {
			const order = [...updated]
				.sort((a, b) => a.displayOrder - b.displayOrder)
				.map((area) => area.slug);
			queryClient.setQueryData<PracticeArea[]>(areasQueryKey, (areas = []) =>
				applyDisplayOrder(areas, order),
			);
		},
		onSettled: invalidateAreasAfterLastWrite,
	});

	const place = useMutation({
		...filedUnder(practiceMutationKey, placePracticeMutation()),
		scope: structuralScope,
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: practicesQueryKey });
			const practices = queryClient.getQueryData<Practice[]>(practicesQueryKey) ?? [];
			const destinationAreaSlug = variables.body.areaSlug ?? null;
			const previous = practicePlacementSnapshot(
				practices,
				variables.path.practiceSlug,
				destinationAreaSlug,
			);
			const optimistic = placePractice(
				practices,
				variables.path.practiceSlug,
				destinationAreaSlug,
				variables.body.position,
			);
			queryClient.setQueryData(practicesQueryKey, optimistic);
			const affectedSlugs = new Set(previous.map(({ slug }) => slug));
			applyPlacementCaches(
				optimistic
					.filter(({ slug }) => affectedSlugs.has(slug))
					.map(({ slug, areaSlug, displayOrder }) => ({ slug, areaSlug, displayOrder })),
			);
			return { previous };
		},
		onError: (_error, _variables, context) => {
			const previous = context?.previous;
			if (previous) {
				applyPlacementCaches(previous);
			}
			toast.error("Failed to move practice");
		},
		onSuccess: (updated) => {
			applyPlacementCaches(
				updated.map(({ slug, areaSlug, displayOrder }) => ({ slug, areaSlug, displayOrder })),
			);
		},
		onSettled: invalidatePracticesAfterLastWrite,
	});

	const deletePractice = useMutation({
		...filedUnder(practiceMutationKey, deletePracticeMutation()),
		scope: structuralScope,
		onMutate: async (variables) => {
			await Promise.all([
				queryClient.cancelQueries({ queryKey: practicesQueryKey }),
				queryClient.cancelQueries({
					queryKey: getPracticeQueryKey({
						path: {
							workspaceSlug,
							practiceSlug: variables.path.practiceSlug,
						},
					}),
					exact: true,
				}),
			]);
		},
		onSuccess: (_data, variables) => {
			queryClient.setQueryData<Practice[]>(practicesQueryKey, (practices = []) =>
				practices.filter((practice) => practice.slug !== variables.path.practiceSlug),
			);
			queryClient.removeQueries({
				queryKey: getPracticeQueryKey({
					path: {
						workspaceSlug,
						practiceSlug: variables.path.practiceSlug,
					},
				}),
				exact: true,
			});
			toast.success("Practice deleted");
		},
		onError: () => toast.error("Failed to delete practice"),
		onSettled: invalidatePracticesAfterLastWrite,
	});

	const setActive = useMutation({
		...filedUnder(practiceMutationKey, setActiveMutation()),
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: practicesQueryKey });
			const previousActive = queryClient
				.getQueryData<Practice[]>(practicesQueryKey)
				?.find((practice) => practice.slug === variables.path.practiceSlug)?.active;
			queryClient.setQueryData<Practice[]>(practicesQueryKey, (practices = []) =>
				patchPractice(practices, variables.path.practiceSlug, {
					active: variables.body.active,
				}),
			);
			return { previousActive };
		},
		onError: (_error, variables, context) => {
			if (context?.previousActive !== undefined) {
				queryClient.setQueryData<Practice[]>(practicesQueryKey, (practices = []) =>
					patchPractice(practices, variables.path.practiceSlug, {
						active: context.previousActive,
					}),
				);
			}
			toast.error(`Failed to toggle "${variables.path.practiceSlug}"`);
		},
		onSuccess: (updated) => {
			queryClient.setQueryData<Practice[]>(practicesQueryKey, (practices = []) =>
				patchPractice(practices, updated.slug, { active: updated.active }),
			);
			queryClient.setQueryData<Practice>(
				getPracticeQueryKey({
					path: { workspaceSlug, practiceSlug: updated.slug },
				}),
				(practice) => (practice ? { ...practice, active: updated.active } : practice),
			);
		},
		onSettled: invalidatePracticesAfterLastWrite,
	});

	const pendingAreaSlugs = usePendingMutationIds<{ path: { areaSlug?: string } }, string>(
		areaMutationKey,
		(variables) => variables.path.areaSlug,
	);
	const pendingPracticeSlugs = usePendingMutationIds<{ path: { practiceSlug?: string } }, string>(
		practiceMutationKey,
		(variables) => variables.path.practiceSlug,
	);
	const blockedPracticeOrderBuckets = new Set<string>();
	const blockedMoveDestinationSlugs = new Set<string>();
	if (deleteArea.isPending && deleteArea.variables) {
		const deletingAreaSlug = deleteArea.variables.path.areaSlug;
		blockedPracticeOrderBuckets.add(deletingAreaSlug);
		blockedPracticeOrderBuckets.add(UNASSIGNED);
		blockedMoveDestinationSlugs.add(deletingAreaSlug);
		blockedMoveDestinationSlugs.add(UNASSIGNED);
	}
	if (deletePractice.isPending && deletePractice.variables) {
		const practice = queryClient
			.getQueryData<Practice[]>(practicesQueryKey)
			?.find(({ slug }) => slug === deletePractice.variables.path.practiceSlug);
		const bucket = practice?.areaSlug ?? UNASSIGNED;
		blockedPracticeOrderBuckets.add(bucket);
		blockedMoveDestinationSlugs.add(bucket);
	}
	return {
		areaStructurePending: createArea.isPending || deleteArea.isPending || reorderAreas.isPending,
		createArea,
		deleteArea,
		deletePractice,
		placePractice: place,
		pendingAreaSlugs,
		pendingPracticeSlugs,
		blockedMoveDestinationSlugs,
		blockedPracticeOrderBuckets,
		reorderAreas,
		setActive,
		updateArea,
	};
}
