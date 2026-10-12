import { type ReactNode, useState } from "react";

import type { AgentJob, ReviewLineage, TracedArtifact } from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import {
	DISCOVERED_VIA_LABELS,
	SIGNAL_STATE_LABELS,
} from "@/components/practice-trace/trace-format";
import type { ResultOrigin } from "@/components/practice-vocabulary/result-origin-defs";
import { DrawerBody } from "@/components/ui/drawer";
import { hasText } from "@/lib/text";

import { LevelBodySkeleton } from "./LevelBodySkeleton";
import { Step } from "./LineageStep";
import type { RecordRef } from "./review-layers";
import type { TraceNode } from "./review-trace";
import { ReviewArtifactLabel } from "./ReviewArtifact";
import { ReviewLevelHeader } from "./ReviewLevelHeader";
import { ReviewLineageGraph } from "./ReviewLineageGraph";
import type { TraceDisplay } from "./trace-display";
import type { TraceFilter } from "./trace-filter";
import { TRACE_END_TONES, unreviewedVerdict } from "./trace-verdict";
import { TraceLegend } from "./TraceLegend";

/**
 * What a trace tells: the chain of the record, or, for work none of whose moments started a review,
 * the work and its latest moment, which is all there is.
 */
export type TraceChainState =
	| PanelState<{ lineage: ReviewLineage }>
	| { status: "unreviewed"; work: TracedArtifact }
	| { status: "none" };

export interface TraceLevelProps {
	nested?: boolean;
	path: LevelPath;
	/** What the trace stands on, in words: "Review of #1423 · 7 Oct, 14:02". */
	title: ReactNode;
	/** A line under the title, where the trace stands on something other than the row it came from. */
	description?: ReactNode;
	chain: TraceChainState;
	here: TraceNode | undefined;
	/** Opens a step's record over the trace, as the one level above it. */
	onOpen: (record: RecordRef) => void;
	/** What the trace shows on top of its steps: times, system info, siblings, connections. */
	display: TraceDisplay;
	onDisplayChange: (display: TraceDisplay) => void;
	filter: TraceFilter;
	onFilterChange: (filter: TraceFilter) => void;
	reviewOutcome?: AgentJob["reviewOutcome"];
	/**
	 * The review the trace tells, as its sheet reads it: its times, model, outcome and first read
	 * come from it, so the trace and the review's sheet say the same.
	 */
	job?: AgentJob;
	/** How the review came to run, from its row: live, by hand or backfilled. */
	origin?: ResultOrigin;
	/** Where the review stands among the reviews of its work, from its row. */
	position?: { index: number; total: number };
	/** The work's record, which the latest moment of work without a review opens. */
	workRecord?: RecordRef;
	/**
	 * Whether Practice reviews are on for the workspace now, so the trace of work without a review
	 * never says they are off while they are on.
	 */
	practicesEnabled?: boolean;
}

/**
 * The trace of one record, as a wide sheet over the tables: where it came from, its siblings, where
 * it stands and what became of it. A step opens its record's sheet over the
 * trace; that sheet links nowhere, so Escape closes it back to the trace, then the trace.
 */
export function TraceLevel({
	nested,
	path,
	title,
	description,
	chain,
	workRecord,
	...story
}: TraceLevelProps) {
	return (
		<>
			<ReviewLevelHeader
				nested={nested}
				path={path}
				kind="trace"
				loading={chain.status === "loading"}
				title={title}
				description={description}
			/>
			<DrawerBody className="pt-2">
				<TraceBody chain={chain} workRecord={workRecord} {...story} />
			</DrawerBody>
		</>
	);
}

function TraceBody({
	chain,
	workRecord,
	practicesEnabled,
	onOpen,
	...story
}: Omit<TraceLevelProps, "nested" | "path" | "title" | "description">) {
	switch (chain.status) {
		case "loading": {
			return <LevelBodySkeleton />;
		}
		case "error": {
			return (
				<QueryErrorAlert
					error={chain.error}
					title="We could not load this trace"
					onRetry={chain.onRetry}
				/>
			);
		}
		case "none": {
			return (
				<p className="text-sm text-muted-foreground">
					Nothing was recorded about this record, so there is no trace to show.
				</p>
			);
		}
		case "unreviewed": {
			return (
				<UnreviewedTrace
					work={chain.work}
					practicesEnabled={practicesEnabled}
					onOpen={workRecord === undefined ? undefined : () => onOpen(workRecord)}
				/>
			);
		}
		case "ready": {
			return <ReviewLineageGraph lineage={chain} onOpen={onOpen} {...story} />;
		}
	}
}

/**
 * Work none of whose moments started a review: its latest moment, and the end of the path, with
 * whether it still waits or why no review started. The moment opens the work's sheet, which has the
 * setting that would let it through.
 */
function UnreviewedTrace({
	work,
	practicesEnabled,
	onOpen,
}: {
	work: TracedArtifact;
	practicesEnabled: boolean | undefined;
	/** Opens the work's sheet; absent where the trace has no work to open, so the moment is text. */
	onOpen?: () => void;
}) {
	const [legendOpen, setLegendOpen] = useState(false);
	const moment = work.lastSignal;
	const verdict = unreviewedVerdict(moment, practicesEnabled);
	return (
		<div role="group" aria-label="How this came about" className="min-w-0 space-y-3">
			<p data-trace-verdict className="text-sm font-medium text-balance">
				{verdict.sentence}
			</p>
			<div className="flex justify-end">
				<TraceLegend open={legendOpen} onOpenChange={setLegendOpen} />
			</div>
			<ol aria-label="Steps, oldest first" className="text-xs">
				<li className="grid grid-cols-[0.6875rem_minmax(0,1fr)] gap-x-2.5 pb-1.5">
					<span aria-hidden />
					<span className="px-1.5 text-2xs font-semibold tracking-wide text-muted-foreground uppercase">
						Trigger
					</span>
				</li>
				<Step dot={{ tone: "outline" }} data-step="work">
					<span className="block px-1.5 py-1">
						<ReviewArtifactLabel reviewedWork={work.reviewedWork} className="font-medium" />
						{hasText(work.reviewedWork.title) && (
							<span className="block break-words text-muted-foreground">
								{work.reviewedWork.title}
							</span>
						)}
					</span>
				</Step>
				<Step dot={{ tone: "outline", hollow: true }} at={moment.occurredAt} data-step="moment">
					{onOpen === undefined ? (
						<span data-step-key="moment" className="block px-1.5 py-1">
							<span className="block font-medium">{moment.displayName}</span>
							<span className="block text-muted-foreground">
								{DISCOVERED_VIA_LABELS[moment.discoveredVia]} · {SIGNAL_STATE_LABELS[moment.state]}
							</span>
						</span>
					) : (
						<button
							type="button"
							data-step-key="moment"
							title="Open its sheet"
							onClick={onOpen}
							className="block w-full rounded-md px-1.5 py-1 text-left outline-none hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-ring"
						>
							<span className="block font-medium">{moment.displayName}</span>
							<span className="block text-muted-foreground">
								{DISCOVERED_VIA_LABELS[moment.discoveredVia]} · {SIGNAL_STATE_LABELS[moment.state]}
							</span>
						</button>
					)}
				</Step>
				<Step
					dot={{ tone: TRACE_END_TONES[verdict.end.kind] }}
					data-step="end"
					data-end={verdict.end.kind}
				>
					<p className="px-1.5 py-1">
						<span className="font-medium">{verdict.end.label}</span>: {verdict.end.reason}
					</p>
				</Step>
			</ol>
		</div>
	);
}
