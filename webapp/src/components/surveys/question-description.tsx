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
				// oxlint-disable-next-line react/no-danger -- survey HTML is authored in PostHog by an admin of this instance, not by a respondent
				dangerouslySetInnerHTML={{ __html: description }}
			/>
		);
	}

	return <FieldDescription className={className}>{description}</FieldDescription>;
}
