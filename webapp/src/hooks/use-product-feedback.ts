import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
	dismissProductSurveyMutation,
	listAvailableProductSurveysOptions,
	listAvailableProductSurveysQueryKey,
	submitInstanceProductFeedbackMutation,
	submitProductSurveyResponseMutation,
	submitWorkspaceProductFeedbackMutation,
} from "@/api/@tanstack/react-query.gen";
import type { FeedbackRequest } from "@/api/types.gen";

export function useActiveSurvey(workspaceSlug: string | undefined) {
	const queryClient = useQueryClient();
	const slug = workspaceSlug ?? "";
	const request = { path: { workspaceSlug: slug } };
	const query = useQuery({
		...listAvailableProductSurveysOptions(request),
		enabled: !!workspaceSlug,
	});
	const survey = query.data?.[0];
	const refresh = () =>
		void queryClient.invalidateQueries({ queryKey: listAvailableProductSurveysQueryKey(request) });
	const submit = useMutation({
		...submitProductSurveyResponseMutation(),
		onSuccess: () => {
			toast.success("Thank you for your response.");
			refresh();
		},
		onError: () => toast.error("Couldn't submit the survey."),
	});
	const dismiss = useMutation({
		...dismissProductSurveyMutation(),
		onSuccess: refresh,
		onError: () => toast.error("Couldn't dismiss the survey."),
	});
	return {
		survey,
		submit: (submittedAnswers: Record<string, string>) =>
			survey &&
			submit.mutate({
				path: { workspaceSlug: slug, surveyId: survey.id },
				body: { answers: submittedAnswers },
			}),
		dismiss: () => survey && dismiss.mutate({ path: { workspaceSlug: slug, surveyId: survey.id } }),
		isSubmitting: submit.isPending,
		isDismissing: dismiss.isPending,
	};
}

export function useSubmitProductFeedback(workspaceSlug: string | undefined) {
	const callbacks = {
		onSuccess: () =>
			toast.success("Thanks — your feedback was sent to this instance's administrators."),
		onError: () => toast.error("Couldn't send feedback. Please try again."),
	};
	const workspaceMutation = useMutation({
		...submitWorkspaceProductFeedbackMutation(),
		...callbacks,
	});
	const instanceMutation = useMutation({
		...submitInstanceProductFeedbackMutation(),
		...callbacks,
	});
	return {
		isPending: workspaceMutation.isPending || instanceMutation.isPending,
		submit: async (body: FeedbackRequest) => {
			try {
				if (workspaceSlug) await workspaceMutation.mutateAsync({ path: { workspaceSlug }, body });
				else await instanceMutation.mutateAsync({ body });
				return true;
			} catch {
				return false;
			}
		},
	};
}
