import type { ReflectionFeedback } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
import { ReflectionEvidenceList } from "./ReflectionEvidenceList";

export interface ReflectionMessageProps {
	workspaceSlug: string;
	feedback: ReflectionFeedback;
}

/**
 * One process-level message: a habit that recurs across this developer's work, what it rests on,
 * and — as the last thing the composed body says — one thing to try next time.
 *
 * <p>Three things this deliberately does not render, each of which would change what the page is.
 *
 * <p>No delivery badge. Which state the ledger holds the message in is an operator's question; the
 * developer reading it is the delivery, so a badge here would report the reader's own arrival back
 * at them. `readAt` is skipped for the same reason and one more: opening the page is what sets it,
 * so any "new to you" marking is true for exactly one render and disappears under the reader on the
 * next refetch.
 *
 * <p>No count on its own, anywhere. The occurrences are named as the work they happened on, which is
 * evidence for a claim about a way of working; the same number printed as a figure is a score, and
 * this page is not a scoreboard.
 *
 * <p>No praise. Feedback aimed at the person rather than the work is the least effective kind there
 * is, so the surface offers nowhere to put it — there is no space for a verdict on the developer,
 * only for the pattern, the work, and the next step.
 */
export function ReflectionMessage({ workspaceSlug, feedback }: ReflectionMessageProps) {
	const headingId = `reflection-${feedback.id}`;
	const evidenceHeadingId = `${headingId}-evidence`;
	const body = feedback.body.trim();
	// The composer names the habit; the practice name is the fallback the server already falls back
	// to, and repeating it is better than a heading-shaped gap above a message.
	const headline = feedback.headline.trim() || feedback.practiceName;
	const framing = [
		{ term: "Why this matters", detail: feedback.whyItMatters?.trim() },
		{ term: "What good looks like", detail: feedback.whatGoodLooksLike?.trim() },
	].filter((entry) => Boolean(entry.detail));

	return (
		<article aria-labelledby={headingId} className="min-w-0 space-y-3 p-4 sm:p-6">
			<header className="min-w-0 space-y-1">
				<h3 id={headingId} className="min-w-0 break-words text-base font-semibold">
					{headline}
				</h3>
				<p className="min-w-0 break-words text-sm text-muted-foreground">
					About the practice <span className="text-foreground">{feedback.practiceName}</span>
					{feedback.areaName ? <>, in {feedback.areaName}</> : null}. Prepared{" "}
					<RelativeTime value={feedback.preparedAt} className="text-sm" />.
				</p>
			</header>

			{body ? (
				<div className={UNTRUSTED_MARKDOWN_PROSE}>
					<UntrustedMarkdown>{body}</UntrustedMarkdown>
				</div>
			) : (
				// A unit whose text did not survive composition. Saying so beats a headline floating
				// above its evidence with nothing between them.
				<p className="text-sm text-muted-foreground">
					No feedback text was composed for this pattern. The work it was seen on is below.
				</p>
			)}

			{framing.length > 0 && (
				// A rail rather than a second box: this is the practice's own words, quoted beside the
				// message, and boxing it would make it look like a second piece of feedback.
				<dl className="min-w-0 space-y-2 border-l pl-4 text-sm">
					{framing.map((entry) => (
						<div key={entry.term} className="min-w-0 space-y-0.5">
							<dt className="font-medium">{entry.term}</dt>
							<dd className="min-w-0 break-words text-muted-foreground">{entry.detail}</dd>
						</div>
					))}
				</dl>
			)}

			{feedback.evidence.length > 0 && (
				<ReflectionEvidenceList
					workspaceSlug={workspaceSlug}
					evidence={feedback.evidence}
					headingId={evidenceHeadingId}
				/>
			)}
		</article>
	);
}
