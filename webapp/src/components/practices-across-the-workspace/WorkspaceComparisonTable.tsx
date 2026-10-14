import { ClipboardCheckIcon } from "lucide-react";
import type { ReactNode } from "react";

import type { OutcomeVector } from "@/api/types.gen";
import {
	OPEN_ROW_BAR,
	PracticeTableFrame,
	PracticeTableRow,
	STACKED,
	SubjectCell,
} from "@/components/practice-vocabulary/PracticeTable";
import { Skeleton } from "@/components/ui/skeleton";
import { TableCell, TableHead } from "@/components/ui/table";

import { OutcomeCounts, OutcomeCountsSkeleton } from "./OutcomeCounts";

/** The hidden words of the marked row, read after its name. */
export const CAME_FROM_YOUR_PROFILE = ", where you came from in your Practice profile";

/** One practice group or one practice. */
export interface ComparisonRow {
	key: string;
	/** Ends the accessible name of the row's link, so each row's link is unique on the screen. */
	name: string;
	/** The name as the subject cell draws it: a group's pill, a practice's pill. */
	subject: ReactNode;
	/** What the row counts beside its results: "5 practices" for a group, "12 developers" for a practice. */
	count: string;
	outcomes: OutcomeVector;
}

export type ComparisonTableState =
	| { status: "loading" }
	| { status: "ready"; rows: readonly ComparisonRow[] };

export interface WorkspaceComparisonTableProps {
	"aria-label": string;
	/** "Practice group", "Practice". */
	subjectHead: string;
	/** The head of the {@link ComparisonRow.count} column. */
	countHead: string;
	state: ComparisonTableState;
	/** The link at the row's end, which a press anywhere on the row also follows. */
	rowLink: (row: ComparisonRow) => { text: string; onOpen: () => void };
	/** The row whose level is open over the page. */
	openKey?: string;
	/**
	 * The row the reader came from in their Practice profile. It wears the same bar as an open
	 * row, but says so in words too: `aria-current` and a hidden sentence, since no level is open.
	 */
	markedKey?: string;
	empty: { title: string; description: string };
}

/**
 * Practice groups or practices, each with its count and its results, in the practice table frame.
 * Every row is listed with no paging, as the Practice profile lists them.
 */
export function WorkspaceComparisonTable({
	"aria-label": label,
	subjectHead,
	countHead,
	state,
	rowLink,
	openKey,
	markedKey,
	empty,
}: WorkspaceComparisonTableProps) {
	return (
		<PracticeTableFrame
			aria-label={label}
			columns={4}
			// On a phone a row stacks as PracticeTable's do, so the counts never scroll out of sight.
			tableClassName={STACKED}
			head={
				<>
					<TableHead className="w-80">{subjectHead}</TableHead>
					<TableHead className="w-36">{countHead}</TableHead>
					<TableHead>Results</TableHead>
					<TableHead className="w-32">
						<span className="sr-only">Open</span>
					</TableHead>
				</>
			}
			rows={state.status === "ready" ? state.rows : []}
			rowKey={(row) => row.key}
			renderRow={(row) => (
				<PracticeTableRow
					open={row.key === openKey}
					marked={row.key === markedKey}
					link={{ ...rowLink(row), name: row.name }}
				>
					<SubjectCell
						badge={
							row.key === markedKey ? (
								<>
									{row.subject}
									<span className="sr-only">{CAME_FROM_YOUR_PROFILE}</span>
								</>
							) : (
								row.subject
							)
						}
						className={OPEN_ROW_BAR}
					/>
					<TableCell className="align-top text-sm tabular-nums">{row.count}</TableCell>
					<TableCell className="align-top whitespace-normal @max-[38rem]:basis-full">
						<OutcomeCounts outcomes={row.outcomes} />
					</TableCell>
				</PracticeTableRow>
			)}
			empty={{ icon: <ClipboardCheckIcon />, ...empty }}
			isLoading={state.status === "loading"}
			loadingRow={
				<>
					<TableCell>
						<span className="flex items-center gap-2">
							<Skeleton className="size-4 shrink-0 rounded-full" />
							<Skeleton className="h-4 w-48" />
						</span>
					</TableCell>
					<TableCell>
						<Skeleton className="h-4 w-24" />
					</TableCell>
					<TableCell className="align-top whitespace-normal @max-[38rem]:basis-full">
						<OutcomeCountsSkeleton />
					</TableCell>
					<TableCell>
						<Skeleton className="ml-auto h-4 w-24" />
					</TableCell>
				</>
			}
		/>
	);
}
