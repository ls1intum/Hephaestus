import { Activity } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

interface ActivitySummaryCardProps {
	username: string;
	displayName?: string;
	currUserIsDashboardUser: boolean;
	numberOfPullRequests: number;
	numberOfGoodPractices: number;
	numberOfBadPractices: number;
	isDetectingBadPractices: boolean;
	onDetectBadPractices: () => void;
}

export function ActivitySummaryCard({
	username,
	displayName,
	currUserIsDashboardUser,
	numberOfPullRequests,
	numberOfGoodPractices,
	numberOfBadPractices,
}: ActivitySummaryCardProps) {
	// Use displayName if available, otherwise fall back to username
	const userDisplayName = displayName || username;
	const userLabel = currUserIsDashboardUser ? "You have" : `${userDisplayName} has`;

	// Handle pluralization
	const prText = numberOfPullRequests === 1 ? "active pull request" : "active pull requests";
	const goodText = numberOfGoodPractices === 1 ? "good practice" : "good practices";
	const badText = numberOfBadPractices === 1 ? "area for improvement" : "areas for improvement";

	return (
		<Card className="mb-4">
			<CardHeader>
				<CardTitle>
					<Activity className="inline mr-2 h-4 w-4" /> Activity Summary
				</CardTitle>
				<CardDescription>
					<p className="text-muted-foreground">
						{userLabel}{" "}
						<span className="font-medium">
							{numberOfPullRequests} {prText}
						</span>{" "}
						with{" "}
						<span className="font-medium text-github-success-foreground">
							{numberOfGoodPractices} {goodText}
						</span>{" "}
						and{" "}
						<span className="font-medium text-github-attention-foreground">
							{numberOfBadPractices} {badText}
						</span>
						.
					</p>
				</CardDescription>
			</CardHeader>
			{/* {currUserIsDashboardUser && (
				<CardContent>
					<TooltipProvider>
						<Tooltip>
							<TooltipTrigger asChild>
								<Button
									variant="outline"
									className="w-full gap-2"
									onClick={onDetectBadPractices}
									disabled={isDetectingBadPractices}
								>
									{isDetectingBadPractices ? (
										<Spinner className="size-4" />
									) : (
										<RefreshCw className="size-4" />
									)}
									<span>Refresh Analysis</span>
								</Button>
							</TooltipTrigger>
							<TooltipContent>
								<p>Updates insights for all open pull requests</p>
							</TooltipContent>
						</Tooltip>
					</TooltipProvider>
				</CardContent>
			)} */}
		</Card>
	);
}
