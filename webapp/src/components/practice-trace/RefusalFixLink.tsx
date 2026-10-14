import { Link } from "@tanstack/react-router";

import { DEFAULT_REVIEW_SECTION } from "@/components/admin/practices/review/review-sections";

import { REFUSAL_FIXES, type RefusalReason } from "./trace-format";

export interface RefusalFixLinkProps {
	workspaceSlug: string;
	reason: RefusalReason;
	className?: string;
}

/**
 * The way out of a refusal, where one exists. Renders nothing at all rather than a disabled or
 * explanatory stand-in when there is no fix: the sentence beside it already says everything a
 * member can act on. Every link leads into `/admin`, so only a route that knows the reader is an
 * admin renders it.
 */
export function RefusalFixLink({ workspaceSlug, reason, className }: RefusalFixLinkProps) {
	const fix = REFUSAL_FIXES[reason];
	if (!fix) {
		return null;
	}
	const linkClass = className ?? "font-medium underline underline-offset-4 hover:no-underline";

	if (fix.section) {
		return (
			<Link
				to="/w/$workspaceSlug/admin/practices/review"
				params={{ workspaceSlug }}
				search={{ section: fix.section === DEFAULT_REVIEW_SECTION ? undefined : fix.section }}
				className={linkClass}
			>
				{fix.label}
			</Link>
		);
	}
	return (
		<Link to={fix.to} params={{ workspaceSlug }} className={linkClass}>
			{fix.label}
		</Link>
	);
}
