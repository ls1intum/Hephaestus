import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";

export interface ResearchSectionProps {
	/**
	 * Whether the user has opted out of research analytics
	 */
	researchOptOut: boolean;
	/**
	 * Callback when research opt-out setting is changed
	 */
	onToggleResearchOptOut: (checked: boolean) => void;
	/**
	 * Whether the component is in loading state
	 */
	isLoading?: boolean;
}

/**
 * ResearchSection component for managing research and analytics preferences
 * Allows users to opt out of PostHog analytics for research purposes
 */
export function ResearchSection({
	researchOptOut,
	onToggleResearchOptOut,
	isLoading = false,
}: ResearchSectionProps) {
	return (
		<div className="sm:w-2/3 w-full flex flex-col gap-3">
			<h2 className="text-lg font-semibold">Research & Analytics</h2>
			<div className="flex flex-row items-center justify-between">
				{isLoading ? (
					<>
						<span className="flex-col items-start">
							<Skeleton className="h-5 w-36 mb-2" />
							<Skeleton className="h-4 w-80" />
						</span>
						<Skeleton className="h-5 w-10 rounded-full mr-2" />
					</>
				) : (
					<>
						<span className="flex-col items-start">
							<h3>Opt out of research</h3>
							<Label className="font-light">
								Disable analytics and delete your data from our research
								platform.
							</Label>
						</span>
						<Switch
							className="mr-2"
							checked={researchOptOut}
							onCheckedChange={onToggleResearchOptOut}
							disabled={isLoading}
						/>
					</>
				)}
			</div>
		</div>
	);
}
