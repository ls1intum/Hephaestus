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
				// The only raw-HTML sink in the app. The string is a survey question's description as
				// configured in the PostHog project this deployment is wired to, so its author is whoever
				// operates that project — never a workspace member, and never a respondent, whose answers
				// are rendered as text.
				// oxlint-disable-next-line react/no-danger
				dangerouslySetInnerHTML={{ __html: description }}
			/>
		);
	}

	return <FieldDescription className={className}>{description}</FieldDescription>;
}
