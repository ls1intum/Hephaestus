import { useId } from "react";

import { claimCurrentnessNote } from "@/components/practice-vocabulary/claim-currentness";
import { OUTCOME_DEFS, resultVerdict } from "@/components/practice-vocabulary/outcome-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";

import { cn } from "cn";
import { statusDefOr } from "~/components/common/status";
import { INDENT, ListSkeleton, LoadError, Truncated } from "~/components/report/report-parts";
import type { Loadable } from "~/components/report/report-summary";
import type { ResultPage, ResultRow } from "~/shared/review-context";

export interface ResultListProps {
	results: Loadable<ResultPage>;
	onRetry: () => void;
}

/**
 * Labels and icons come from the shared outcome registry; the problem count and the severity apply
 * only to Not met.
 */
function verdict(row: ResultRow) {
	let tone = "text-muted-foreground";
	if (row.outcome === "MET") {
		tone = "text-success";
	} else if (row.outcome === "NOT_MET") {
		tone = "text-destructive";
	}
	return {
		def: OUTCOME_DEFS[row.outcome],
		label: resultVerdict(row.outcome, row.problems.length),
		tone,
		severity:
			row.outcome === "NOT_MET" && row.severity !== undefined
				? statusDefOr(SEVERITY_DEFS, row.severity).label
				: undefined,
	};
}

function Result({ row }: { row: ResultRow }) {
	const { def, label, tone, severity } = verdict(row);
	const Icon = def.icon;
	const currentnessNote = claimCurrentnessNote(row.claimCurrentness);
	return (
		<li className={cn(INDENT, "flex min-w-0 items-start gap-2 py-1.5 text-sm")}>
			<Icon aria-hidden className={cn("mt-0.5 size-4 shrink-0", tone)} />
			<div className="flex min-w-0 flex-1 flex-col">
				<span className="flex flex-wrap items-baseline gap-x-2">
					<span className="font-medium break-words">{row.practiceName}</span>
					<span className="text-xs text-muted-foreground">
						{label}
						{severity === undefined ? null : ` · ${severity}`}
						{currentnessNote === undefined ? null : ` · ${currentnessNote}`}
					</span>
				</span>
				<span className="break-words text-muted-foreground">{row.summary}</span>
				{row.outcome === "NOT_MET" && row.problems.length > 0 ? (
					<ul
						aria-label="Problems"
						className="mt-1 list-disc space-y-0.5 pl-4 text-muted-foreground"
					>
						{row.problems.map((problem, index) => (
							// A list row carries no problem id; a problem's place in its result is stable.
							<li key={index} className="break-words">
								{problem.summary}
							</li>
						))}
					</ul>
				) : null}
			</div>
		</li>
	);
}

/**
 * The reader's own results on this work, as the review concluded them — each practice, its
 * outcome and one sentence, and under a result that is not met the one line of each problem. Why,
 * where each problem is and the evidence behind it are the web app's to show; here nothing opens
 * further.
 */
export function ResultList({ results, onRetry }: ResultListProps) {
	const heading = useId();
	let body;
	if (results.status === "loading") {
		body = <ListSkeleton />;
	} else if (results.status === "error") {
		body = <LoadError message={results.message} onRetry={onRetry} />;
	} else if (results.data.rows.length === 0) {
		body = (
			<p className={cn(INDENT, "py-1.5 text-sm text-muted-foreground")}>
				No results about your work here.
			</p>
		);
	} else {
		const { rows, total } = results.data;
		body = (
			<>
				<Truncated items={rows} render={(row) => <Result key={row.id} row={row} />} />
				{total > rows.length ? (
					<p className={cn(INDENT, "pb-1 text-xs text-muted-foreground")}>
						Showing the {rows.length} most severe of {total}.
					</p>
				) : null}
			</>
		);
	}
	return (
		<section aria-labelledby={heading} className="border-t border-border py-1">
			<h3
				id={heading}
				className={cn(INDENT, "pt-2 pb-0.5 text-xs font-semibold text-muted-foreground")}
			>
				Your results
			</h3>
			{body}
		</section>
	);
}
