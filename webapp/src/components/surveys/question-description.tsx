import { FieldDescription } from "@/components/ui/field";

interface QuestionDescriptionProps {
	description?: string | null;
	descriptionContentType?: "text" | "html";
	className?: string;
}

/**
 * Renders a question description inside FieldDescription, interpreting content as text or HTML.
 *
 * @param description - The description content; if falsy, nothing is rendered.
 * @param descriptionContentType - Determines how `description` is interpreted: `"text"` renders it as plain text, `"html"` renders it as HTML.
 * @param className - Optional class name forwarded to the FieldDescription container.
 * @returns A JSX element containing the rendered description, or `null` when `description` is falsy.
 */
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
				/** biome-ignore lint/security/noDangerouslySetInnerHtml: HTML content comes from PostHog */
				dangerouslySetInnerHTML={{ __html: description }}
			/>
		);
	}

	return (
		<FieldDescription className={className}>{description}</FieldDescription>
	);
}