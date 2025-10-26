import { AlertCircle, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";

import type {
	PostHogSurvey,
	SurveyQuestion as SurveyQuestionType,
	SurveyResponse,
} from "@/types/survey";
import { SURVEY_RESPONSE_END } from "@/types/survey";
import { SurveyQuestion } from "./survey-question";

interface SurveyContainerProps {
	survey: PostHogSurvey;
	onComplete: (responses: Record<string, SurveyResponse>) => void;
	onDismiss: (currentStep: number) => void;
	onProgress?: (
		responses: Record<string, SurveyResponse>,
		meta: { currentStep: number; totalSteps: number },
	) => void;
}

type ErrorMap = Record<string, string>;

/**
 * Renders and manages a multi-step, branching survey UI and user interaction state.
 *
 * @param survey - The survey definition including questions, branching rules, and metadata.
 * @param onComplete - Called with collected responses when the survey is finished.
 * @param onDismiss - Called with the current step index when the survey is dismissed/closed.
 * @param onProgress - Optional callback invoked after advancing to a next step with the current responses and progress metadata.
 * @returns The survey UI element or `null` when there is no current question to display.
 */
export function SurveyContainer({
	survey,
	onComplete,
	onDismiss,
	onProgress,
}: SurveyContainerProps) {
	const [history, setHistory] = useState<number[]>([0]);
	const [responses, setResponses] = useState<Record<string, SurveyResponse>>(
		{},
	);
	const [errors, setErrors] = useState<ErrorMap>({});

	const currentStepIndex = history[history.length - 1] ?? 0;
	const currentQuestion = survey.questions[currentStepIndex];
	const totalSteps = survey.questions.length;
	const viewIndex = Math.min(currentStepIndex + 1, totalSteps);
	const progress = (viewIndex / Math.max(totalSteps, 1)) * 100;

	const surveyId = survey.id;

	useEffect(() => {
		void surveyId;
		setHistory([0]);
		setResponses({});
		setErrors({});
	}, [surveyId]);

	const handleResponse = (questionId: string, value: SurveyResponse) => {
		setResponses((prev) => ({ ...prev, [questionId]: value }));
		setErrors((prev) => {
			if (!prev[questionId]) return prev;
			const { [questionId]: _removed, ...rest } = prev;
			return rest;
		});
	};

	const handleBack = () => {
		if (history.length > 1) {
			setHistory((prev) => prev.slice(0, prev.length - 1));
		}
	};

	const handleClose = () => {
		onDismiss(currentStepIndex);
	};

	const handleNext = () => {
		if (!currentQuestion) return;
		const currentResponse = responses[currentQuestion.id];

		if (!validateQuestion(currentQuestion, currentResponse)) {
			setErrors((prev) => ({
				...prev,
				[currentQuestion.id]: "This question is required",
			}));
			return;
		}

		const snapshotResponses = {
			...responses,
			[currentQuestion.id]: currentResponse,
		};

		const nextStep = computeNextStep({
			question: currentQuestion,
			currentIndex: currentStepIndex,
			survey,
			response: currentResponse,
		});

		if (nextStep === null || nextStep >= totalSteps) {
			onComplete(snapshotResponses);
			return;
		}

		setHistory((prev) => [...prev, nextStep]);

		if (onProgress) {
			onProgress(snapshotResponses, { currentStep: nextStep, totalSteps });
		}
	};

	const isLastStep =
		!currentQuestion ||
		currentStepIndex === totalSteps - 1 ||
		currentQuestion.branching?.type === "end";

	if (!currentQuestion) {
		return null;
	}

	return (
		<div className="flex max-h-[85vh] flex-col sm:max-h-[600px]">
			<div className="flex items-start justify-between border-b p-4 pb-3">
				<div className="flex-1 pr-6 sm:pr-8">
					<h2 className="text-lg font-semibold sm:text-xl text-balance">
						{survey.name}
					</h2>
					{survey.description && (
						<p className="mt-1 text-xs text-muted-foreground sm:text-sm text-balance">
							{survey.description}
						</p>
					)}
				</div>
				<Button
					variant="ghost"
					size="icon"
					className="h-8 w-8 shrink-0"
					onClick={handleClose}
					aria-label="Close survey"
				>
					<X className="h-4 w-4" />
				</Button>
			</div>

			<div className="p-4 pb-3 sm:px-6 sm:pb-4">
				<div className="mb-2 flex items-center justify-between text-xs text-muted-foreground">
					<span>
						Question {currentStepIndex + 1} of {totalSteps}
					</span>
					<span>{Math.round(progress)}% complete</span>
				</div>
				<Progress value={progress} className="h-1.5" />
			</div>

			<div className="flex-1 overflow-y-auto px-4 pb-4 sm:px-6 sm:pb-6">
				<SurveyQuestion
					question={currentQuestion as SurveyQuestionType}
					value={responses[currentQuestion.id]}
					onChange={(nextValue) =>
						handleResponse(currentQuestion.id, nextValue)
					}
					error={errors[currentQuestion.id]}
				/>
			</div>

			<div className="flex items-center justify-between gap-2 border-t bg-muted/30 p-4 pt-3 sm:gap-3">
				<Button
					variant="outline"
					onClick={handleBack}
					disabled={history.length <= 1}
					className="flex-1 bg-transparent sm:flex-initial"
				>
					Back
				</Button>
				<Button onClick={handleNext} className="flex-1 sm:flex-initial">
					{isLastStep ? "Submit" : "Next"}
				</Button>
			</div>

			{errors.global && (
				<div className="px-4 pb-4 sm:px-6">
					<Alert variant="destructive">
						<AlertCircle className="h-4 w-4" />
						<AlertDescription>{errors.global}</AlertDescription>
					</Alert>
				</div>
			)}
		</div>
	);
}

const validateQuestion = (
	question: SurveyQuestionType,
	response: SurveyResponse,
): boolean => {
	if (!question.required) {
		return true;
	}

	switch (question.type) {
		case "link":
			return typeof response === "string" && response.length > 0;
		case "rating":
			return typeof response === "number";
		case "single_choice":
			return typeof response === "string" && response.length > 0;
		case "multiple_choice":
			return Array.isArray(response) && response.length > 0;
		default:
			return typeof response === "string" && response.trim().length > 0;
	}
};

const computeNextStep = ({
	question,
	currentIndex,
	survey,
	response,
}: {
	question: SurveyQuestionType;
	currentIndex: number;
	survey: PostHogSurvey;
	response: SurveyResponse;
}): number | null => {
	const branching = question.branching;
	if (!branching) {
		return currentIndex + 1;
	}

	switch (branching.type) {
		case "end":
			return null;
		case "specific_question": {
			if (!branching.specificQuestionId) {
				return currentIndex + 1;
			}
			const targetIndex = survey.questions.findIndex(
				(candidate) => candidate.id === branching.specificQuestionId,
			);
			return targetIndex >= 0 ? targetIndex : currentIndex + 1;
		}
		case "response_based": {
			const responseKey = resolveBranchingKey(question, response);
			const targetId = responseKey
				? branching.responseValues?.[responseKey]
				: undefined;

			if (!targetId) {
				return currentIndex + 1;
			}

			if (targetId === SURVEY_RESPONSE_END) {
				return null;
			}

			const targetIndex = survey.questions.findIndex(
				(candidate) => candidate.id === targetId,
			);
			return targetIndex >= 0 ? targetIndex : currentIndex + 1;
		}
		default:
			return currentIndex + 1;
	}
};

const resolveBranchingKey = (
	question: SurveyQuestionType,
	response: SurveyResponse,
): string | undefined => {
	if (response === null || response === undefined) {
		return undefined;
	}

	if (question.type === "rating" && typeof response === "number") {
		const scale = question.scale ?? 5;
		const bucket = classifyRatingBucket(response, scale);
		return bucket ?? response.toString();
	}

	if (typeof response === "string") {
		return response;
	}

	if (Array.isArray(response) && response.length > 0) {
		return response[0];
	}

	return undefined;
};

const classifyRatingBucket = (value: number, scale: number) => {
	if (scale <= 0) {
		return undefined;
	}

	const third = Math.max(Math.floor(scale / 3), 1);
	if (value <= third) {
		return "negative";
	}
	if (value >= scale - third + 1) {
		return "positive";
	}
	return "neutral";
};