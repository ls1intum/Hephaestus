import {
	CheckIcon,
	CommentDiscussionIcon,
	CommentIcon,
	FileDiffIcon,
	IssueClosedIcon,
	IssueOpenedIcon,
} from "@primer/octicons-react";
import { MessageSquareReply } from "lucide-react";
import {
	getProviderTerms,
	getPullRequestStateIcon,
	type IconComponent,
	type ProviderType,
} from "@/lib/provider";

export type ActivityBadgeKey =
	| "changeRequests"
	| "approvals"
	| "comments"
	| "codeComments"
	| "ownReplies"
	| "openPullRequests"
	| "mergedPullRequests"
	| "closedPullRequests"
	| "openedIssues"
	| "closedIssues";

export interface ActivityBadgeMetadata {
	key: ActivityBadgeKey;
	label: string;
	tooltip: string;
	ariaLabel: (count: number) => string;
	icon: IconComponent;
	colorClass: string;
	countsTowardScore: boolean;
}

export interface ActivityLegendItem {
	key: "reviewedPullRequests" | ActivityBadgeKey;
	label: string;
	icon: IconComponent;
	colorClass: string;
	countsTowardScore: boolean;
}

export type ActivityBadgeCounts = Record<ActivityBadgeKey, number>;

export function getActivityBadgeMetadata(providerType: ProviderType): ActivityBadgeMetadata[] {
	const terms = getProviderTerms(providerType);
	const pullRequests = terms.pullRequests.toLowerCase();
	const { icon: OpenPullRequestIcon, colorClass: openPullRequestColorClass } =
		getPullRequestStateIcon(providerType, "OPEN");
	const { icon: MergedPullRequestIcon, colorClass: mergedPullRequestColorClass } =
		getPullRequestStateIcon(providerType, "MERGED");
	const { icon: ClosedPullRequestIcon, colorClass: closedPullRequestColorClass } =
		getPullRequestStateIcon(providerType, "CLOSED");

	return [
		{
			key: "changeRequests",
			label: "Changes requested",
			tooltip: "Changes Requested",
			ariaLabel: (count) => `${count} changes requested. Counts toward score.`,
			icon: FileDiffIcon,
			colorClass: "text-provider-danger-foreground",
			countsTowardScore: true,
		},
		{
			key: "approvals",
			label: "Approvals",
			tooltip: "Approvals",
			ariaLabel: (count) => `${count} approvals. Counts toward score.`,
			icon: CheckIcon,
			colorClass: "text-provider-success-foreground",
			countsTowardScore: true,
		},
		{
			key: "comments",
			label: "Review comments",
			tooltip: "Review comments",
			ariaLabel: (count) => `${count} comment reviews. Counts toward score.`,
			icon: CommentIcon,
			colorClass: "text-provider-muted-foreground",
			countsTowardScore: true,
		},
		{
			key: "codeComments",
			label: "Inline comments",
			tooltip: `Inline comments on someone else's ${pullRequests}. Counts toward score.`,
			ariaLabel: (count) =>
				`${count} scored inline feedback comments on ${pullRequests} authored by someone else. Counts toward score.`,
			icon: CommentDiscussionIcon,
			colorClass: "text-provider-muted-foreground",
			countsTowardScore: true,
		},
		{
			key: "ownReplies",
			label: `Replies on your own ${pullRequests}`,
			tooltip: `Replies on your own ${pullRequests}. Doesn't affect score.`,
			ariaLabel: (count) =>
				`${count} replies on your own ${pullRequests} and inline threads. Visible only and does not affect score.`,
			icon: MessageSquareReply,
			colorClass: "text-provider-muted-foreground",
			countsTowardScore: false,
		},
		{
			key: "openPullRequests",
			label: `Open ${pullRequests}`,
			tooltip: `Your open ${pullRequests}. Doesn't affect score.`,
			ariaLabel: (count) =>
				`${count} open ${pullRequests} you authored. Visible only and does not affect score.`,
			icon: OpenPullRequestIcon,
			colorClass: openPullRequestColorClass,
			countsTowardScore: false,
		},
		{
			key: "mergedPullRequests",
			label: `Merged ${pullRequests}`,
			tooltip: `Your merged ${pullRequests}. Doesn't affect score.`,
			ariaLabel: (count) =>
				`${count} merged ${pullRequests} you authored. Visible only and does not affect score.`,
			icon: MergedPullRequestIcon,
			colorClass: mergedPullRequestColorClass,
			countsTowardScore: false,
		},
		{
			key: "closedPullRequests",
			label: `Closed ${pullRequests}`,
			tooltip: `Your closed ${pullRequests}. Doesn't affect score.`,
			ariaLabel: (count) =>
				`${count} closed ${pullRequests} you authored. Visible only and does not affect score.`,
			icon: ClosedPullRequestIcon,
			colorClass: closedPullRequestColorClass,
			countsTowardScore: false,
		},
		{
			key: "openedIssues",
			label: "Opened issues",
			tooltip: "Issues you opened. Doesn't affect score.",
			ariaLabel: (count) => `${count} opened issues. Visible only and does not affect score.`,
			icon: IssueOpenedIcon,
			colorClass: "text-provider-open-foreground",
			countsTowardScore: false,
		},
		{
			key: "closedIssues",
			label: "Closed issues",
			tooltip: "Issues you closed. Doesn't affect score.",
			ariaLabel: (count) => `${count} closed issues. Visible only and does not affect score.`,
			icon: IssueClosedIcon,
			colorClass: "text-provider-done-foreground",
			countsTowardScore: false,
		},
	];
}

export function getActivityLegendItems(providerType: ProviderType): ActivityLegendItem[] {
	const terms = getProviderTerms(providerType);
	const { icon: PullRequestIcon } = getPullRequestStateIcon(providerType, "OPEN");

	return [
		{
			key: "reviewedPullRequests",
			label: `Reviewed ${terms.pullRequests.toLowerCase()}`,
			icon: PullRequestIcon,
			colorClass: "text-provider-muted-foreground",
			countsTowardScore: true,
		},
		...getActivityBadgeMetadata(providerType).map(
			({ key, label, icon, colorClass, countsTowardScore }) => ({
				key,
				label,
				icon,
				colorClass,
				countsTowardScore,
			}),
		),
	];
}
