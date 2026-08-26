import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	autonomyRollupQueryKey,
	getPracticeReviewSettingsQueryKey,
	listAreasQueryKey,
	listPracticesQueryKey,
	updatePracticeReviewSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeReviewSettings, UpdatePracticeReviewSettingsRequest } from "@/api/types.gen";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export type PracticeReviewSettingsField = NonNullable<
	UpdatePracticeReviewSettingsRequest["reset"]
>[number];

/**
 * One place that knows what a write to this resource invalidates, because two screens write it. The
 * workspace default sits at the bottom of the practice → area → workspace chain, so changing it
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
			if (previous && !variables.headers?.["If-Match"]) {
				variables.headers = { "If-Match": previous.etag };
			}
			return { previous };
		},
		onSuccess: (updated) => {
			queryClient.setQueryData(settingsQueryKey, updated);
			toast.success(messages.success);
		},
		onError: (error, _variables, context) => {
			if (context?.previous) queryClient.setQueryData(settingsQueryKey, context.previous);
			if (problemStatusOf(error) === 412) {
				toast.error("Review settings changed elsewhere", {
					description: "The latest settings were reloaded. Review your change and try again.",
				});
				void queryClient.invalidateQueries({ queryKey: settingsQueryKey });
				return;
			}
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
				queryKey: listAreasQueryKey({ path: { workspaceSlug } }),
			});
		},
	});
}
