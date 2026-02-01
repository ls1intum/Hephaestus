import { AlertCircle, CheckCircle2, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { ProgressIndicator, ProgressTrack } from "@/components/ui/progress";

import type {
	PostHogSurvey,
	SurveyQuestion as SurveyQuestionType,
	SurveyResponse,
} from "@/types/survey";
import { SurveyQuestion } from "./survey-question";
import { determineNextQuestionIndex } from "./utils/branching";

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

/** Shared header with close button */
function SurveyHeader({
	title,
	description,
	onClose,
}: {
	title: string;
	description?: string;
	onClose: () => void;
}) {
	return (
		<div className="flex items-start justify-between border-b p-4 pb-3">
			<div className="flex-1 pr-6 sm:pr-8">
				<h2 className="text-lg font-semibold sm:text-xl text-balance">{title}</h2>
				{description && (
					<p className="mt-1 text-xs text-muted-foreground sm:text-sm text-balance">
						{description}
					</p>
				)}
			</div>
			<Button
				variant="ghost"
				size="icon"
				className="h-8 w-8 shrink-0"
				onClick={onClose}
				aria-label="Close survey"
			>
				<X className="h-4 w-4" />
			</Button>
		</div>
	);
}

export function SurveyContainer({
	survey,
	onComplete,
	onDismiss,
	onProgress,
}: SurveyContainerProps) {
	const [history, setHistory] = useState<number[]>([0]);
	const [responses, setResponses] = useState<Record<string, SurveyResponse>>({});
	const [errors, setErrors] = useState<ErrorMap>({});
	const [completedResponses, setCompletedResponses] = useState<Record<
		string,
		SurveyResponse
	> | null>(null);

	const isCompleted = completedResponses !== null;

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
		setCompletedResponses(null);
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
		if (completedResponses) {
			onComplete(completedResponses);
		} else {
			onDismiss(currentStepIndex);
		}
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

		const nextStep = determineNextQuestionIndex({
			survey,
			currentIndex: currentStepIndex,
			question: currentQuestion,
			response: currentResponse,
		});

		if (nextStep === null || nextStep >= totalSteps) {
			// Report final progress before showing thank you screen
			onProgress?.(snapshotResponses, {
				currentStep: totalSteps,
				totalSteps,
			});
			setCompletedResponses(snapshotResponses);
			return;
		}

		setHistory((prev) => [...prev, nextStep]);
		onProgress?.(snapshotResponses, { currentStep: nextStep, totalSteps });
	};

	const isLastStep =
		!currentQuestion ||
		currentStepIndex === totalSteps - 1 ||
		currentQuestion.branching?.type === "end";

	// Thank you screen
	if (isCompleted) {
		return (
			<div className="flex max-h-[85vh] flex-col sm:max-h-[600px]">
				<SurveyHeader title={survey.name} onClose={handleClose} />

				<Empty className="border-0">
					<EmptyMedia variant="icon">
						<CheckCircle2 />
					</EmptyMedia>
					<EmptyTitle>Thank you for your participation.</EmptyTitle>
					<EmptyDescription>
						Your insights are critical for our research into making collaborative software
						engineering more effective.
					</EmptyDescription>
					<EmptyContent>
						<Button onClick={handleClose}>Close survey</Button>
					</EmptyContent>
				</Empty>
			</div>
		);
	}

	if (!currentQuestion) {
		return null;
	}

	return (
		<div className="flex max-h-[85vh] flex-col sm:max-h-[600px]">
			<SurveyHeader
				title={survey.name}
				description={survey.description ?? undefined}
				onClose={handleClose}
			/>

			<div className="p-4 pb-3 sm:px-6 sm:pb-4">
				<div className="mb-2 flex items-center justify-between text-xs text-muted-foreground">
					<span>
						Question {currentStepIndex + 1} of {totalSteps}
					</span>
					<span>{Math.round(progress)}% complete</span>
				</div>
				<ProgressRoot.Root value={progress}>
					<ProgressTrack className="h-1.5">
						<ProgressIndicator className="absolute" />
					</ProgressTrack>
				</ProgressRoot.Root>
			</div>

			<div className="flex-1 overflow-y-auto px-4 pb-4 sm:px-6 sm:pb-6">
				<SurveyQuestion
					question={currentQuestion as SurveyQuestionType}
					value={responses[currentQuestion.id]}
					onChange={(nextValue) => handleResponse(currentQuestion.id, nextValue)}
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

const validateQuestion = (question: SurveyQuestionType, response: SurveyResponse): boolean => {
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
