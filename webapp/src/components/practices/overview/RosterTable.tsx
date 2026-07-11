import type { AreaStandingCell, PracticeReportSummary } from "@/api/types.gen";
import { StandingChip } from "@/components/practices/StandingChip";
import { TrendGlyph } from "@/components/practices/TrendBadge";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { getInitials } from "@/lib/avatar";

type Standing = AreaStandingCell["status"];

// Presentation-only ordering within one developer's row of area chips — never a reorder of
// developers. Areas that need a mentor's attention float to the front; a clean STRENGTH area and an
// area with no activity yet carry the least urgency, so they settle to the end.
const AREA_ATTENTION_PRIORITY: Record<Standing, number> = {
	DEVELOPING: 0,
	MIXED: 1,
	STRENGTH: 2,
	NO_ACTIVITY: 3,
};

function sortForAttention(standings: AreaStandingCell[]): AreaStandingCell[] {
	return [...standings].sort(
		(a, b) => AREA_ATTENTION_PRIORITY[a.status] - AREA_ATTENTION_PRIORITY[b.status],
	);
}

function StandingCell({ standing }: { standing: Standing }) {
	// NO_ACTIVITY renders as a muted em dash; the three active standings share the one StandingChip.
	if (standing === "NO_ACTIVITY") {
		return (
			<span className="text-muted-foreground" role="img" aria-label="No activity">
				—
			</span>
		);
	}
	return <StandingChip standing={standing} />;
}

export interface RosterTableProps {
	entries: PracticeReportSummary[];
	onSelectDeveloper: (entry: PracticeReportSummary) => void;
}

/**
 * The mentor roster. Rows are rendered in SERVER ORDER (needs-attention-then-alphabetical) and are
 * never re-sorted client-side. There is deliberately NO score, rank, position, or numeric column —
 * only criterion-referenced standing chips and an attention triage flag.
 *
 * There are ~12 practice areas per developer, so a 12-column matrix (one column per area) would force
 * either horizontal scrolling or illegibly narrow columns. Instead each developer gets ONE wrapping
 * row of compact "area name + standing chip" pairs — legible at any width, and it reads well on small
 * screens without a scrollbar. Areas that need attention are ordered first within the row (see
 * `sortForAttention`); this never reorders developers, only the chips inside a single row.
 */
export function RosterTable({ entries, onSelectDeveloper }: RosterTableProps) {
	return (
		<div className="overflow-x-auto rounded-md border">
			<Table>
				<TableHeader>
					<TableRow>
						<TableHead className="min-w-48">Developer</TableHead>
						<TableHead>Practice areas</TableHead>
						<TableHead>Attention</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					{entries.map((entry) => {
						const orderedStandings = sortForAttention(entry.standings);
						return (
							<TableRow
								key={entry.userLogin}
								className="cursor-pointer"
								onClick={() => onSelectDeveloper(entry)}
							>
								<TableCell>
									{/* The button is the real, keyboard-accessible control; the row onClick is a mouse
									    convenience. Table rows must keep row/cell semantics, so we never make the <tr>
									    a button (that would swallow the <td>s from assistive tech). */}
									<button
										type="button"
										className="flex items-center gap-2 rounded-sm text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
										onClick={(event) => {
											event.stopPropagation();
											onSelectDeveloper(entry);
										}}
									>
										<Avatar className="size-7">
											<AvatarImage src={entry.avatarUrl} alt="" />
											<AvatarFallback>{getInitials(entry.name, entry.userLogin)}</AvatarFallback>
										</Avatar>
										<div className="flex flex-col leading-tight">
											<span className="text-sm font-medium">{entry.name ?? entry.userLogin}</span>
											{entry.name && (
												<span className="text-xs text-muted-foreground">{entry.userLogin}</span>
											)}
										</div>
									</button>
								</TableCell>
								<TableCell>
									<div className="flex flex-wrap gap-x-4 gap-y-2">
										{orderedStandings.map((cell) => (
											<div key={cell.areaSlug} className="flex items-center gap-1.5 text-xs">
												<span className="text-muted-foreground">{cell.areaName}</span>
												<StandingCell standing={cell.status} />
												<TrendGlyph trend={cell.trend} />
											</div>
										))}
									</div>
								</TableCell>
								<TableCell>
									{entry.needsAttention && entry.attentionReasons.length > 0 ? (
										<div className="flex flex-wrap gap-1">
											{entry.attentionReasons.map((reason) => (
												<Badge key={reason} variant="secondary" className="whitespace-nowrap">
													{reason}
												</Badge>
											))}
										</div>
									) : (
										<span
											className="text-muted-foreground"
											role="img"
											aria-label="No attention needed"
										>
											—
										</span>
									)}
								</TableCell>
							</TableRow>
						);
					})}
				</TableBody>
			</Table>
		</div>
	);
}
