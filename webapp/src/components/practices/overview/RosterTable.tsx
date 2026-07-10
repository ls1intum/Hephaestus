import type { PracticeReportSummary, PracticeStatusCell } from "@/api/types.gen";
import { StandingChip } from "@/components/practices/StandingChip";
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

type Standing = PracticeStatusCell["standing"];

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
 */
export function RosterTable({ entries, onSelectDeveloper }: RosterTableProps) {
	// Column set = the reviewing practices, taken from the first entry that has them. The server
	// returns the same practice set (in the same order) for every developer.
	const practiceColumns = entries.find((entry) => entry.standings.length > 0)?.standings ?? [];

	return (
		<div className="overflow-x-auto rounded-md border">
			<Table>
				<TableHeader>
					<TableRow>
						<TableHead className="min-w-48">Developer</TableHead>
						{practiceColumns.map((practice) => (
							<TableHead key={practice.slug} className="whitespace-nowrap">
								{practice.name}
							</TableHead>
						))}
						<TableHead>Attention</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					{entries.map((entry) => {
						const standingBySlug = new Map(entry.standings.map((cell) => [cell.slug, cell]));
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
								{practiceColumns.map((practice) => {
									const cell = standingBySlug.get(practice.slug);
									return (
										<TableCell key={practice.slug}>
											<StandingCell standing={cell?.standing ?? "NO_ACTIVITY"} />
										</TableCell>
									);
								})}
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
										<span className="text-muted-foreground">—</span>
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
