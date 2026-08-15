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
 * Bands rather than one ordered line, because a work type's moments are not a queue: those in the
 * middle repeat in no fixed order, and those at the end are alternatives to each other.
 */
export type MomentPhase = "start" | "during" | "end";

export const PHASE_LABEL: Record<MomentPhase, string> = {
	start: "Starts",
	during: "Along the way",
	end: "Ends",
};

/**
 * What this build knows about one moment beyond what the wire carries. Deliberately no `label`:
 * `PracticeSignalOption.displayName` is authoritative, and a second copy would be a second truth.
 */
export interface MomentDef {
	/** Distinct within a work type, so the strip reads in greyscale (WCAG 2.2 SC 1.4.1). */
	icon: LucideIcon;
	phase: MomentPhase;
	/**
	 * True where the moment happens repeatedly in one piece of work, which makes binding it a
	 * decision about volume the strip has to state up front.
	 */
	repeats: boolean;
}

/**
 * Signal ids are an open vocabulary — `SignalName` reaches the client as a bare `string` — so a
 * domain may declare a moment this build has never met. Hence the fallback rather than a total map.
 */
const MOMENTS: Record<string, MomentDef> = {
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

	"scm.issue.opened": { icon: CircleDotIcon, phase: "start", repeats: false },
	"scm.issue.labeled": { icon: TagIcon, phase: "during", repeats: true },
	"scm.issue.closed": { icon: CircleCheckIcon, phase: "end", repeats: false },

	"chat.conversation_thread.settled": { icon: MessagesSquareIcon, phase: "end", repeats: false },

	"docs.document.published": { icon: BookOpenCheckIcon, phase: "start", repeats: false },
	"docs.document.updated": { icon: FilePenLineIcon, phase: "during", repeats: true },
	"docs.document.archived": { icon: ArchiveIcon, phase: "end", repeats: false },
};

const UNKNOWN_MOMENT: MomentDef = { icon: CircleDashedIcon, phase: "during", repeats: false };

export function momentDef(signal: string): MomentDef {
	return MOMENTS[signal] ?? UNKNOWN_MOMENT;
}

/**
 * A review somebody asked for by hand, which the strip must not offer as a moment: on a manual
 * request the server matches any binding of the artifact's kind and ignores the draft filter rather
 * than matching the signal, so ticking this changes nothing while a binding holding only it looks
 * configured and never fires on its own.
 *
 * Recognised by the id's last segment because the wire does not carry the flag the server decides
 * this with; a domain that names its manual signal otherwise degrades to an ordinary node.
 */
export function isManualRequestSignal(signal: string): boolean {
	return signal.endsWith(".manual_review");
}

export function manualRequestSignal(
	signals: readonly PracticeSignalOption[],
): PracticeSignalOption | undefined {
	return signals.find((option) => isManualRequestSignal(option.signal));
}

export function lifecycleSignals(signals: readonly PracticeSignalOption[]): PracticeSignalOption[] {
	return signals.filter((option) => !isManualRequestSignal(option.signal));
}

export interface MomentBand {
	phase: MomentPhase;
	moments: PracticeSignalOption[];
}

const PHASE_ORDER: MomentPhase[] = ["start", "during", "end"];

/**
 * The work type's moments grouped into bands, keeping the server's order within each. Empty bands
 * are dropped, so one strip serves every work type without a special case at the call site.
 */
export function momentBands(signals: readonly PracticeSignalOption[]): MomentBand[] {
	return PHASE_ORDER.map((phase) => ({
		phase,
		moments: signals.filter((option) => momentDef(option.signal).phase === phase),
	})).filter((band) => band.moments.length > 0);
}
