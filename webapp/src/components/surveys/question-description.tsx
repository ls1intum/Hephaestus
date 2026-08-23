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
				// oxlint-disable-next-line react/no-danger -- Authored by whoever operates the PostHog project this deployment points at, never by a workspace member and never by a respondent, whose answers render as text.
				dangerouslySetInnerHTML={{ __html: description }}
			/>
		);
	}

	return <FieldDescription className={className}>{description}</FieldDescription>;
}
