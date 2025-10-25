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
