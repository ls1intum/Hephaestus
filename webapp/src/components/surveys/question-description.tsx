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
				/** biome-ignore lint/security/noDangerouslySetInnerHtml: HTML content comes from Posthog */
				dangerouslySetInnerHTML={{ __html: description }}
			/>
		);
	}

	return (
		<FieldDescription className={className}>{description}</FieldDescription>
	);
}
