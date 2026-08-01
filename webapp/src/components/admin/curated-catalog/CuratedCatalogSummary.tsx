import type { CuratedCatalogSummary as Summary } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";

/**
 * The catalog in one sentence, so the state of the whole thing is read at a glance rather than
 * reconstructed by scanning every entry.
 */
export function CuratedCatalogSummary({ summary }: { summary: Summary }) {
	const waiting = summary.updatesChangingDetection + summary.updatesChangingWordingOnly;
	return (
		<div className="flex flex-wrap items-center gap-2 rounded-lg border bg-card p-4 text-sm">
			<span className="font-medium">{summary.total} entries follow Hephaestus by default.</span>
			{summary.editedHere > 0 && (
				<Badge variant="secondary">{summary.editedHere} edited here</Badge>
			)}
			{summary.yours > 0 && <Badge variant="secondary">{summary.yours} yours</Badge>}
			{summary.retired > 0 && <Badge variant="outline">{summary.retired} not offered</Badge>}
			{summary.noLongerShipped > 0 && (
				<Badge variant="outline">{summary.noLongerShipped} no longer shipped</Badge>
			)}
			{waiting > 0 && (
				<Badge variant="warning">
					{waiting} {waiting === 1 ? "update" : "updates"} waiting
					{summary.updatesChangingDetection > 0 &&
						` (${summary.updatesChangingDetection} change detection)`}
				</Badge>
			)}
		</div>
	);
}

/** Shown when nothing has been edited, because then there is genuinely nothing to do. */
export function CuratedCatalogNothingToDo() {
	return (
		<Alert>
			<AlertTitle>Nothing needs your attention</AlertTitle>
			<AlertDescription>
				Every entry is the one Hephaestus ships, and new versions arrive on their own. Edit an entry
				only where this instance needs something different.
			</AlertDescription>
		</Alert>
	);
}
