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
	LinkIcon,
	type LucideIcon,
	MessageSquareTextIcon,
	MessagesSquareIcon,
} from "lucide-react";

import type { ItemEventOption, PracticeItemTypeOption } from "@/api/types.gen";

/**
 * Where an event sits in the life of one item.
 *
 * Bands rather than one ordered line, because an item's events are not a queue: those in the middle
 * repeat in no fixed order, and those at the end are alternatives to each other.
 */
export type EventPhase = "start" | "during" | "end";

export const PHASE_LABEL: Record<EventPhase, string> = {
	start: "Starts",
	during: "Along the way",
	end: "Ends",
};

/**
 * What this build knows about one event beyond what the wire carries. Deliberately no `label`:
 * `ItemEventOption.label` is authoritative, and a second copy would be a second truth.
 */
export interface EventDef {
	/** Distinct within an item kind, so the strip reads in greyscale (WCAG 2.2 SC 1.4.1). */
	icon: LucideIcon;
	phase: EventPhase;
	/**
	 * True where the event happens repeatedly on one item, which makes it a decision about volume
	 * the strip has to state up front.
	 */
	repeats: boolean;
}

/**
 * Event names are an open vocabulary — the client receives a bare `string` — so a domain may
 * declare an event this build has never met. Hence the fallback rather than a total map.
 */
const EVENTS: Record<string, EventDef> = {
	"scm.pull_request.opened": { icon: GitPullRequestArrowIcon, phase: "start", repeats: false },
	"scm.pull_request.ready": { icon: EyeIcon, phase: "during", repeats: false },
	"scm.pull_request.synchronized": {
		icon: GitCommitHorizontalIcon,
		phase: "during",
		repeats: true,
	},
	"scm.pull_request.edited": { icon: FilePenLineIcon, phase: "during", repeats: true },
	"scm.pull_request.linked_issue_updated": { icon: LinkIcon, phase: "during", repeats: true },
	"scm.pull_request.merged": { icon: GitMergeIcon, phase: "end", repeats: false },
	"scm.pull_request.closed": { icon: GitPullRequestClosedIcon, phase: "end", repeats: false },

	"scm.pull_request_review.submitted": {
		icon: MessageSquareTextIcon,
		phase: "start",
		repeats: false,
	},

	"scm.issue.opened": { icon: CircleDotIcon, phase: "start", repeats: false },
	"scm.issue.updated": { icon: FilePenLineIcon, phase: "during", repeats: true },
	"scm.issue.closed": { icon: CircleCheckIcon, phase: "end", repeats: false },

	"chat.thread.settled": { icon: MessagesSquareIcon, phase: "end", repeats: false },

	"docs.document.published": { icon: BookOpenCheckIcon, phase: "start", repeats: false },
	"docs.document.updated": { icon: FilePenLineIcon, phase: "during", repeats: true },
	"docs.document.archived": { icon: ArchiveIcon, phase: "end", repeats: false },
};

const UNKNOWN_EVENT: EventDef = { icon: CircleDashedIcon, phase: "during", repeats: false };

export function eventDef(name: string): EventDef {
	return EVENTS[name] ?? UNKNOWN_EVENT;
}

/** The name of an event the practice still names and this version can no longer offer or describe. */
export const WITHDRAWN_EVENT_LABEL = "An event this version no longer offers";

/**
 * Events a practice is already bound to that its item kind no longer offers. Drawn rather than
 * dropped — the form refuses to save while one is chosen, and an event nobody can see is an event
 * nobody can untick. A withdrawn event has no label on the wire, so it is named by
 * {@link WITHDRAWN_EVENT_LABEL}, never by its wire id.
 */
export function withdrawnEvents(
	itemType: PracticeItemTypeOption,
	selected: readonly string[],
): ItemEventOption[] {
	const offered = new Set(itemType.events.map((option) => option.name));
	return selected
		.filter((name) => !offered.has(name))
		.map((name) => ({
			name,
			label: WITHDRAWN_EVENT_LABEL,
			recommended: false,
			connected: false,
			raisedBy: [],
		}));
}

/** The label of a chosen event, or {@link WITHDRAWN_EVENT_LABEL} where the item kind lost it. */
export function eventLabel(name: string, events: readonly ItemEventOption[]): string {
	return events.find((option) => option.name === name)?.label ?? WITHDRAWN_EVENT_LABEL;
}

export interface EventBand {
	phase: EventPhase;
	events: ItemEventOption[];
}

const PHASE_ORDER: EventPhase[] = ["start", "during", "end"];

/**
 * The item kind's events grouped into bands, keeping the server's order within each. Empty bands
 * are dropped, so one strip serves every item kind without a special case at the call site.
 */
export function eventBands(events: readonly ItemEventOption[]): EventBand[] {
	return PHASE_ORDER.map((phase) => ({
		phase,
		events: events.filter((option) => eventDef(option.name).phase === phase),
	})).filter((band) => band.events.length > 0);
}
