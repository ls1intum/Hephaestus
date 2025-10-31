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
	return (
		<div className="sm:w-2/3 w-full flex flex-col gap-3">
			<h2 className="text-lg font-semibold">Research Participation</h2>
			<div className="flex flex-row items-center justify-between">
				<div className="flex flex-col items-start max-w-xl">
					<h3>Help improve Hephaestus</h3>
					<Label className="font-light">
						Share which features you use and how you interact with them to
						support academic research and product improvements. May include
						occasional surveys. Data is only accessible to administrators.
					</Label>
				</div>
				<Switch
					aria-busy={isLoading}
					className="mr-2"
					checked={participateInResearch}
					onCheckedChange={onToggleResearch}
					disabled={isLoading}
				/>
			</div>
		</div>
	);
}
