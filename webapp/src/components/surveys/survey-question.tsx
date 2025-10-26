import type {
	SurveyQuestion as SurveyQuestionType,
	SurveyResponse,
} from "@/types/survey";
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

/**
 * Renders the appropriate survey question UI for the given question definition.
 *
 * Renders a question-specific component (open text, link, rating, single-choice, or multiple-choice)
 * and wires its value updates to the provided `onChange` handler.
 *
 * @param question - Survey question definition including type, id, text, and type-specific properties
 * @param value - Current response value for the question
 * @param onChange - Called with the updated response value when the user changes their answer
 * @param error - Optional validation or display error message to show for the question
 * @returns A JSX element for the specific question component, or `null` if the question type is unrecognized
 */
export function SurveyQuestion({
	question,
	value,
	onChange,
	error,
}: SurveyQuestionProps) {
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