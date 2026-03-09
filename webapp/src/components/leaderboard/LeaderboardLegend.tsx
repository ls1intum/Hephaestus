import {
	CheckIcon,
	CommentDiscussionIcon,
	CommentIcon,
	FileDiffIcon,
	InfoIcon,
} from "@primer/octicons-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getProviderTerms, getPullRequestStateIcon, type ProviderType } from "@/lib/provider";
import { ScoringExplanationDialog } from "./ScoringExplanationDialog";

export function LeaderboardLegend({ providerType = "GITHUB" }: { providerType?: ProviderType }) {
	const [showScoringModal, setShowScoringModal] = useState(false);
	const terms = getProviderTerms(providerType);
	const { icon: PrIcon } = getPullRequestStateIcon(providerType, "OPEN");

	return (
		<>
			<Card>
				<CardHeader>
					<CardTitle>
						<InfoIcon className="inline mr-2 h-4 w-4" /> Activity Legend
					</CardTitle>
					<CardDescription>Understanding the leaderboard activity indicators</CardDescription>
				</CardHeader>
				<CardContent>
					<div className="space-y-3">
						<div className="grid grid-cols-1 gap-2">
							<div className="flex items-center gap-2 text-provider-muted-foreground">
								<PrIcon className="h-4 w-4" size={16} />
								<span>Reviewed {terms.pullRequests.toLowerCase()}</span>
							</div>
							<div className="flex items-center gap-2 text-provider-danger-foreground">
								<FileDiffIcon className="h-4 w-4" />
								<span>Changes requested</span>
							</div>
							<div className="flex items-center gap-2 text-provider-success-foreground">
								<CheckIcon className="h-4 w-4" />
								<span>Approvals</span>
							</div>
							<div className="flex items-center gap-2 text-provider-muted-foreground">
								<CommentIcon className="h-4 w-4" />
								<span>Comments</span>
							</div>
							<div className="flex items-center gap-2 text-provider-muted-foreground">
								<CommentDiscussionIcon className="h-4 w-4" />
								<span>Code comments</span>
							</div>
						</div>

						<div className="pt-2 border-t">
							<p className="text-sm text-provider-muted-foreground mb-2">
								Your score combines your review activity with the complexity of the{" "}
								{terms.pullRequests.toLowerCase()} you've reviewed. Score calculation weighs change
								requests highest, followed by approvals and comments.
							</p>
							<Button
								variant="outline"
								size="sm"
								className="text-provider-link-foreground"
								onClick={() => setShowScoringModal(true)}
							>
								View scoring formula
							</Button>
						</div>
					</div>
				</CardContent>
			</Card>

			<ScoringExplanationDialog
				open={showScoringModal}
				onOpenChange={setShowScoringModal}
				providerType={providerType}
			/>
		</>
	);
}
