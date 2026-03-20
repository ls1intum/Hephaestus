import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

export interface AiReviewSectionProps {
	/**
	 * Whether the user has AI review comments enabled
	 */
	aiReviewEnabled: boolean;
	/**
	 * Callback when the AI review setting changes
	 */
	onToggleAiReview: (checked: boolean) => void;
	/**
	 * Whether the component is in loading state
	 */
	isLoading?: boolean;
}

/**
 * AiReviewSection component for managing AI-generated review comment preferences.
 * Gated by the run_practice_review Keycloak role (visibility controlled by parent).
 */
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
					AI Review
				</h2>
				<p className="text-sm text-muted-foreground">Configure AI-powered feedback on your code</p>
			</div>

			<div className="flex items-start justify-between gap-6 py-4">
				<div className="space-y-1 flex-1">
					<Label htmlFor="ai-review-comments" className="text-base font-medium cursor-pointer">
						Practice review comments
					</Label>
					<p className="text-sm text-muted-foreground leading-relaxed">
						Receive AI-generated practice review comments on your pull requests.
					</p>
				</div>
				<Switch
					id="ai-review-comments"
					className="mt-1"
					checked={aiReviewEnabled}
					onCheckedChange={onToggleAiReview}
					disabled={pending}
					aria-busy={pending}
					aria-label="Toggle AI review comments"
				/>
			</div>
		</section>
	);
}
