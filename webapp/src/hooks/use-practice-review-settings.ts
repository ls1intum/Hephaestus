import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	getPracticeReviewSettingsQueryKey,
	listAreasQueryKey,
	listPracticesQueryKey,
	reviewTierRollupQueryKey,
	updatePracticeReviewSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeReviewSettings, UpdatePracticeReviewSettingsRequest } from "@/api/types.gen";
import { problemDetailOf } from "@/lib/problem-detail";

export type PracticeReviewSettingsField = NonNullable<
	UpdatePracticeReviewSettingsRequest["reset"]
>[number];

/**
 * The workspace's practice-review policy, with one place that knows what a write to it invalidates.
 *
 * <p>Both the review-settings screen and the autonomy screen write this resource, and the workspace
 * default tier is the bottom of the practice → area → workspace chain: changing it changes the tier in
 * force on every practice and area that holds none of its own, and none of that is in the response.
 * A caller that only refreshed this resource would leave a hundred rows showing the tier they had
 * before the decision that was meant to move all of them.
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
			// Only the server resolves the chain, so everything downstream of the default is now a guess.
			void queryClient.invalidateQueries({
				queryKey: reviewTierRollupQueryKey({ path: { workspaceSlug } }),
			});
			void queryClient.invalidateQueries({
				queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
			});
			void queryClient.invalidateQueries({
				queryKey: listAreasQueryKey({ path: { workspaceSlug } }),
			});
		},
	});
}

/**
 * The optimistic echo of a PATCH: every field the request set becomes both the effective value and the
 * raw override, because setting a value here is what "this workspace has chosen" means.
 *
 * <p>Resets are deliberately not echoed — clearing an override resolves against the fleet default,
 * which only the server knows. The caller skips this function entirely when `reset` is non-empty.
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
		...(patch.runForAllUsers === undefined
			? {}
			: {
					runForAllUsers: patch.runForAllUsers,
					runForAllUsersOverride: patch.runForAllUsers,
				}),
		...(patch.cooldownMinutes === undefined
			? {}
			: {
					cooldownMinutes: patch.cooldownMinutes,
					cooldownMinutesOverride: patch.cooldownMinutes,
				}),
		...(patch.defaultReviewTier === undefined
			? {}
			: {
					defaultReviewTier: patch.defaultReviewTier,
					defaultReviewTierOverride: patch.defaultReviewTier,
				}),
		...(patch.feedbackReach === undefined
			? {}
			: {
					feedbackReach: patch.feedbackReach,
					feedbackReachOverride: patch.feedbackReach,
				}),
		// The scope has no separate "override" key: it replaces wholesale and an empty scope already
		// means "unrestricted", so the effective value is the only value there is.
		...(patch.reviewScope === undefined ? {} : { reviewScope: patch.reviewScope }),
	};
}
