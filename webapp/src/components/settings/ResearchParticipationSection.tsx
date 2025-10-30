import { Microscope, ShieldCheck } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
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
			<div className="flex items-center gap-2">
				<Microscope className="h-5 w-5 text-muted-foreground" />
				<h2 className="text-lg font-semibold">Research Participation</h2>
				<Badge variant="outline" className="text-xs gap-1">
					<ShieldCheck className="h-3 w-3" />
					SIGSOFT Ethics Verified
				</Badge>
			</div>
			<div className="flex flex-row items-center justify-between">
				{isLoading ? (
					<>
						<div className="flex flex-col items-start">
							<Skeleton className="h-5 w-48 mb-2" />
							<Skeleton className="h-4 w-full mb-1" />
							<Skeleton className="h-4 w-full" />
						</div>
						<Skeleton className="h-5 w-10 rounded-full mr-2" />
					</>
				) : (
					<>
						<div className="flex flex-col items-start">
							<h3 className="text-balance font-medium">
								Help advance software engineering research
							</h3>
							<Label className="font-light text-pretty text-muted-foreground">
								Your anonymized usage data contributes to peer-reviewed academic
								research. All data is stripped of identifying information, used
								exclusively for scholarly purposes, and handled according to ACM
								SIGSOFT ethical guidelines. You maintain full control and can
								withdraw consent at any time.
							</Label>
						</div>
						<Switch
							className="mr-2"
							checked={participateInResearch}
							onCheckedChange={onToggleResearch}
							disabled={isLoading}
						/>
					</>
				)}
			</div>
		</div>
	);
}
