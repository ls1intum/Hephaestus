import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import {
	listAreasQueryKey,
	listPracticesQueryKey,
	reviewTierRollupQueryKey,
	setAreaReviewTierMutation,
	setReviewTierMutation,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeArea } from "@/api/types.gen";
import { usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { problemDetailOf } from "@/lib/problem-detail";
import type { ReviewTier } from "@/lib/review-tiers";

export interface BulkProgress {
	done: number;
	total: number;
}

/**
 * The writes the autonomy screen makes that the catalogue does not: an area's tier, and the same
 * practice tier applied to a selection.
 *
 * <p>Every one of them invalidates the rollup and the practice list rather than patching them. The
 * inheritance chain is resolved server-side on purpose, so a client that predicted what an area tier
 * does to forty inheriting practices would be a second implementation of it, drifting on the first
 * change. The area response carries the area and nothing else.
 */
export function useReviewAutonomyMutations(workspaceSlug: string) {
	const queryClient = useQueryClient();
	const [bulk, setBulk] = useState<BulkProgress | null>(null);
	const areaMutationKey = ["review-autonomy", workspaceSlug, "areas"] as const;

	const invalidateResolved = () => {
		void queryClient.invalidateQueries({
			queryKey: reviewTierRollupQueryKey({ path: { workspaceSlug } }),
		});
		void queryClient.invalidateQueries({
			queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
		});
	};

	const setAreaTier = useMutation({
		...setAreaReviewTierMutation(),
		mutationKey: areaMutationKey,
		onSuccess: (updated) => {
			queryClient.setQueryData<PracticeArea[]>(
				listAreasQueryKey({ path: { workspaceSlug } }),
				(areas) =>
					areas?.map((area) =>
						area.slug === updated.slug ? { ...area, reviewTier: updated.reviewTier } : area,
					),
			);
		},
		onError: (error) =>
			toast.error("Couldn't change the area", { description: problemDetailOf(error) }),
		onSettled: () => {
			if (queryClient.isMutating({ mutationKey: areaMutationKey }) === 1) {
				void queryClient.invalidateQueries({
					queryKey: listAreasQueryKey({ path: { workspaceSlug } }),
				});
				invalidateResolved();
			}
		},
	});

	const setPracticeTier = useMutation({
		...setReviewTierMutation(),
		onError: (error) =>
			toast.error("Couldn't change the practice", { description: problemDetailOf(error) }),
		onSettled: invalidateResolved,
	});

	/**
	 * One request per practice, in order, because there is no bulk endpoint — and each write takes the
	 * same workspace lock, so firing forty at once would queue on the server anyway while making the
	 * failures arrive interleaved and unattributable.
	 *
	 * <p>Partial failure is reported rather than rolled back: the practices that did change stay
	 * changed, which is what the admin asked for and what a refetch will show either way. A practice
	 * Hephaestus cannot review is refused above Off, and that refusal is the common one here.
	 */
	const setManyPracticeTiers = async (
		practiceSlugs: readonly string[],
		tier: ReviewTier | null,
	) => {
		if (practiceSlugs.length === 0) return;
		let failed = 0;
		setBulk({ done: 0, total: practiceSlugs.length });
		try {
			for (const [index, practiceSlug] of practiceSlugs.entries()) {
				try {
					await setPracticeTier.mutateAsync({
						path: { workspaceSlug, practiceSlug },
						// Omitted, not null: the generated request types the field as optional, and the server
						// reads an absent field as "hold no tier here and inherit".
						body: tier === null ? {} : { reviewTier: tier },
					});
				} catch {
					failed += 1;
				}
				setBulk({ done: index + 1, total: practiceSlugs.length });
			}
		} finally {
			setBulk(null);
			invalidateResolved();
		}

		const changed = practiceSlugs.length - failed;
		if (failed === 0) {
			toast.success(`Changed ${changed} ${changed === 1 ? "practice" : "practices"}`);
		} else if (changed === 0) {
			toast.error(`Couldn't change ${failed} ${failed === 1 ? "practice" : "practices"}`);
		} else {
			toast.warning(
				`Changed ${changed} of ${practiceSlugs.length}; ${failed} couldn't be changed.`,
			);
		}
	};

	const pendingAreaSlugs = usePendingMutationIds<{ path: { areaSlug?: string } }, string>(
		areaMutationKey,
		(variables) => variables.path.areaSlug,
	);
	const pendingPracticeSlugs = new Set<string>(
		setPracticeTier.isPending && setPracticeTier.variables
			? [setPracticeTier.variables.path.practiceSlug]
			: [],
	);

	return {
		bulk,
		pendingAreaSlugs,
		pendingPracticeSlugs,
		setAreaTier,
		setPracticeTier,
		setManyPracticeTiers,
	};
}
