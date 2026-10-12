import { Link, useParams } from "@tanstack/react-router";

import { cn } from "cn";
import type { Practice, ReviewPracticeGroup } from "@/api/types.gen";
import { PracticeDetailHoverCard } from "@/components/admin/practice-editor/PracticeDetailHoverCard";
import { InlineLink } from "@/components/common/InlineLink";
import { GroupPill } from "@/components/practice-vocabulary/GroupPill";

export interface ReviewPracticeLinkProps {
	practiceSlug: string;
	practiceName: string;
	group: ReviewPracticeGroup | undefined;
	practice?: Practice;
	className?: string;
}

/**
 * Opens the Observations list filtered to the practice rather than Practice setup: the reader is
 * asking how the practice behaves, and Practice setup is one press from the counts above that list
 * when the answer is "change it".
 */
export function ReviewPracticeLink({
	practiceSlug,
	practiceName,
	group,
	practice,
	className,
}: ReviewPracticeLinkProps) {
	const workspaceSlug = useParams({ strict: false, select: (params) => params.workspaceSlug });
	// `relative` lifts this above the stretched title link of `ReviewRow`, which otherwise covers the
	// whole row and would swallow the click.
	const link = (
		<InlineLink
			render={
				<Link
					to="/w/$workspaceSlug/admin/practices/reviews/observations"
					params={{ workspaceSlug: workspaceSlug ?? "" }}
					search={{ practiceSlug: [practiceSlug] }}
				/>
			}
			className={cn("relative inline-flex max-w-full min-w-0 items-center gap-1.5", className)}
		>
			<PracticeGroupMark group={group} />
			<span className="min-w-0 break-words">{practiceName}</span>
		</InlineLink>
	);

	return practice ? (
		<PracticeDetailHoverCard practice={practice}>{link}</PracticeDetailHoverCard>
	) : (
		link
	);
}

export interface ReviewPracticeNameProps {
	practiceName: string;
	group: ReviewPracticeGroup | undefined;
	className?: string;
}

/**
 * The practice by its group's mark and its name, as plain text. A row names its practice this way:
 * the row's title is its one link, and the record's level links the practice.
 */
export function ReviewPracticeName({ practiceName, group, className }: ReviewPracticeNameProps) {
	return (
		<span className={cn("inline-flex max-w-full min-w-0 items-center gap-1.5", className)}>
			<PracticeGroupMark group={group} />
			<span className="min-w-0 break-words">{practiceName}</span>
		</span>
	);
}

function PracticeGroupMark({ group }: { group: ReviewPracticeGroup | undefined }) {
	if (!group) {
		return null;
	}
	return (
		<GroupPill
			size="sm"
			slug={group.slug}
			name={group.name}
			icon={group.icon}
			color={group.color}
			srLabel
		/>
	);
}
