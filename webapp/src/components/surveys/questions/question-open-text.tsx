import {
	Field,
	FieldContent,
	FieldError,
	FieldLabel,
} from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import type { SurveyQuestion, SurveyResponse } from "@/types/survey";
import { QuestionDescription } from "../question-description";

type QuestionOpenTextQuestion = Pick<
	SurveyQuestion,
	"id" | "question" | "description" | "descriptionContentType" | "required"
>;

export interface QuestionOpenTextProps extends QuestionOpenTextQuestion {
	value: SurveyResponse;
	onChange: (value: string) => void;
	error?: string;
}

/**
 * Render an open-text survey question with label, description, multiline textarea, and optional validation error.
 *
 * @param id - Unique identifier for the question (used to build element ids for accessibility)
 * @param question - The question prompt text to display as the field label
 * @param description - Optional supplemental description or instructions for the question
 * @param descriptionContentType - Content type of `description` (defaults to "text")
 * @param required - If true, marks the field as required and displays an asterisk
 * @param value - Current response value; non-string values are treated as an empty string
 * @param onChange - Callback invoked with the updated string value when the textarea changes
 * @param error - Optional validation error message; when present, sets appropriate ARIA attributes and displays the error
 * @returns The form field comprising the label, description, textarea, and conditional error message
 */
export function QuestionOpenText({
	id,
	question: prompt,
	description,
	descriptionContentType = "text",
	required,
	value,
	onChange,
	error,
}: QuestionOpenTextProps) {
	const elementId = `survey-question-${id}`;
	const textValue = typeof value === "string" ? value : "";

	return (
		<Field data-invalid={error ? "true" : undefined}>
			<FieldLabel htmlFor={elementId}>
				{prompt}
				{required && <span className="text-destructive">*</span>}
			</FieldLabel>
			<FieldContent>
				<QuestionDescription
					description={description}
					descriptionContentType={descriptionContentType}
				/>
				<Textarea
					id={elementId}
					value={textValue}
					onChange={(event) => onChange(event.target.value)}
					placeholder="Type your answer here..."
					className="min-h-[120px] resize-none"
					aria-invalid={Boolean(error)}
					aria-describedby={error ? `${elementId}-error` : undefined}
				/>
				{error && <FieldError id={`${elementId}-error`}>{error}</FieldError>}
			</FieldContent>
		</Field>
	);
}