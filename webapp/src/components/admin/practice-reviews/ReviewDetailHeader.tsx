import { Link } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { RelativeTime } from "@/components/common/RelativeTime";

export interface ReviewDetailHeaderProps {
	/** Status badges. Above the title, because they say what kind of thing the reader is about to read. */
	chips?: ReactNode;
	title: ReactNode;
	/** Where this came from and when — see {@link ReviewProvenanceLine}. */
	provenance?: ReactNode;
	/** Controls that act on the whole record. Only the review detail has any. */
	actions?: ReactNode;
}

/**
 * The head of every practice-review detail screen, in one shape.
 *
 * <p>The four detail screens each invented their own: one put the work above the title, one below,
 * one led with a grey "Reviewed work" eyebrow that repeated its own breadcrumb, and every one of
 * them buried the link to the review that produced it at the bottom of a Technical details accordion.
 * A reader arriving from a list had to re-find the same three facts in a different place each time.
 */
export function ReviewDetailHeader({ chips, title, provenance, actions }: ReviewDetailHeaderProps) {
	return (
		<header className="space-y-3">
			<div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
				<div className="min-w-0 space-y-2">
					{chips && <div className="flex flex-wrap items-center gap-2">{chips}</div>}
					<h2 className="break-words text-2xl font-semibold tracking-tight">{title}</h2>
					{provenance}
				</div>
				{actions}
			</div>
		</header>
	);
}

export interface ReviewProvenanceLineProps {
	workspaceSlug: string;
	agentJobId: string;
	/** How this record came about: "Observed", "Composed". A verb, so the line reads as a sentence. */
	verb: string;
	at: Date;
}

/**
 * One line saying which review produced this, and when — with the review one click away.
 *
 * <p>This is the fact that was hardest to reach and easiest to state. Both detail screens carried a
 * link to their parent review as a raw UUID inside a collapsed "Technical details" accordion: an
 * operator investigating a piece of feedback had to open a drawer labelled as being for technicians
 * in order to answer "which run made this?". The UUID itself is gone with it — it is in the address
 * bar of the page the link goes to, and it was never something to read.
 */
export function ReviewProvenanceLine({
	workspaceSlug,
	agentJobId,
	verb,
	at,
}: ReviewProvenanceLineProps) {
	return (
		<p className="text-sm text-muted-foreground">
			{verb}{" "}
			<Link
				to="/w/$workspaceSlug/admin/practices/reviews/$jobId"
				params={{ workspaceSlug, jobId: agentJobId }}
				className="font-medium text-foreground underline underline-offset-4"
			>
				in a review
			</Link>{" "}
			<RelativeTime value={at} />
		</p>
	);
}

/**
 * The two-to-four facts that place a record, side by side and each under its own label.
 *
 * <p>A `<dl>` rather than prose: these are labelled values, a screen reader should announce them as
 * pairs, and the practice, the developer and the work were previously scattered across a header, a
 * two-column grid and an accordion in an order that differed per screen.
 */
export function ReviewFactGrid({ children }: { children: ReactNode }) {
	return (
		<dl className="grid gap-4 rounded-lg border p-4 sm:grid-cols-2 lg:grid-cols-3">{children}</dl>
	);
}

export function ReviewFact({ label, children }: { label: string; children: ReactNode }) {
	return (
		<div className="min-w-0 space-y-1">
			<dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
			<dd className="min-w-0 text-sm">{children}</dd>
		</div>
	);
}
