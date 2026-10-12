import type { ReactNode } from "react";

import type { TracedArtifact } from "@/api/types.gen";
import {
	DISCOVERED_VIA_DESCRIPTIONS,
	DISCOVERED_VIA_LABELS,
	SIGNAL_STATE_LABELS,
	signalCountsLabel,
} from "@/components/practice-trace/trace-format";
import { TRACED_WORK_DEFS } from "@/components/practice-vocabulary/traced-work-defs";
import { isKnownArtifactKind, reviewedWorkName } from "@/lib/artifact-kinds";

import { RecordRowActions } from "./RecordRowActions";
import type { RowReviewRequest } from "./RequestReviewAction";
import { workRecord } from "./review-layers";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ReviewDayTime } from "./ReviewDayTime";
import { ReviewRow, ReviewRowLink, ReviewRowMeta } from "./ReviewRow";

export interface UnreviewedWorkRowProps {
	work: TracedArtifact;
	/** Request review, the row's third action; disabled for the kinds nobody can ask about. */
	request?: RowReviewRequest;
	/** Why the last ask about this work started nothing, drawn by the route. */
	refusal?: ReactNode;
}

/** A piece of work's key in the list: an id alone repeats across kinds. */
export function workRowKey(work: Pick<TracedArtifact, "artifactKind" | "artifactId">): string {
	return `${work.artifactKind}:${work.artifactId}`;
}

/**
 * One piece of work none of whose moments started a review: the work and its latest moment, how we
 * came to know of it and in which state it stopped. The title opens the work's sheet, which says why
 * no review started and which setting would let it through. Track, Trace and Request review end the
 * row.
 */
export function UnreviewedWorkRow({ work, request, refusal }: UnreviewedWorkRowProps) {
	const title = work.reviewedWork.title ?? work.reviewedWork.label;
	const kind = work.artifactKind;
	const moment = work.lastSignal;
	const record = isKnownArtifactKind(kind) ? workRecord(kind, work.artifactId) : undefined;
	return (
		<ReviewRow
			actions={
				record && (
					<RecordRowActions
						record={record}
						name={reviewedWorkName(work.reviewedWork)}
						request={request}
					/>
				)
			}
			status={TRACED_WORK_DEFS.NO_REVIEW_STARTED}
			title={
				record === undefined ? (
					<span className="font-medium break-words">{title}</span>
				) : (
					<ReviewRowLink entry={record} className="font-medium break-words">
						{title}
					</ReviewRowLink>
				)
			}
			meta={
				<>
					<ReviewRowMeta
						items={[
							// The link carries the provider mark and the repository already.
							<ReviewArtifactLink key="work" reviewedWork={work.reviewedWork} />,
							signalCountsLabel(work.signalCount, work.reviewedSignalCount),
						]}
					/>
					<p className="text-foreground">
						<span className="font-medium">Latest: {moment.displayName}</span>
						<span className="text-muted-foreground">
							{" · "}
							<ReviewDayTime at={moment.occurredAt} />
							{" · "}
							<span title={DISCOVERED_VIA_DESCRIPTIONS[moment.discoveredVia]}>
								{DISCOVERED_VIA_LABELS[moment.discoveredVia]}
							</span>
							{" · "}
							{SIGNAL_STATE_LABELS[moment.state]}
						</span>
					</p>
					{refusal}
				</>
			}
		/>
	);
}
