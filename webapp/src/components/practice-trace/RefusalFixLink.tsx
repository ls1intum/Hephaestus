import { Link } from "@tanstack/react-router";
import { REFUSAL_FIXES, type SignalStateReason } from "./trace-format";

export interface RefusalFixLinkProps {
	workspaceSlug: string;
	reason: SignalStateReason;
	/**
	 * Whether this reader may open workspace administration. Passed in rather than read here: every
	 * member of a workspace can open a trace, and a link into `/admin` would bounce all of them but
	 * the admins off the guard and back to the workspace home — a control whose only outcome, for
	 * most of the people offered it, is losing the page they were reading.
	 */
	canAdminister: boolean;
	className?: string;
}

/**
 * The way out of a refusal, where one exists.
 *
 * <p>A product that says silence always has an answer should hand over the answer, not name it. The
 * model-unbound reason used to end "choose one under AI models" — a page title in a string, on a
 * screen that already renders links.
 *
 * <p>Renders nothing at all rather than a disabled or explanatory stand-in when there is no fix or no
 * standing to apply it. A greyed-out link still costs a reader the effort of finding out it is
 * greyed out, and the sentence beside it already says everything a member can act on.
 */
export function RefusalFixLink({
	workspaceSlug,
	reason,
	canAdminister,
	className,
}: RefusalFixLinkProps) {
	const fix = REFUSAL_FIXES[reason];
	if (!fix || !canAdminister) return null;
	return (
		<Link
			to={fix.to}
			params={{ workspaceSlug }}
			className={className ?? "font-medium underline underline-offset-4 hover:no-underline"}
		>
			{fix.label}
		</Link>
	);
}
