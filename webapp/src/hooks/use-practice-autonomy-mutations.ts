import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { toast } from "sonner";
import {
	autonomyRollupQueryKey,
	listAreasQueryKey,
	listPracticesQueryKey,
	setAreaAutonomyMutation,
	setAutonomyMutation,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeArea } from "@/api/types.gen";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import type { PracticeAutonomy } from "@/lib/practice-autonomy";
import { problemDetailOf } from "@/lib/problem-detail";

export interface BulkProgress {
	done: number;
	total: number;
}

/**
 * Every write here invalidates the rollup and the practice list rather than patching them: the
 * inheritance chain is resolved server-side, and a client predicting what an area autonomy does to the
 * practices inheriting it would be a second implementation of that chain. The area response carries
 * the area and nothing else.
 */
export function usePracticeAutonomyMutations(workspaceSlug: string) {
	const queryClient = useQueryClient();
	const [bulk, setBulk] = useState<BulkProgress | null>(null);
	// A ref rather than the state above: the mutation's callbacks close over the render that created
	// them and would keep reading `null` for the whole run.
	const bulkRunning = useRef(false);
	const areaMutationKey = ["practice-autonomy", workspaceSlug, "areas"] as const;
	const practiceMutationKey = ["practice-autonomy", workspaceSlug, "practices"] as const;

	const invalidateResolved = () => {
		void queryClient.invalidateQueries({
			queryKey: autonomyRollupQueryKey({ path: { workspaceSlug } }),
		});
		void queryClient.invalidateQueries({
			queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
		});
	};

	const setAreaAutonomy = useMutation({
		...filedUnder(areaMutationKey, setAreaAutonomyMutation()),
		onSuccess: (updated) => {
			queryClient.setQueryData<PracticeArea[]>(
				listAreasQueryKey({ path: { workspaceSlug } }),
				(areas) =>
					areas?.map((area) =>
						area.slug === updated.slug ? { ...area, autonomy: updated.autonomy } : area,
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

	const setPracticeAutonomy = useMutation({
		...filedUnder(practiceMutationKey, setAutonomyMutation()),
		onError: (error) =>
			toast.error("Couldn't change the practice", { description: problemDetailOf(error) }),
		// One write, one refetch — except inside a bulk run, which settles once at the end rather than
		// refetching the list and the rollup after every PATCH in it.
		onSettled: () => {
			if (!bulkRunning.current) invalidateResolved();
		},
	});

	/**
	 * One request per practice, in order: there is no bulk endpoint, and each write takes the same
	 * workspace lock, so firing them in parallel queues on the server anyway while making the failures
	 * arrive interleaved and unattributable.
	 *
	 * Partial failure is reported, not rolled back — the practices that did change stay changed, which
	 * is what a refetch will show either way.
	 */
	const setManyPracticeAutonomies = async (
		practiceSlugs: readonly string[],
		autonomy: PracticeAutonomy | null,
	) => {
		if (practiceSlugs.length === 0) return;
		let failed = 0;
		bulkRunning.current = true;
		setBulk({ done: 0, total: practiceSlugs.length });
		try {
			for (const [index, practiceSlug] of practiceSlugs.entries()) {
				try {
					await setPracticeAutonomy.mutateAsync({
						path: { workspaceSlug, practiceSlug },
						// Omitted, not null: the generated request types the field as optional, and the server
						// reads an absent field as "hold no autonomy here and inherit".
						body: autonomy === null ? {} : { autonomy: autonomy },
					});
				} catch {
					failed += 1;
				}
				setBulk({ done: index + 1, total: practiceSlugs.length });
			}
		} finally {
			bulkRunning.current = false;
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
	const pendingPracticeSlugs = usePendingMutationIds<{ path: { practiceSlug?: string } }, string>(
		practiceMutationKey,
		(variables) => variables.path.practiceSlug,
	);

	return {
		bulk,
		pendingAreaSlugs,
		pendingPracticeSlugs,
		setAreaAutonomy,
		setPracticeAutonomy,
		setManyPracticeAutonomies,
	};
}
