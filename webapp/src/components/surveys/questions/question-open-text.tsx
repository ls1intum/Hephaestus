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
				{required && <span className="text-destructive ml-0.5">*</span>}
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
