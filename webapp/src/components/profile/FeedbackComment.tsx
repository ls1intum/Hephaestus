import { useId, useState } from "react";

import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";

export interface FeedbackCommentProps {
	/** The comment already recorded, if any. Editing starts from it and is discarded on cancel. */
	comment?: string;
	/** The server requires an explanation for a disputed observation and rejects an empty one. */
	isRequired: boolean;
	isPending?: boolean;
	onSave: (comment: string | undefined) => void;
}

/**
 * The written half of a response. The draft stays local until saved, so a keystroke sends no request
 * and abandoning it leaves the recorded comment untouched.
 */
export function FeedbackComment({ comment, isRequired, isPending, onSave }: FeedbackCommentProps) {
	const [draft, setDraft] = useState(comment ?? "");
	const fieldId = useId();
	const trimmed = draft.trim();
	const isMissing = isRequired && trimmed === "";
	const isUnchanged = trimmed === (comment ?? "").trim();

	return (
		<Field data-invalid={isMissing ? true : undefined}>
			<FieldLabel htmlFor={fieldId}>
				{isRequired ? "Why do you disagree?" : "Anything to add? (optional)"}
			</FieldLabel>
			<Textarea
				id={fieldId}
				value={draft}
				aria-invalid={isMissing ? true : undefined}
				disabled={isPending}
				placeholder={
					isRequired
						? "What did the review get wrong?"
						: "Context that would help whoever reads this later"
				}
				onChange={(event) => setDraft(event.target.value)}
			/>
			{isMissing ? (
				<FieldError>An explanation is required when you dispute an observation.</FieldError>
			) : (
				<FieldDescription>Only people who can see this review will read it.</FieldDescription>
			)}
			<div className="flex flex-wrap gap-2">
				<Button
					type="button"
					size="sm"
					disabled={Boolean(isPending) || isMissing || isUnchanged}
					onClick={() => onSave(trimmed === "" ? undefined : trimmed)}
				>
					Save comment
				</Button>
				{!isUnchanged && (
					<Button
						type="button"
						size="sm"
						variant="ghost"
						disabled={isPending}
						onClick={() => setDraft(comment ?? "")}
					>
						Cancel
					</Button>
				)}
			</div>
		</Field>
	);
}
