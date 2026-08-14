import {
	ArchiveIcon,
	BookOpenCheckIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleDotIcon,
	EyeIcon,
	FilePenLineIcon,
	GitCommitHorizontalIcon,
	GitMergeIcon,
	GitPullRequestArrowIcon,
	GitPullRequestClosedIcon,
	type LucideIcon,
	MessageSquareTextIcon,
	MessagesSquareIcon,
	TagIcon,
} from "lucide-react";
import type { PracticeSignalOption } from "@/api/types.gen";

/**
 * Where a moment sits in the life of one piece of work.
 *
 * Three bands rather than one line, because the moments a work type offers are not a queue. A pull
 * request is opened once, then churns — pushes and reviews land over and over — and then ends one of
 * two ways. Drawing that as a single arrow would claim an order between "New commits pushed" and
 * "Review submitted" that does not exist, and would put "Merged" and "Closed without merging" in
 * sequence when they are alternatives.
 */
export type MomentPhase = "start" | "during" | "end";

export const PHASE_LABEL: Record<MomentPhase, string> = {
	start: "Starts",
	during: "Along the way",
	end: "Ends",
};

/**
 * What this build knows about one moment beyond the words the server already sends.
 *
 * Deliberately no `label`: `PracticeSignalOption.displayName` is authoritative and a second copy here
 * would be a second thing to keep true. This registry adds only what the wire cannot carry — the
 * glyph, the band, and whether binding it means reviewing once or reviewing again and again.
 */
export interface MomentDef {
	/** Distinct within a work type, so the strip reads in greyscale (WCAG 2.2 SC 1.4.1). */
	icon: LucideIcon;
	phase: MomentPhase;
	/**
	 * True where the moment happens repeatedly in one piece of work. Binding one of these is a
	 * decision about volume — a practice on "New commits pushed" reviews every push — so the strip
	 * says so on the node rather than letting an operator find out from the review count.
	 */
	repeats: boolean;
}

/**
 * Keyed on the signal ids the server declares, which are an open vocabulary (`SignalName` reaches the
 * client as a bare `string`). A `Partial` map and a fallback are therefore the honest shape: a domain
 * that adds a moment renders as a neutral node under "Along the way" instead of vanishing from the
 * strip, and nothing here has to be edited for that to be true.
 */
const MOMENTS: Record<string, MomentDef> = {
	// scm.pull_request — opened, ready, synchronized, reviewed, merged, closed
	"scm.pull_request.opened": { icon: GitPullRequestArrowIcon, phase: "start", repeats: false },
	"scm.pull_request.ready": { icon: EyeIcon, phase: "during", repeats: false },
	"scm.pull_request.synchronized": {
		icon: GitCommitHorizontalIcon,
		phase: "during",
		repeats: true,
	},
	"scm.pull_request.reviewed": { icon: MessageSquareTextIcon, phase: "during", repeats: true },
	"scm.pull_request.merged": { icon: GitMergeIcon, phase: "end", repeats: false },
	"scm.pull_request.closed": { icon: GitPullRequestClosedIcon, phase: "end", repeats: false },

	// scm.issue — opened, labeled, closed
	"scm.issue.opened": { icon: CircleDotIcon, phase: "start", repeats: false },
	"scm.issue.labeled": { icon: TagIcon, phase: "during", repeats: true },
	"scm.issue.closed": { icon: CircleCheckIcon, phase: "end", repeats: false },

	// chat.conversation_thread — one moment, and only one
	"chat.conversation_thread.settled": { icon: MessagesSquareIcon, phase: "end", repeats: false },

	// docs.document — published, updated, archived
	"docs.document.published": { icon: BookOpenCheckIcon, phase: "start", repeats: false },
	"docs.document.updated": { icon: FilePenLineIcon, phase: "during", repeats: true },
	"docs.document.archived": { icon: ArchiveIcon, phase: "end", repeats: false },
};

const UNKNOWN_MOMENT: MomentDef = { icon: CircleDashedIcon, phase: "during", repeats: false };

export function momentDef(signal: string): MomentDef {
	return MOMENTS[signal] ?? UNKNOWN_MOMENT;
}

/**
 * A review somebody asked for by hand, which is not a point in the life of the work at all.
 *
 * <p>It is a moment the strip must not offer, and the reason is not aesthetic. On a manual request the
 * server matches <em>any</em> binding of the artifact's kind and ignores the draft filter, rather than
 * matching the signal — so a practice already runs on a hand-asked review whether or not this moment
 * is ticked, and ticking it changes nothing. It is also the one option that can consume a whole
 * occasion: a binding holding only this signal looks configured and never fires on its own.
 *
 * <p>Recognised by the id's last segment because the wire does not carry the flag the server decides
 * this with. A future domain that names its manual signal something else degrades to what this screen
 * did before — the moment appears as an ordinary node — rather than breaking.
 */
export function isManualRequestSignal(signal: string): boolean {
	return signal.endsWith(".manual_review");
}

export function manualRequestSignal(
	signals: readonly PracticeSignalOption[],
): PracticeSignalOption | undefined {
	return signals.find((option) => isManualRequestSignal(option.signal));
}

/** The moments that are points in the life of the work, which is every one but the hand-asked review. */
export function lifecycleSignals(signals: readonly PracticeSignalOption[]): PracticeSignalOption[] {
	return signals.filter((option) => !isManualRequestSignal(option.signal));
}

export interface MomentBand {
	phase: MomentPhase;
	moments: PracticeSignalOption[];
}

const PHASE_ORDER: MomentPhase[] = ["start", "during", "end"];

/**
 * The work type's moments grouped into bands, in band order, keeping the server's order within each.
 *
 * Empty bands are dropped rather than rendered blank, which is what lets one strip serve a pull
 * request's six moments and a conversation's single one without a special case at the call site.
 */
export function momentBands(signals: readonly PracticeSignalOption[]): MomentBand[] {
	return PHASE_ORDER.map((phase) => ({
		phase,
		moments: signals.filter((option) => momentDef(option.signal).phase === phase),
	})).filter((band) => band.moments.length > 0);
}
