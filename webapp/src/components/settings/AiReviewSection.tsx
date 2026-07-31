import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";

export interface AiReviewSectionProps {
	aiReviewEnabled: boolean;
	onToggleAiReview: (checked: boolean) => void;
	isLoading?: boolean;
}

export function AiReviewSection({
	aiReviewEnabled,
	onToggleAiReview,
	isLoading = false,
}: AiReviewSectionProps) {
	const pending = Boolean(isLoading);

	return (
		<section className="space-y-4" aria-labelledby="ai-review-heading">
			<div className="space-y-1">
				<h2 id="ai-review-heading" className="text-xl font-semibold">
					Practice feedback
				</h2>
				<p className="text-sm text-muted-foreground">
					Choose whether Hephaestus can send new practice feedback to you.
				</p>
			</div>

			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="ai-review-comments">Comments and Slack reminders</FieldLabel>
					<FieldDescription>
						When off, Hephaestus won't post practice feedback on pull or merge requests you author
						or send Slack reminders. Reviews still run, and workspace admins can still see the
						findings.
					</FieldDescription>
				</FieldContent>
				<Switch
					id="ai-review-comments"
					checked={aiReviewEnabled}
					onCheckedChange={onToggleAiReview}
					disabled={pending}
					aria-busy={pending}
				/>
			</Field>
		</section>
	);
}
