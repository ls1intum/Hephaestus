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

export interface ActivityBadgeMetadata {
	key: ActivityBadgeKey;
	label: string;
	tooltip: string;
	ariaLabel: (count: number) => string;
	icon: IconComponent;
	colorClass: string;
	primaryReviewSignal: boolean;
}

function buildActivityBadgeMetadata(providerType: ProviderType) {
	const pullRequests = getProviderTerms(providerType).pullRequests.toLowerCase();
	const open = getPullRequestStateIcon(providerType, "OPEN");
	const merged = getPullRequestStateIcon(providerType, "MERGED");
	const closed = getPullRequestStateIcon(providerType, "CLOSED");

	return [
		{
			key: "changeRequests",
			primaryReviewSignal: true,
			icon: FileDiffIcon,
			colorClass: "text-provider-danger-foreground",
			label: "Changes requested",
			tooltip: "Changes Requested",
			ariaLabel: (count: number) => `${count} changes requested. Part of review activity.`,
		},
		{
			key: "approvals",
			primaryReviewSignal: true,
			icon: CheckIcon,
			colorClass: "text-provider-success-foreground",
			label: "Approvals",
			tooltip: "Approvals",
			ariaLabel: (count: number) => `${count} approvals. Part of review activity.`,
		},
		{
			key: "comments",
			primaryReviewSignal: true,
			icon: CommentIcon,
			colorClass: "text-provider-muted-foreground",
			label: "Review comments",
			tooltip: "Review comments",
			ariaLabel: (count: number) => `${count} comment reviews. Part of review activity.`,
		},
		{
			key: "codeComments",
			primaryReviewSignal: true,
			icon: CommentDiscussionIcon,
			colorClass: "text-provider-muted-foreground",
			label: "Inline comments",
			tooltip: `Inline comments on someone else's ${pullRequests}. Part of review activity.`,
			ariaLabel: (count: number) =>
				`${count} inline feedback comments on ${pullRequests} authored by someone else. Part of review activity.`,
		},
		{
			key: "ownReplies",
			primaryReviewSignal: false,
			icon: MessageSquareReply,
			colorClass: "text-provider-muted-foreground",
			label: `Replies on your own ${pullRequests}`,
			tooltip: `Replies on your own ${pullRequests}. Shown for context.`,
			ariaLabel: (count: number) =>
				`${count} replies on your own ${pullRequests} and inline threads. Shown for context.`,
		},
		{
			key: "openPullRequests",
			primaryReviewSignal: false,
			icon: open.icon,
			colorClass: open.colorClass,
			label: `Open ${pullRequests}`,
			tooltip: `Your open ${pullRequests}. Shown for context.`,
			ariaLabel: (count: number) =>
				`${count} open ${pullRequests} you authored. Shown for context.`,
		},
		{
			key: "mergedPullRequests",
			primaryReviewSignal: false,
			icon: merged.icon,
			colorClass: merged.colorClass,
			label: `Merged ${pullRequests}`,
			tooltip: `Your merged ${pullRequests}. Shown for context.`,
			ariaLabel: (count: number) =>
				`${count} merged ${pullRequests} you authored. Shown for context.`,
		},
		{
			key: "closedPullRequests",
			primaryReviewSignal: false,
			icon: closed.icon,
			colorClass: closed.colorClass,
			label: `Closed ${pullRequests}`,
			tooltip: `Your closed ${pullRequests}. Shown for context.`,
			ariaLabel: (count: number) =>
				`${count} closed ${pullRequests} you authored. Shown for context.`,
		},
		{
			key: "openedIssues",
			primaryReviewSignal: false,
			icon: IssueOpenedIcon,
			colorClass: "text-provider-open-foreground",
			label: "Opened issues",
			tooltip: "Issues you opened. Shown for context.",
			ariaLabel: (count: number) => `${count} opened issues. Shown for context.`,
		},
		{
			key: "closedIssues",
			primaryReviewSignal: false,
			icon: IssueClosedIcon,
			colorClass: "text-provider-done-foreground",
			label: "Closed issues",
			tooltip: "Issues you closed. Shown for context.",
			ariaLabel: (count: number) => `${count} closed issues. Shown for context.`,
		},
	] as const;
}

const RESOLVED = {
	GITHUB: buildActivityBadgeMetadata("GITHUB"),
	GITLAB: buildActivityBadgeMetadata("GITLAB"),
} as const;

export type ActivityBadgeKey = (typeof RESOLVED)["GITHUB"][number]["key"];

export function getActivityBadgeMetadata(
	providerType: ProviderType,
): readonly ActivityBadgeMetadata[] {
	return RESOLVED[providerType];
}
