import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { toast } from "sonner";

import {
	getFeedbackModeRollupQueryKey,
	listPracticeGroupsQueryKey,
	listPracticesQueryKey,
	setGroupFeedbackModeMutation,
	setPracticeFeedbackModeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeGroup } from "@/api/types.gen";
import { filedUnder, pathString, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import type { FeedbackMode } from "@/lib/practice-feedback-mode";
import { problemDetailOf } from "@/lib/problem-detail";

export interface BulkProgress {
	done: number;
	total: number;
}

/**
 * Every write here invalidates the rollup and the practice list rather than patching them: the
 * inheritance chain is resolved server-side, and a client predicting what a group feedback mode does to the
 * practices inheriting it would be a second implementation of that chain. The group response carries
 * the group and nothing else.
 */
export function usePracticeFeedbackModeMutations(workspaceSlug: string) {
	const queryClient = useQueryClient();
	const [bulk, setBulk] = useState<BulkProgress | null>(null);
	// A ref rather than the state above: the mutation's callbacks close over the render that created
	// them and would keep reading `null` for the whole run.
	const bulkRunning = useRef(false);
	const groupMutationKey = ["practice-feedback-mode", workspaceSlug, "groups"] as const;
	const practiceMutationKey = ["practice-feedback-mode", workspaceSlug, "practices"] as const;

	const invalidateResolved = () => {
		void queryClient.invalidateQueries({
			queryKey: getFeedbackModeRollupQueryKey({ path: { workspaceSlug } }),
		});
		void queryClient.invalidateQueries({
			queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
		});
	};

	const setGroupFeedbackMode = useMutation({
		...filedUnder(groupMutationKey, setGroupFeedbackModeMutation()),
		onSuccess: (updated) => {
			queryClient.setQueryData<PracticeGroup[]>(
				listPracticeGroupsQueryKey({ path: { workspaceSlug } }),
				(groups) => groups?.map((group) => (group.slug === updated.slug ? updated : group)),
			);
		},
		onError: (error) =>
			toast.error("We could not change the group", { description: problemDetailOf(error) }),
		onSettled: () => {
			if (queryClient.isMutating({ mutationKey: groupMutationKey }) === 1) {
				void queryClient.invalidateQueries({
					queryKey: listPracticeGroupsQueryKey({ path: { workspaceSlug } }),
				});
				invalidateResolved();
			}
		},
	});

	const setPracticeFeedbackMode = useMutation({
		...filedUnder(practiceMutationKey, setPracticeFeedbackModeMutation()),
		onError: (error) =>
			toast.error("We could not change the practice", { description: problemDetailOf(error) }),
		// One write, one refetch — except inside a bulk run, which settles once at the end rather than
		// refetching the list and the rollup after every PATCH in it.
		onSettled: () => {
			if (!bulkRunning.current) {
				invalidateResolved();
			}
		},
	});

	const setManyPracticeFeedbackModes = async (
		practiceSlugs: readonly string[],
		feedbackMode: FeedbackMode | null,
	) => {
		if (practiceSlugs.length === 0) {
			return;
		}
		let failed = 0;
		bulkRunning.current = true;
		setBulk({ done: 0, total: practiceSlugs.length });
		for (const [index, practiceSlug] of practiceSlugs.entries()) {
			try {
				await setPracticeFeedbackMode.mutateAsync({
					path: { workspaceSlug, practiceSlug },
					body: { feedbackMode },
				});
			} catch {
				failed += 1;
			}
			setBulk({ done: index + 1, total: practiceSlugs.length });
		}
		bulkRunning.current = false;
		setBulk(null);
		invalidateResolved();

		const changed = practiceSlugs.length - failed;
		if (failed === 0) {
			toast.success(`Changed ${changed} ${changed === 1 ? "practice" : "practices"}`);
		} else if (changed === 0) {
			toast.error(
				`We could not change ${failed} ${failed === 1 ? "practice" : "practices"}. Try again.`,
			);
		} else {
			toast.warning(
				`Changed ${changed} of ${practiceSlugs.length}. ${failed} could not be changed.`,
			);
		}
	};

	const pendingGroupSlugs = usePendingMutationIds(groupMutationKey, (variables) =>
		pathString(variables, "groupSlug"),
	);
	const pendingPracticeSlugs = usePendingMutationIds(practiceMutationKey, (variables) =>
		pathString(variables, "practiceSlug"),
	);

	return {
		bulk,
		pendingGroupSlugs,
		pendingPracticeSlugs,
		setGroupFeedbackMode,
		setPracticeFeedbackMode,
		setManyPracticeFeedbackModes,
	};
}
