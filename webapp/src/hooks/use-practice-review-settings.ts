import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
	autonomyRollupQueryKey,
	getPracticeReviewSettingsQueryKey,
	listGroupsQueryKey,
	listPracticesQueryKey,
	updatePracticeReviewSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeReviewSettings, UpdatePracticeReviewSettingsRequest } from "@/api/types.gen";
import { problemDetailOf } from "@/lib/problem-detail";

export type PracticeReviewSettingsField = NonNullable<
	UpdatePracticeReviewSettingsRequest["reset"]
>[number];

/**
 * One place that knows what a write to this resource invalidates, because two screens write it. The
 * workspace default sits at the bottom of the practice → group → workspace chain, so changing it
 * changes the autonomy in force on everything holding none of its own — and none of that is in the
 * response.
 */
export function usePracticeReviewSettingsMutation(
	workspaceSlug: string,
	messages: { success: string; error: string },
) {
	const queryClient = useQueryClient();
	const settingsQueryKey = getPracticeReviewSettingsQueryKey({ path: { workspaceSlug } });
	const mutationKey = ["workspace", workspaceSlug, "practice-review-settings"] as const;

	return useMutation({
		...updatePracticeReviewSettingsMutation(),
		mutationKey,
		scope: { id: `workspace:${workspaceSlug}:practice-review-settings` },
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: settingsQueryKey });
			const previous = queryClient.getQueryData<PracticeReviewSettings>(settingsQueryKey);
			if (previous && !variables.body.reset?.length) {
				queryClient.setQueryData(settingsQueryKey, patchReviewSettings(previous, variables.body));
			}
			return { previous };
		},
		onSuccess: (updated) => {
			queryClient.setQueryData(settingsQueryKey, updated);
			toast.success(messages.success);
		},
		onError: (error, _variables, context) => {
			if (context?.previous) queryClient.setQueryData(settingsQueryKey, context.previous);
			toast.error(messages.error, { description: problemDetailOf(error) });
		},
		onSettled: () => {
			if (queryClient.isMutating({ mutationKey }) !== 1) return;
			void queryClient.invalidateQueries({ queryKey: settingsQueryKey });
			void queryClient.invalidateQueries({
				queryKey: autonomyRollupQueryKey({ path: { workspaceSlug } }),
			});
			void queryClient.invalidateQueries({
				queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
			});
			void queryClient.invalidateQueries({
				queryKey: listGroupsQueryKey({ path: { workspaceSlug } }),
			});
		},
	});
}

/**
 * The optimistic echo of a PATCH: every field the request set becomes both the effective value and the
 * raw override.
 *
 * Resets are not echoed — clearing an override resolves against the fleet default, which only the
 * server knows, so the caller skips this entirely when `reset` is non-empty.
 */
export function patchReviewSettings(
	settings: PracticeReviewSettings,
	patch: UpdatePracticeReviewSettingsRequest,
): PracticeReviewSettings {
	return {
		...settings,
		...(patch.deliverToMerged === undefined
			? {}
			: {
					deliverToMerged: patch.deliverToMerged,
					deliverToMergedOverride: patch.deliverToMerged,
				}),
		...(patch.cooldownMinutes === undefined
			? {}
			: {
					cooldownMinutes: patch.cooldownMinutes,
					cooldownMinutesOverride: patch.cooldownMinutes,
				}),
		...(patch.defaultAutonomy === undefined
			? {}
			: {
					defaultAutonomy: patch.defaultAutonomy,
					defaultAutonomyOverride: patch.defaultAutonomy,
				}),
		// The scope has no separate "override" key: it replaces wholesale and an empty scope already
		// means "unrestricted", so the effective value is the only value there is.
		...(patch.reviewScope === undefined ? {} : { reviewScope: patch.reviewScope }),
	};
}
