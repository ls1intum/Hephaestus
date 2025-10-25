import type {
	SurveyQuestion as PostHogSurveyQuestion,
	Survey as PostHogSurveyRaw,
} from "posthog-js";
import { SurveyQuestionBranchingType } from "posthog-js";

export type SurveyQuestionType =
	| "open"
	| "link"
	| "rating"
	| "single_choice"
	| "multiple_choice";

export type SurveyResponse =
	| string
	| number
	| string[]
	| boolean
	| null
	| undefined;

export interface ConditionalBranching {
	type: "specific_question" | "response_based" | "end";
	specificQuestionId?: string;
	responseValues?: Record<string, string>;
}

export interface SurveyQuestion {
	id: string;
	type: SurveyQuestionType;
	question: string;
	description?: string | null;
	descriptionContentType?: "text" | "html";
	required: boolean;
	choices?: string[];
	scale?: number;
	display?: "number" | "emoji";
	buttonText?: string;
	lowerBoundLabel?: string;
	upperBoundLabel?: string;
	linkUrl?: string | null;
	hasOpenChoice?: boolean;
	shuffleOptions?: boolean;
	skipSubmitButton?: boolean;
	branching?: ConditionalBranching;
}

export interface PostHogSurvey {
	id: string;
	name: string;
	description?: string | null;
	type: "api" | "popover" | "widget" | "external_survey";
	questions: SurveyQuestion[];
	conditions?: PostHogSurveyRaw["conditions"];
	start_date?: string | null;
	end_date?: string | null;
	enable_partial_responses?: boolean | null;
	current_iteration?: number | null;
	current_iteration_start_date?: string | null;
}

export const SURVEY_RESPONSE_END = "__end__";

export const normalisePostHogSurvey = (
	survey: PostHogSurveyRaw,
): PostHogSurvey => {
	const normalizedIds = survey.questions.map(
		(question, index) => question.id ?? `${survey.id}-question-${index + 1}`,
	);

	const questions = survey.questions.map((question, index) =>
		normalizeQuestion({
			question,
			questionId: normalizedIds[index],
			allQuestions: survey.questions,
			normalizedIds,
		}),
	);

	return {
		id: survey.id,
		name: survey.name,
		description: survey.description ?? null,
		type: survey.type,
		questions,
		conditions: survey.conditions ?? undefined,
		start_date: survey.start_date,
		end_date: survey.end_date,
		enable_partial_responses: survey.enable_partial_responses,
		current_iteration: survey.current_iteration,
		current_iteration_start_date: survey.current_iteration_start_date,
	};
};

const normalizeQuestion = ({
	question,
	questionId,
	allQuestions,
	normalizedIds,
}: {
	question: PostHogSurveyQuestion;
	questionId: string;
	allQuestions: PostHogSurveyQuestion[];
	normalizedIds: string[];
}): SurveyQuestion => {
	const branching = question.branching
		? normalizeBranching({
				branching: question.branching,
				normalizedIds,
				allQuestions,
			})
		: undefined;

	switch (question.type) {
		case "link":
			return {
				id: questionId,
				type: "link",
				question: question.question,
				description: question.description ?? null,
				descriptionContentType: question.descriptionContentType ?? "text",
				required: !(question.optional ?? false),
				buttonText: question.buttonText ?? undefined,
				linkUrl: question.link ?? null,
				branching,
			};
		case "rating":
			return {
				id: questionId,
				type: "rating",
				question: question.question,
				description: question.description ?? null,
				descriptionContentType: question.descriptionContentType ?? "text",
				required: !(question.optional ?? false),
				buttonText: question.buttonText ?? undefined,
				scale: question.scale ?? 5,
				display: question.display ?? "number",
				lowerBoundLabel: question.lowerBoundLabel ?? undefined,
				upperBoundLabel: question.upperBoundLabel ?? undefined,
				branching,
			};
		case "single_choice":
		case "multiple_choice":
			return {
				id: questionId,
				type: question.type,
				question: question.question,
				description: question.description ?? null,
				descriptionContentType: question.descriptionContentType ?? "text",
				required: !(question.optional ?? false),
				buttonText: question.buttonText ?? undefined,
				choices: [...(question.choices ?? [])],
				hasOpenChoice: question.hasOpenChoice ?? false,
				shuffleOptions: question.shuffleOptions ?? undefined,
				skipSubmitButton: question.skipSubmitButton ?? undefined,
				branching,
			};
		default:
			return {
				id: questionId,
				type: "open",
				question: question.question,
				description: question.description ?? null,
				descriptionContentType: question.descriptionContentType ?? "text",
				required: !(question.optional ?? false),
				buttonText: question.buttonText ?? undefined,
				branching,
			};
	}
};

const normalizeBranching = ({
	branching,
	normalizedIds,
	allQuestions,
}: {
	branching: NonNullable<PostHogSurveyQuestion["branching"]>;
	normalizedIds: string[];
	allQuestions: PostHogSurveyQuestion[];
}): ConditionalBranching | undefined => {
	const resolveByIndex = (index: number | undefined): string | undefined => {
		if (typeof index !== "number") return undefined;
		return normalizedIds[index] ?? allQuestions[index]?.id;
	};

	switch (branching.type) {
		case SurveyQuestionBranchingType.End:
			return { type: "end" };
		case SurveyQuestionBranchingType.SpecificQuestion: {
			const index = (branching as { index?: number }).index;
			const target = resolveByIndex(index);
			if (!target) return undefined;
			return { type: "specific_question", specificQuestionId: target };
		}
		case SurveyQuestionBranchingType.ResponseBased: {
			const values = (branching as { responseValues?: Record<string, unknown> })
				.responseValues;
			if (!values) return undefined;
			const mapped: Record<string, string> = {};
			for (const [key, value] of Object.entries(values)) {
				const target = resolveBranchTarget(value, resolveByIndex);
				if (target) {
					mapped[key] = target;
				}
			}
			return Object.keys(mapped).length > 0
				? { type: "response_based", responseValues: mapped }
				: undefined;
		}
		default:
			return undefined;
	}
};

const resolveBranchTarget = (
	value: unknown,
	resolveByIndex: (index: number | undefined) => string | undefined,
): string | undefined => {
	if (typeof value === "number") {
		return resolveByIndex(value);
	}

	if (typeof value === "string") {
		if (value === "end" || value === "confirmation") {
			return SURVEY_RESPONSE_END;
		}
		return value;
	}

	if (value && typeof value === "object") {
		const candidateId =
			(value as { questionId?: string }).questionId ??
			(value as { specificQuestionId?: string }).specificQuestionId;
		if (typeof candidateId === "string") {
			return candidateId;
		}

		const maybeIndex = (value as { index?: number }).index;
		const byIndex = resolveByIndex(maybeIndex);
		if (byIndex) {
			return byIndex;
		}

		const maybeType = (value as { type?: string }).type;
		if (maybeType === "end" || maybeType === "confirmation") {
			return SURVEY_RESPONSE_END;
		}
	}

	return undefined;
};
