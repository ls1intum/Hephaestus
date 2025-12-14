import {
	type PostHogSurvey,
	SURVEY_RESPONSE_END,
	type SurveyQuestion,
	type SurveyResponse,
} from "@/types/survey";

const DEFAULT_FALLBACK_KEYS = [
	"default",
	"fallback",
	"*",
	"__default__",
	"__fallback__",
];
const OPEN_CHOICE_SENTINELS = [
	"__other__",
	"__custom__",
	"other",
	"Other",
	"custom",
	"Custom",
];

const TRUE_KEYS = ["true", "True", "1", "yes", "Yes"];
const FALSE_KEYS = ["false", "False", "0", "no", "No"];

export interface BranchingContext {
	survey: PostHogSurvey;
	currentIndex: number;
	question: SurveyQuestion;
	response: SurveyResponse;
}

export function determineNextQuestionIndex({
	survey,
	currentIndex,
	question,
	response,
}: BranchingContext): number | null {
	const branching = question.branching;
	if (!branching) {
		return currentIndex + 1;
	}

	switch (branching.type) {
		case "end":
			return null;
		case "specific_question": {
			const targetId = branching.specificQuestionId;
			if (!targetId) {
				return currentIndex + 1;
			}
			const targetIndex = survey.questions.findIndex(
				(candidate) => candidate.id === targetId,
			);
			return targetIndex >= 0 ? targetIndex : currentIndex + 1;
		}
		case "response_based": {
			const targetId = resolveResponseBranchTarget(
				question,
				response,
				branching.responseValues ?? {},
			);
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
}

export function resolveResponseBranchTarget(
	question: SurveyQuestion,
	response: SurveyResponse,
	responseValues: Record<string, string>,
): string | undefined {
	if (Object.keys(responseValues).length === 0) {
		return undefined;
	}

	const candidates = collectResponseKeys(question, response);
	const fallbackKeys = DEFAULT_FALLBACK_KEYS.filter(
		(key) => !candidates.includes(key),
	);
	const lookupOrder = [...candidates, ...fallbackKeys];

	for (const key of lookupOrder) {
		if (key in responseValues) {
			return responseValues[key];
		}
	}

	return undefined;
}

export function collectResponseKeys(
	question: SurveyQuestion,
	response: SurveyResponse,
): string[] {
	const seen = new Set<string>();
	const keys: string[] = [];

	const add = (value: string | undefined | null) => {
		if (!value) {
			return;
		}
		if (!seen.has(value)) {
			seen.add(value);
			keys.push(value);
		}
	};

	const addWithVariants = (raw: string) => {
		add(raw);
		const trimmed = raw.trim();
		if (trimmed && trimmed !== raw) {
			add(trimmed);
		}
		const lower = raw.toLowerCase();
		if (lower && lower !== raw) {
			add(lower);
		}
		const lowerTrimmed = lower.trim();
		if (lowerTrimmed && lowerTrimmed !== lower) {
			add(lowerTrimmed);
		}
	};

	if (response === null || response === undefined) {
		return keys;
	}

	if (question.type === "rating") {
		if (typeof response === "number" && Number.isFinite(response)) {
			const bucket = classifyRatingBucket(response, question.scale ?? 5);
			add(bucket);
			add(response.toString());
			add(response.toFixed(0));
		}
		return keys;
	}

	if (Array.isArray(response)) {
		addArrayResponses(question, response, addWithVariants, add);
		return keys;
	}

	if (typeof response === "string") {
		addWithVariants(response);
		addChoiceIndexKey(question, response, add);
		addOpenChoiceSentinelsIfNeeded(question, [response], add);
		return keys;
	}

	if (typeof response === "number" && Number.isFinite(response)) {
		add(response.toString());
		add(response.toFixed(0));
		return keys;
	}

	if (typeof response === "boolean") {
		const booleanKeys = response ? TRUE_KEYS : FALSE_KEYS;
		booleanKeys.forEach(add);
	}

	return keys;
}

function addArrayResponses(
	question: SurveyQuestion,
	values: SurveyResponse,
	addWithVariants: (value: string) => void,
	add: (value?: string | null) => void,
) {
	if (!Array.isArray(values)) {
		return;
	}

	const asStrings = values
		.map((value) => {
			if (typeof value === "string") {
				return value;
			}
			return value != null ? String(value) : "";
		})
		.filter((value): value is string => value.length > 0);

	for (const item of asStrings) {
		addWithVariants(item);
		addChoiceIndexKey(question, item, add);
	}

	addOpenChoiceSentinelsIfNeeded(question, asStrings, add);
}

function addChoiceIndexKey(
	question: SurveyQuestion,
	answer: string,
	add: (value?: string | null) => void,
) {
	const choices = question.choices ?? [];
	if (choices.length === 0) {
		return;
	}

	const trimmedAnswer = answer.trim();
	const index = choices.findIndex((choice) => {
		if (choice === answer) {
			return true;
		}
		if (choice.trim() === trimmedAnswer) {
			return true;
		}
		return false;
	});

	if (index >= 0) {
		add(String(index));
	}
}

function addOpenChoiceSentinelsIfNeeded(
	question: SurveyQuestion,
	responses: string[],
	add: (value?: string | null) => void,
) {
	if (!question.hasOpenChoice) {
		return;
	}

	const choices = question.choices ?? [];
	const baseChoices = choices.length > 0 ? choices.slice(0, -1) : choices;
	const openChoiceLabel =
		choices.length > 0 ? choices[choices.length - 1] : undefined;
	const hasCustomSelection = responses.some(
		(value) => !baseChoices.includes(value),
	);

	if (!hasCustomSelection) {
		return;
	}

	OPEN_CHOICE_SENTINELS.forEach(add);
	add(openChoiceLabel);
}

function classifyRatingBucket(
	value: number,
	scale: number,
): string | undefined {
	if (!Number.isFinite(value) || !Number.isFinite(scale) || scale <= 0) {
		return undefined;
	}

	const upperBound = Math.max(Math.round(scale), 1);
	const bounded = Math.min(Math.max(Math.round(value), 1), upperBound);

	const third = Math.max(Math.floor(upperBound / 3), 1);
	if (bounded <= third) {
		return "negative";
	}
	if (bounded >= upperBound - third + 1) {
		return "positive";
	}
	return "neutral";
}
