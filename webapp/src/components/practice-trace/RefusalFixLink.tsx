import { Link } from "@tanstack/react-router";

import { DEFAULT_REVIEW_SECTION } from "@/components/admin/practices/review/review-sections";

import { REFUSAL_FIXES, type RefusalReason } from "./trace-format";

export interface RefusalFixLinkProps {
	workspaceSlug: string;
	reason: RefusalReason;
	className?: string;
}

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
