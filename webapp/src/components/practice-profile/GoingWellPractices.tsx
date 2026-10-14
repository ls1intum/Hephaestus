import type { ReactNode } from "react";

import type { GoingWellEvidence, GoingWellPractice } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import type { LoadState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { Section } from "@/components/layout/Section";
import { count } from "@/components/practice-vocabulary/feedback-text";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDay } from "@/lib/dates";
import { itemKindLabel } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

import { practiceLevel, REVIEW_SELECTION_CLEARED, reviewLevel } from "./practice-profile-search";

export interface GoingWellPracticesProps {
	/**
	 * The practices the reader keeps meeting, each with the recent results that show it, as the
	 * overview carries them. The server picks them; nothing here judges what goes well.
	 */
	practices: GoingWellPractice[];
	state: LoadState;
}

const TITLE = "Going well";

/** Two entries' worth of skeleton: a claim about the reader's work shows only once it is in. */
const SKELETON_ENTRIES = 2;

/**
 * The practices that go well in the reader's work, each named by a link to its own level, with the
 * workspace's sentence for it and the results that show it, each a link into its review. A practice
 * whose workspace wrote no sentence shows its name and its results alone: the page never writes
 * praise of its own.
 */
export function GoingWellPractices({ practices, state }: GoingWellPracticesProps) {
	let body: ReactNode;
	if (state.status === "error") {
		body = (
			<QueryErrorAlert
				error={state.error}
				title="We could not load what is going well"
				onRetry={state.onRetry}
			/>
		);
	} else if (state.status === "loading") {
		body = (
			<div className="flex flex-col gap-4" aria-busy="true">
				<span className="sr-only">Loading what is going well…</span>
				{Array.from({ length: SKELETON_ENTRIES }, (_, index) => (
					<div key={index} className="flex flex-col gap-2">
						<Skeleton className="h-5 w-56" />
						<Skeleton className="h-4 w-full max-w-md" />
						<Skeleton className="h-4 w-40" />
					</div>
				))}
			</div>
		);
	} else if (practices.length === 0) {
		body = (
			<p className="text-sm text-muted-foreground">
				Practices that your work keeps meeting appear here, with the work that shows it.
			</p>
		);
	} else {
		body = (
			<ul className="flex flex-col gap-5">
				{practices.map((practice) => (
					<li key={practice.practiceSlug}>
						<GoingWellEntry practice={practice} />
					</li>
				))}
			</ul>
		);
	}

	return (
		<Section size="lg" title={TITLE}>
			{body}
		</Section>
	);
}

function GoingWellEntry({ practice }: { practice: GoingWellPractice }) {
	const { practiceSlug, practiceName, goingWell, evidence, hasMore, workCount, since } = practice;
	return (
		<div className="flex min-w-0 flex-col gap-1.5">
			<h3 className="text-base font-medium break-words">
				<InlineLink render={<DetailStackLink entry={practiceLevel(practiceSlug)} />}>
					{practiceName}
				</InlineLink>
			</h3>
			{hasText(goingWell) && <p className="max-w-2xl text-sm text-pretty">{goingWell}</p>}
			<p className="text-xs text-muted-foreground">
				Met on {count(workCount, "piece of work", "pieces of work")} since {formatDay(since)}
			</p>
			{(evidence.length > 0 || hasMore) && (
				<ul
					aria-label={`Work that shows ${practiceName}`}
					className="flex min-w-0 flex-wrap gap-x-4 gap-y-1 text-sm"
				>
					{evidence.map((entry) => (
						<li key={entry.resultId} className="min-w-0 break-words">
							<EvidenceLink evidence={entry} />
						</li>
					))}
					{hasMore && (
						<li>
							<InlineLink
								tone="count"
								aria-label={`More results for ${practiceName}`}
								render={
									<DetailStackLink
										entry={practiceLevel(practiceSlug)}
										levelSearch={{ practiceTab: "results" }}
									/>
								}
							>
								More
							</InlineLink>
						</li>
					)}
				</ul>
			)}
		</div>
	);
}

/**
 * One result that shows the practice, by the item it was recorded on in the provider's own noun:
 * "Pull request #22". It opens the review that recorded it, at that result.
 */
function EvidenceLink({ evidence }: { evidence: GoingWellEvidence }) {
	const { item } = evidence;
	return (
		<InlineLink
			render={
				<DetailStackLink
					entry={reviewLevel(evidence.reviewId)}
					levelSearch={{ ...REVIEW_SELECTION_CLEARED, reviewResult: evidence.resultId }}
				/>
			}
		>
			{itemKindLabel(item.kind, 1, item.provider)} {item.label}
		</InlineLink>
	);
}
