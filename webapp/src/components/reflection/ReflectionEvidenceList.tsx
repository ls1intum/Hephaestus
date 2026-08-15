import { Link } from "@tanstack/react-router";
import type { ReflectionEvidence } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { artifactKindIcon, artifactKindLabel } from "@/lib/artifact-kinds";

export interface ReflectionEvidenceListProps {
	workspaceSlug: string;
	/** Newest first, as the endpoint sends them: the list has to read as "this is still happening". */
	evidence: ReflectionEvidence[];
	/** Owned by the message, so the heading below is tied to the section without guessing an id. */
	headingId: string;
}

/**
 * What a pattern claim rests on: the pieces of work it was seen in, and nothing else.
 *
 * <p>The unit of proof at this level is recurrence, so this is a set of work rather than a quoted
 * line. The line already appeared on the pull request; repeating it here would turn the page into a
 * second copy of that comment instead of the thing the comment cannot say.
 *
 * <p>The heading counts the list under it rather than reporting {@code occurrenceCount}. They agree
 * by contract, and a label read off the array cannot survive a page where they stop agreeing —
 * "seen on 4" above three rows is the reading that would make a reader distrust the whole surface.
 * It is deliberately a sentence about evidence and never a figure on its own: a number standing
 * alone on this page would be read as a score, which this page is not.
 */
export function ReflectionEvidenceList({
	workspaceSlug,
	evidence,
	headingId,
}: ReflectionEvidenceListProps) {
	return (
		// A `div` and a labelled list rather than a `section`: a named `section` is a `region`
		// landmark, and a page with three patterns on it would then carry three landmarks whose names
		// are counts — two of which read identically the moment two patterns have the same number of
		// occurrences, which is the duplicate axe's `landmark-unique` is about. The heading still names
		// the list, through the list itself.
		<div className="min-w-0">
			<h4 id={headingId} className="text-xs font-semibold uppercase text-muted-foreground">
				Seen on {evidence.length === 1 ? "1 piece" : `${evidence.length} pieces`} of your work
			</h4>
			<ul aria-labelledby={headingId} className="mt-2 space-y-1.5">
				{evidence.map((occurrence) => (
					<ReflectionEvidenceRow
						key={`${occurrence.artifactKind}:${occurrence.artifactId}:${String(occurrence.observedAt)}`}
						workspaceSlug={workspaceSlug}
						occurrence={occurrence}
					/>
				))}
			</ul>
		</div>
	);
}

interface ReflectionEvidenceRowProps {
	workspaceSlug: string;
	occurrence: ReflectionEvidence;
}

function ReflectionEvidenceRow({ workspaceSlug, occurrence }: ReflectionEvidenceRowProps) {
	const KindIcon = artifactKindIcon(occurrence.artifactKind);
	const kind = artifactKindLabel(occurrence.artifactKind);
	// What the review recorded on that work, when it recorded a title. The alternative is the raw
	// artifact id, which is a database key and not a number any developer has ever seen on a pull
	// request — printing it as "#1423" would name a different piece of work than the one it opens.
	const recorded = occurrence.title?.trim();

	return (
		<li className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-0.5 text-sm">
			<Link
				to="/w/$workspaceSlug/reviews/$artifactKind/$artifactId"
				params={{
					workspaceSlug,
					artifactKind: occurrence.artifactKind,
					artifactId: String(occurrence.artifactId),
				}}
				className="inline-flex min-w-0 items-baseline gap-1.5 underline underline-offset-4 hover:no-underline"
			>
				<KindIcon className="size-3.5 shrink-0 translate-y-0.5 text-muted-foreground" aria-hidden />
				{/* The kind is on the icon, which carries no meaning to a screen reader and none at all
				    without colour (SC 1.4.1); saying it here puts it in the link's accessible name, and
				    keeps the visible words a suffix of that name for speech control (SC 2.5.3). Left off
				    when it is already the visible text, so the name cannot say the kind twice. */}
				{recorded && <span className="sr-only">{kind}: </span>}
				<span className="min-w-0 break-words">{recorded || kind}</span>
			</Link>
			<span className="text-muted-foreground">
				<RelativeTime value={occurrence.observedAt} className="text-sm" />
			</span>
		</li>
	);
}
