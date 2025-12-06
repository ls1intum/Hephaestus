import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

export interface ResearchParticipationSectionProps {
	/**
	 * Whether the user participates in research
	 */
	participateInResearch: boolean;
	/**
	 * Callback when the research participation setting changes
	 */
	onToggleResearch: (checked: boolean) => void;
	/**
	 * Whether the component is in loading state
	 */
	isLoading?: boolean;
}

/**
 * ResearchParticipationSection component for managing research consent
 * Provides context about data usage and an opt-out toggle
 */
export function ResearchParticipationSection({
	participateInResearch,
	onToggleResearch,
	isLoading = false,
}: ResearchParticipationSectionProps) {
	const pending = Boolean(isLoading);

	return (
		<section className="space-y-4" aria-labelledby="research-heading">
			<div className="space-y-1">
				<h2 id="research-heading" className="text-xl font-semibold">
					Research Participation
				</h2>
				<p className="text-sm text-muted-foreground">
					Help us improve through research
				</p>
			</div>

			<div className="flex items-start justify-between gap-6 py-4">
				<div className="space-y-1 flex-1">
					<Label
						htmlFor="research-participation"
						className="text-base font-medium cursor-pointer"
					>
						Help improve Hephaestus
					</Label>
					<p className="text-sm text-muted-foreground leading-relaxed">
						Share which features you use and how you interact with them to
						support academic research and product improvements. May include
						occasional surveys. Data is only accessible to administrators.
					</p>
				</div>
				<Switch
					id="research-participation"
					className="mt-1"
					checked={participateInResearch}
					onCheckedChange={onToggleResearch}
					disabled={pending}
					aria-busy={pending}
					aria-label="Toggle research participation"
				/>
			</div>
		</section>
	);
}
