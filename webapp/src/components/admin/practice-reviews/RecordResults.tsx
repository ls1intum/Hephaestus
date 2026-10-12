import type { ComponentType, ReactElement, ReactNode } from "react";

import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	type RecordTableColumn,
	RecordTableSkeleton,
} from "@/components/practice-vocabulary/RecordTable";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";

import { REVIEW_PAGE_SIZE } from "./review-search";

/**
 * Where a record list stands, its rows aside: a filtered empty state has to carry the way out of
 * itself. Each list adds its rows to the `ready` state under its own name.
 */
export type RecordResultsStatus =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "empty"; filtered: false }
	| { status: "empty"; filtered: true; onClearFilters: () => void }
	| { status: "ready" };

/** What a list says when it has nothing to show, filtered or not. */
export interface RecordResultsWords {
	/** "We could not load results". */
	errorTitle: string;
	icon: ComponentType;
	/** "No results yet". */
	emptyTitle: string;
	/** "No results match these filters". */
	filteredTitle: string;
	/** Where the records come from, said while there are none yet. */
	emptyDescription: string;
}

export interface RecordResultsProps {
	state: RecordResultsStatus;
	"aria-label": string;
	columns: readonly RecordTableColumn[];
	words: RecordResultsWords;
	/** The table, handed what to say in it when it has no rows. */
	children: (empty: ReactNode) => ReactElement;
}

/**
 * The error, the skeleton and the empty state every record list shares, around the table it draws
 * itself: the Results and Feedback lists differ only in their rows and their words.
 */
export function RecordResults({
	state,
	"aria-label": label,
	columns,
	words,
	children,
}: RecordResultsProps): ReactElement {
	if (state.status === "error") {
		return <QueryErrorAlert error={state.error} title={words.errorTitle} onRetry={state.onRetry} />;
	}
	if (state.status === "loading") {
		return <RecordTableSkeleton aria-label={label} columns={columns} rows={REVIEW_PAGE_SIZE} />;
	}
	const Icon = words.icon;
	return children(
		state.status === "empty" && (
			<Empty>
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Icon />
					</EmptyMedia>
					<EmptyTitle>{state.filtered ? words.filteredTitle : words.emptyTitle}</EmptyTitle>
					<EmptyDescription>
						{state.filtered ? "Change or clear the filters to see more." : words.emptyDescription}
					</EmptyDescription>
				</EmptyHeader>
				{state.filtered && (
					<EmptyContent>
						<Button variant="outline" size="sm" onClick={state.onClearFilters}>
							Clear all filters
						</Button>
					</EmptyContent>
				)}
			</Empty>
		),
	);
}
