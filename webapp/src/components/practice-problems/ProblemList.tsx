import { cn } from "cn";
import type { ReactNode } from "react";

import type { Problem, ProblemSummary } from "@/api/types.gen";
import { StatusBadge } from "@/components/common/StatusBadge";
import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
import type { DetailStackEntry } from "@/components/layout/detail-drawer/detail-stack";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { hasText } from "@/lib/text";

/** A problem in full, or as a list read sends it: its severity and summary only. */
export type ProblemEntry = Problem | ProblemSummary;

export interface ProblemCardProps {
	problem: ProblemEntry;
	/**
	 * The passages that show the problem, drawn for the reader: the operator's evidence view or the
	 * developer's. Each surface keeps its own access rules, so the card only places what it is given.
	 */
	evidence?: ReactNode;
	/** The operator ranks problems by severity; the developer's own page leaves the label out. */
	severity?: "shown" | "omitted";
	/**
	 * The level that shows this problem on its own. It takes the place of the level the card is on,
	 * which is a step in history, so Back returns there.
	 */
	open?: DetailStackEntry;
}

/** One problem under a Not met result: its severity, summary, explanation, evidence and next step. */
export function ProblemCard({ problem, evidence, severity = "shown", open }: ProblemCardProps) {
	const full = "citations" in problem ? problem : undefined;
	const summary = problem.summary.trim();
	return (
		<div className="flex min-w-0 flex-col gap-2 rounded-xl border bg-background p-3">
			<div className="flex flex-wrap items-start gap-2">
				{severity === "shown" && <StatusBadge def={SEVERITY_DEFS[problem.severity]} />}
				<p className="min-w-0 flex-1 basis-48 text-sm font-medium text-pretty break-words">
					{open === undefined ? (
						summary
					) : (
						<DetailStackLink entry={open} single className="underline-offset-4 hover:underline">
							{summary}
						</DetailStackLink>
					)}
				</p>
			</div>
			{hasText(full?.explanation) && <ProblemProse>{full.explanation}</ProblemProse>}
			{evidence}
			{hasText(full?.nextStep) && (
				<div className="space-y-1 text-sm">
					<p className="font-medium">Next step</p>
					<ProblemProse>{full.nextStep}</ProblemProse>
				</div>
			)}
		</div>
	);
}

// The review writes a problem's explanation and next step in Markdown.
function ProblemProse({ children }: { children: string }) {
	return (
		<div className={cn(UNTRUSTED_MARKDOWN_PROSE, "text-sm text-pretty")}>
			<UntrustedMarkdown>{children}</UntrustedMarkdown>
		</div>
	);
}

export interface ProblemListProps {
	problems: readonly ProblemEntry[];
	/** The evidence of one problem in full; a summary has none. */
	evidence?: (problem: Problem) => ReactNode;
	severity?: ProblemCardProps["severity"];
	/** The level of one problem, where the surface can open it. */
	open?: (problemId: string) => DetailStackEntry;
}

function positionOf(problem: ProblemEntry): number {
	return "position" in problem ? problem.position : 0;
}

/** A result's problems in the order the review recorded them. */
export function ProblemList({ problems, evidence, severity, open }: ProblemListProps) {
	// A summary carries no position; the list read sends summaries in the recorded order already.
	const ordered = [...problems].sort((a, b) => positionOf(a) - positionOf(b));
	return (
		<ol aria-label="Problems" className="flex min-w-0 flex-col gap-2">
			{ordered.map((problem) => (
				<li key={problem.id} className="min-w-0">
					<ProblemCard
						problem={problem}
						evidence={"citations" in problem ? evidence?.(problem) : undefined}
						severity={severity}
						open={open?.(problem.id)}
					/>
				</li>
			))}
		</ol>
	);
}
