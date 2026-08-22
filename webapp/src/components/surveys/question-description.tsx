import { FieldDescription } from "@/components/ui/field";

interface QuestionDescriptionProps {
	description?: string | null;
	descriptionContentType?: "text" | "html";
	className?: string;
}

export function QuestionDescription({
	description,
	descriptionContentType = "text",
	className,
}: QuestionDescriptionProps) {
	if (!description) {
		return null;
	}

	if (descriptionContentType === "html") {
		return (
			<FieldDescription
				className={className}
				// Authored by whoever operates the PostHog project this deployment points at — never a
				// workspace member, and never a respondent, whose answers are rendered as text.
				// oxlint-disable-next-line react/no-danger
				dangerouslySetInnerHTML={{ __html: description }}
			/>
		);
	}

	return <FieldDescription className={className}>{description}</FieldDescription>;
}
