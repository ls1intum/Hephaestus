import { cn } from "cn";
import { OUTCOME_DEFS, type Outcome } from "@/components/practice-vocabulary/outcome-defs";
import { resultSummary } from "./result-words";

import {
	countOf,
	type LineageFeedback,
	type LineageResult,
	type NumberedById,
	toneChip,
} from "./lineage-chain";

/**
 * How a result's chip looks: its verdict's tone, hollow where the practice does not apply, a ring
 * on the record the trace was opened for. The legend draws its samples with it.
 */
export function resultChipClass({
	outcome,
	ringed = false,
	size = "md",
	className,
}: {
	outcome: Outcome;
	ringed?: boolean;
	size?: "sm" | "md";
	className?: string;
}): string {
	return cn(
		"inline-flex shrink-0 items-center justify-center rounded-full border font-semibold tabular-nums",
		size === "md" ? "size-[1.125rem] text-2xs" : "size-4 text-2xs leading-none",
		toneChip(OUTCOME_DEFS[outcome].badgeVariant),
		outcome === "DOES_NOT_APPLY" && "bg-background",
		ringed && "ring-2 ring-foreground ring-offset-1 ring-offset-background",
		className,
	);
}

/**
 * One result as a small numbered chip in its verdict's tone. Does not apply is hollow, so it does
 * not read as could not determine, which shares its grey. The level's traced result is ringed. The
 * words are for a screen reader only, since the number alone says nothing there.
 */
export function LineageResultChip({
	result,
	number,
	size = "md",
	ringed,
	withSummary = false,
	className,
	connector,
}: {
	result: LineageResult;
	number: number;
	size?: "sm" | "md";
	/** The record the trace was opened for. */
	ringed: boolean;
	/** Says the result's summary to a screen reader too, where nothing beside the chip does. */
	withSummary?: boolean;
	className?: string;
	/** Where the details' connectors end: `source` on the result, `use:<feedback id>` on a piece. */
	connector?: string;
}) {
	const outcome = OUTCOME_DEFS[result.outcome];
	const words = [
		`Result ${number}, ${outcome.label.toLowerCase()}`,
		withSummary ? `: ${resultSummary({ summary: result.summary ?? undefined })}` : "",
	].join("");
	return (
		<span
			data-result-chip={number}
			data-result-id={result.id}
			data-connector={connector}
			data-ringed={ringed}
			className={resultChipClass({
				outcome: result.outcome,
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
 * "From 1 3": the chips of the results a piece of feedback was composed from. A result the story
 * does not number has no chip, so it is counted: as one of another review where the piece came
 * from one, otherwise as one the story leaves out.
 */
export function LineageSourceChips({
	feedback,
	byId,
	elsewhere,
	connectors = false,
	fromClassName,
}: {
	feedback: LineageFeedback;
	byId: NumberedById;
	/** Marks each chip as the end of a connector from its result. */
	connectors?: boolean;
	/** The review the piece was composed in, where it is not the chain's own. */
	elsewhere?: "an earlier review" | "a later review";
	/** The "From" word, which a table whose column is headed "From" hides from sight. */
	fromClassName?: string;
}) {
	if (feedback.basis.length === 0) {
		return <span>From no recorded result</span>;
	}
	const known = feedback.basis
		.map((id) => byId.get(id))
		.filter((entry) => entry !== undefined)
		.sort((a, b) => a.number - b.number);
	const unknown = feedback.basis.length - known.length;
	const counted = known.length > 0 ? `+ ${unknown}` : countOf(unknown, "result", "results");
	const rest = `${counted} ${elsewhere === undefined ? "not listed" : `of ${elsewhere}`}`;
	return (
		<span className="flex flex-wrap items-center gap-1.5">
			<span className={fromClassName}>From</span>
			{known.map(({ result, number }) => (
				<LineageResultChip
					key={result.id}
					result={result}
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
