import type { Survey as PostHogSurveyRaw } from "posthog-js";

import { usePostHog } from "posthog-js/react";
import { useEffect, useRef, useState } from "react";

import {
	normalisePostHogSurvey,
	type PostHogSurvey,
	type SurveyQuestion,
	type SurveyResponse,
} from "@/types/survey";
import { SurveyContainer } from "./survey-container";

interface PostHogSurveyWidgetProps {
	surveyId?: string;
	autoOpen?: boolean;
	reloadOnComplete?: boolean;
}

export function PostHogSurveyWidget({
	surveyId,
	autoOpen = true,
	reloadOnComplete = false,
}: PostHogSurveyWidgetProps) {
	const posthog = usePostHog();
	const [survey, setSurvey] = useState<PostHogSurvey | null>(null);
	const [isVisible, setIsVisible] = useState(false);
	const [submissionId, setSubmissionId] = useState<string | null>(null);
	const hasTrackedShown = useRef(false);

	useEffect(() => {
		if (!posthog || !autoOpen) {
			return;
		}

		let isActive = true;

		const handleSurveys = (surveys: PostHogSurveyRaw[]) => {
			if (!isActive) {
				return;
			}

			const eligible = surveys.filter((candidate) => candidate.type === "api");
			const selected = surveyId
				? eligible.find((candidate) => candidate.id === surveyId)
				: eligible[0];

			if (!selected) {
				return;
			}

			const normalized = normalisePostHogSurvey(selected);
			setSurvey(normalized);
			setIsVisible(true);
			hasTrackedShown.current = false;
		};

		const unsubscribe = posthog.onSurveysLoaded((surveys) =>
			handleSurveys(surveys as PostHogSurveyRaw[]),
		);
		posthog.getActiveMatchingSurveys(
			(surveys) => handleSurveys(surveys as PostHogSurveyRaw[]),
			true,
		);

		return () => {
			isActive = false;
			unsubscribe?.();
		};
	}, [autoOpen, posthog, surveyId]);

	useEffect(() => {
		if (!posthog || !survey || !isVisible) {
			return;
		}

		if (hasTrackedShown.current) {
			return;
		}

		posthog.capture("survey shown", {
			$survey_id: survey.id,
			$survey_name: survey.name,
		});
		hasTrackedShown.current = true;
	}, [isVisible, posthog, survey]);

	const ensureSubmissionId = () => {
		if (submissionId) {
			return submissionId;
		}
		const generated =
			typeof crypto !== "undefined" && crypto.randomUUID
				? crypto.randomUUID()
				: `${Date.now()}`;
		setSubmissionId(generated);
		return generated;
	};

	const handleDismiss = (step: number) => {
		if (!survey || !posthog) {
			return;
		}
		posthog.capture("survey dismissed", {
			$survey_id: survey.id,
			$survey_name: survey.name,
			$current_step: step,
		});
		setIsVisible(false);
		setSurvey(null);
		setSubmissionId(null);
	};

	const handleProgress = (responses: Record<string, SurveyResponse>) => {
		if (!survey || !posthog) {
			return;
		}
		if (survey.enable_partial_responses === false) {
			return;
		}
		if (Object.keys(responses).length === 0) {
			return;
		}
		const id = ensureSubmissionId();
		posthog.capture(
			"survey partial",
			buildSurveyEventPayload({
				survey,
				responses,
				submissionId: id,
				completed: false,
			}),
		);
	};

	const handleComplete = (responses: Record<string, SurveyResponse>) => {
		if (!survey || !posthog) {
			return;
		}
		const id = ensureSubmissionId();
		posthog.capture(
			"survey sent",
			buildSurveyEventPayload({
				survey,
				responses,
				submissionId: id,
				completed: true,
			}),
		);
		setIsVisible(false);
		setSurvey(null);
		setSubmissionId(null);

		if (reloadOnComplete) {
			posthog.getActiveMatchingSurveys(() => {
				// No-op: purpose is to refresh the internal cache
			}, true);
		}
	};

	if (!survey || !isVisible) {
		return null;
	}

	return (
		<div className="fixed inset-x-0 bottom-0 z-[100] w-full px-4 pb-4 sm:inset-auto sm:bottom-6 sm:right-6 sm:w-auto sm:px-0 sm:pb-0 animate-in fade-in slide-in-from-bottom-4 sm:slide-in-from-bottom-0">
			<div className="mx-auto w-full sm:max-w-md">
				<div className="overflow-hidden rounded-lg border bg-background shadow-2xl">
					<SurveyContainer
						survey={survey}
						onComplete={handleComplete}
						onDismiss={handleDismiss}
						onProgress={handleProgress}
					/>
				</div>
			</div>
		</div>
	);
}

const buildSurveyEventPayload = ({
	survey,
	responses,
	submissionId,
	completed,
}: {
	survey: PostHogSurvey;
	responses: Record<string, SurveyResponse>;
	submissionId: string;
	completed: boolean;
}) => {
	const payload: Record<string, unknown> = {
		$survey_id: survey.id,
		$survey_name: survey.name,
		$survey_submission_id: submissionId,
		$survey_completed: completed,
		$survey_partially_completed: !completed,
		$survey_questions: survey.questions.map((question) => ({
			id: question.id,
			question: question.question,
			type: question.type,
		})),
	};

	if (typeof survey.current_iteration === "number") {
		payload.$survey_iteration = survey.current_iteration;
	}
	if (survey.current_iteration_start_date) {
		payload.$survey_iteration_start_date = survey.current_iteration_start_date;
	}

	survey.questions.forEach((question) => {
		const response = responses[question.id];
		if (response === undefined || response === null) {
			return;
		}
		const key = `$survey_response_${question.id}`;
		const value = transformResponseValue(question, response);
		if (value !== undefined) {
			payload[key] = value;
		}
	});

	return payload;
};

const transformResponseValue = (
	question: SurveyQuestion,
	response: SurveyResponse,
) => {
	if (response === null || response === undefined) {
		return undefined;
	}

	switch (question.type) {
		case "multiple_choice":
			return Array.isArray(response) ? response : [String(response)];
		case "rating": {
			if (typeof response === "number") {
				return response.toString();
			}
			const numeric = Number(response);
			return Number.isFinite(numeric) ? numeric.toString() : String(response);
		}
		default:
			if (Array.isArray(response)) {
				return response.map((value) => value.toString());
			}
			return String(response);
	}
};
