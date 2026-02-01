import type { SurveyQuestion as SurveyQuestionType, SurveyResponse } from "@/types/survey";
import { QuestionLink } from "./questions/question-link";
import { QuestionMultipleChoice } from "./questions/question-multiple-choice";
import { QuestionOpenText } from "./questions/question-open-text";
import { QuestionRating } from "./questions/question-rating";
import { QuestionSingleChoice } from "./questions/question-single-choice";

interface SurveyQuestionProps {
	question: SurveyQuestionType;
	value: SurveyResponse;
	onChange: (value: SurveyResponse) => void;
	error?: string;
}

export function SurveyQuestion({ question, value, onChange, error }: SurveyQuestionProps) {
	const commonProps = {
		id: question.id,
		question: question.question,
		description: question.description,
		descriptionContentType: question.descriptionContentType,
		required: question.required,
	};
	switch (question.type) {
		case "open":
			return (
				<QuestionOpenText
					{...commonProps}
					value={value}
					onChange={(nextValue) => onChange(nextValue)}
					error={error}
				/>
			);
		case "link":
			return (
				<QuestionLink
					{...commonProps}
					buttonText={question.buttonText}
					linkUrl={question.linkUrl ?? undefined}
					value={value}
					onChange={(nextValue) => onChange(nextValue)}
					error={error}
				/>
			);
		case "rating":
			return (
				<QuestionRating
					{...commonProps}
					display={question.display}
					scale={question.scale}
					lowerBoundLabel={question.lowerBoundLabel}
					upperBoundLabel={question.upperBoundLabel}
					value={value}
					onChange={(nextValue) => onChange(nextValue)}
					error={error}
				/>
			);
		case "single_choice":
			return (
				<QuestionSingleChoice
					{...commonProps}
					choices={question.choices}
					hasOpenChoice={question.hasOpenChoice}
					value={value}
					onChange={(nextValue) => onChange(nextValue)}
					error={error}
				/>
			);
		case "multiple_choice":
			return (
				<QuestionMultipleChoice
					{...commonProps}
					choices={question.choices}
					hasOpenChoice={question.hasOpenChoice}
					value={value}
					onChange={(nextValue) => onChange(nextValue)}
					error={error}
				/>
			);
		default:
			return null;
	}
}
