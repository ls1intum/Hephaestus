import { ExternalLink } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldTitle,
} from "@/components/ui/field";
import type { SurveyQuestion, SurveyResponse } from "@/types/survey";
import { QuestionDescription } from "../question-description";

type QuestionLinkQuestion = Pick<
	SurveyQuestion,
	| "id"
	| "question"
	| "description"
	| "descriptionContentType"
	| "required"
	| "buttonText"
	| "linkUrl"
>;

export interface QuestionLinkProps extends QuestionLinkQuestion {
	value: SurveyResponse;
	onChange: (value: string) => void;
	error?: string;
}

/**
 * Render a survey question with a button that opens an external link and records that the link was clicked.
 *
 * Clicking the button calls `onChange` with `linkUrl` if provided or the string `"link_clicked"`, and opens `linkUrl` in a new browser tab when present.
 *
 * @param question - The question prompt text to display as the field title.
 * @param description - Additional descriptive content shown beneath the title.
 * @param descriptionContentType - Content type for the description (e.g., "text" or "markdown").
 * @param required - When true, show a required indicator next to the title.
 * @param buttonText - Label for the action button; defaults to "Open link" when omitted.
 * @param linkUrl - Optional URL to open when the button is clicked; also used as the response value if present.
 * @param value - Current survey response value; a non-empty string is treated as "was clicked".
 * @param onChange - Callback invoked with the response value when the button is clicked.
 * @param error - Optional validation error message displayed under the control.
 * @returns A JSX element rendering the titled field, description, action button, optional confirmation text after click, and any error message.
 */
export function QuestionLink({
	question: prompt,
	description,
	descriptionContentType = "text",
	required,
	buttonText,
	linkUrl,
	value,
	onChange,
	error,
}: QuestionLinkProps) {
	const wasClicked = typeof value === "string" && value.length > 0;

	const handleClick = () => {
		const responseValue = linkUrl ?? "link_clicked";
		onChange(responseValue);

		if (linkUrl && typeof window !== "undefined") {
			window.open(linkUrl, "_blank", "noopener,noreferrer");
		}
	};

	return (
		<Field data-invalid={error ? "true" : undefined}>
			<FieldTitle>
				{prompt}
				{required && <span className="text-destructive ml-1">*</span>}
			</FieldTitle>
			<FieldContent>
				<QuestionDescription
					description={description}
					descriptionContentType={descriptionContentType}
				/>
				<Button
					onClick={handleClick}
					variant={wasClicked ? "secondary" : "default"}
					size="lg"
					className="w-fit"
				>
					{buttonText ?? "Open link"}
					<ExternalLink className="ml-2 h-4 w-4" aria-hidden="true" />
				</Button>
				{wasClicked && (
					<FieldDescription>Thanks for checking this out!</FieldDescription>
				)}
				{error && <FieldError>{error}</FieldError>}
			</FieldContent>
		</Field>
	);
}