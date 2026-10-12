import { cn } from "cn";
import type { ReviewLineageFeedback, ReviewLineageObservation } from "@/api/types.gen";
import { OUTCOME_DEFS, type Outcome } from "@/components/practice-vocabulary/outcome-defs";

import { countOf, type NumberedById, toneChip } from "./lineage-chain";

/**
 * How an observation's chip looks: its verdict's tone, hollow where not applicable, a dashed edge
 * and a struck number where marked incorrect, a ring on the record the trace was opened for. The
 * legend draws its samples with it.
 */
export function observationChipClass({
	outcome,
	invalidated = false,
	ringed = false,
	size = "md",
	className,
}: {
	outcome: Outcome;
	invalidated?: boolean;
	ringed?: boolean;
	size?: "sm" | "md";
	className?: string;
}): string {
	return cn(
		"inline-flex shrink-0 items-center justify-center rounded-full border font-semibold tabular-nums",
		size === "md" ? "size-[1.125rem] text-2xs" : "size-4 text-2xs leading-none",
		toneChip(OUTCOME_DEFS[outcome].badgeVariant),
		outcome === "NOT_APPLICABLE" && "bg-background",
		invalidated && "border-dashed line-through",
		ringed && "ring-2 ring-foreground ring-offset-1 ring-offset-background",
		className,
	);
}

/**
 * One observation as a small numbered chip in its verdict's tone. Not applicable is hollow, so it
 * does not read as undetermined, which shares its grey. The level's traced observation is ringed;
 * one marked incorrect has a dashed edge and a struck number. The words are for a screen reader
 * only, since the number alone says nothing there.
 */
export function LineageObservationChip({
	observation,
	number,
	size = "md",
	ringed,
	withSummary = false,
	className,
	connector,
}: {
	observation: ReviewLineageObservation;
	number: number;
	size?: "sm" | "md";
	/** The record the trace was opened for. */
	ringed: boolean;
	/** Says the observation's summary to a screen reader too, where nothing beside the chip does. */
	withSummary?: boolean;
	className?: string;
	/** Where the details' connectors end: `source` on the observation, `use:<feedback id>` on a piece. */
	connector?: string;
}) {
	const outcome = OUTCOME_DEFS[observation.outcome];
	const words = [
		`Observation ${number}, ${outcome.label.toLowerCase()}`,
		observation.invalidated ? ", marked incorrect" : "",
		withSummary ? `: ${observation.summary}` : "",
	].join("");
	return (
		<span
			data-observation-chip={number}
			data-observation-id={observation.id}
			data-connector={connector}
			data-ringed={ringed}
			className={observationChipClass({
				outcome: observation.outcome,
				invalidated: observation.invalidated,
				ringed,
				size,
				className,
			})}
		>
			<span aria-hidden>{number}</span>
			<span className="sr-only">{words}</span>
		</span>
	);
}

/**
 * "From 1 3": the chips of the observations a piece of feedback was composed from. An observation
 * the chain does not list has no chip, so it is counted: as one of another review where the piece
 * came from one, otherwise as one the list leaves out.
 */
export function LineageSourceChips({
	feedback,
	byId,
	elsewhere,
	connectors = false,
	fromClassName,
}: {
	feedback: ReviewLineageFeedback;
	byId: NumberedById;
	/** Marks each chip as the end of a connector from its observation. */
	connectors?: boolean;
	/** The review the piece was composed in, where it is not the chain's own. */
	elsewhere?: "an earlier review" | "a later review";
	/** The "From" word, which a table whose column is headed "From" hides from sight. */
	fromClassName?: string;
}) {
	if (feedback.observationIds.length === 0) {
		return <span>From no recorded observation</span>;
	}
	const known = feedback.observationIds
		.map((id) => byId.get(id))
		.filter((entry) => entry !== undefined)
		.sort((a, b) => a.number - b.number);
	const unknown = feedback.observationIds.length - known.length;
	const counted =
		known.length > 0 ? `+ ${unknown}` : countOf(unknown, "observation", "observations");
	const rest = `${counted} ${elsewhere === undefined ? "not listed" : `of ${elsewhere}`}`;
	return (
		<span className="flex flex-wrap items-center gap-1.5">
			<span className={fromClassName}>From</span>
			{known.map(({ observation, number }) => (
				<LineageObservationChip
					key={observation.id}
					observation={observation}
					number={number}
					size="sm"
					ringed={false}
					connector={connectors ? `use:${feedback.id}` : undefined}
				/>
			))}
			{unknown > 0 && <span>{rest}</span>}
		</span>
	);
}
