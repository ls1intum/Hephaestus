import {
	CheckIcon,
	CommentDiscussionIcon,
	CommentIcon,
	FileDiffIcon,
	InfoIcon,
	IssueClosedIcon,
	IssueOpenedIcon,
} from "@primer/octicons-react";
import { MessageSquareReply } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getProviderTerms, getPullRequestStateIcon, type ProviderType } from "@/lib/provider";
import { ScoringExplanationDialog } from "./ScoringExplanationDialog";

export function LeaderboardLegend({ providerType = "GITHUB" }: { providerType?: ProviderType }) {
	const [showScoringModal, setShowScoringModal] = useState(false);
	const terms = getProviderTerms(providerType);
	const { icon: PrIcon } = getPullRequestStateIcon(providerType, "OPEN");
	const { icon: MergedPrIcon } = getPullRequestStateIcon(providerType, "MERGED");
	const { icon: ClosedPrIcon } = getPullRequestStateIcon(providerType, "CLOSED");

	return (
		<>
			<Card>
				<CardHeader>
					<CardTitle>
						<InfoIcon className="inline mr-2 h-4 w-4" /> Activity Legend
					</CardTitle>
					<CardDescription>What counts toward score, and what is just shown.</CardDescription>
				</CardHeader>
				<CardContent>
					<div className="space-y-3">
						<div className="space-y-2">
							<p className="text-sm font-medium">Counts toward score</p>
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
									<span>Review comments</span>
								</div>
								<div className="flex items-center gap-2 text-provider-muted-foreground">
									<CommentDiscussionIcon className="h-4 w-4" />
									<span>Inline comments</span>
								</div>
							</div>
						</div>

						<div className="space-y-2 pt-2 border-t">
							<p className="text-sm font-medium">Also shown</p>
							<div className="grid grid-cols-1 gap-2">
								<div className="flex items-center gap-2 text-provider-muted-foreground">
									<MessageSquareReply className="h-4 w-4" />
									<span>Replies on your own {terms.pullRequests.toLowerCase()}</span>
								</div>
								<div className="flex items-center gap-2 text-provider-open-foreground">
									<PrIcon className="h-4 w-4" size={16} />
									<span>Open {terms.pullRequests.toLowerCase()}</span>
								</div>
								<div className="flex items-center gap-2 text-provider-done-foreground">
									<MergedPrIcon className="h-4 w-4" size={16} />
									<span>Merged {terms.pullRequests.toLowerCase()}</span>
								</div>
								<div className="flex items-center gap-2 text-provider-closed-foreground">
									<ClosedPrIcon className="h-4 w-4" size={16} />
									<span>Closed {terms.pullRequests.toLowerCase()}</span>
								</div>
								<div className="flex items-center gap-2 text-provider-open-foreground">
									<IssueOpenedIcon className="h-4 w-4" />
									<span>Opened issues</span>
								</div>
								<div className="flex items-center gap-2 text-provider-done-foreground">
									<IssueClosedIcon className="h-4 w-4" />
									<span>Closed issues</span>
								</div>
							</div>
						</div>

						<div className="pt-2 border-t">
							<p className="text-sm text-provider-muted-foreground mb-2">
								Only reviews affect score. Everything else is shown for context.
							</p>
							<Button
								variant="outline"
								size="sm"
								className="text-provider-link-foreground"
								onClick={() => setShowScoringModal(true)}
							>
								How scoring works
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
