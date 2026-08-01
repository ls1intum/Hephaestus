import type { CuratedCatalogSummary as Summary } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";

export interface CuratedCatalogSummaryProps {
	summary: Summary;
}

/**
 * The catalog in one line, so its state is read at a glance rather than reconstructed by scanning
 * every row. Only what departs from "following Hephaestus" is counted — an instance nobody has
 * touched reads as one sentence and no badges.
 */
export function CuratedCatalogSummary({ summary }: CuratedCatalogSummaryProps) {
	// Everything the administrator has not spoken about. Clamped, because an update whose incoming
	// definition matches ours is waiting without being counted in either update bucket.
	const untouched = Math.max(
		0,
		summary.total -
			summary.editedHere -
			summary.yours -
			summary.noLongerShipped -
			summary.updatesChangingDetection -
			summary.updatesChangingWordingOnly,
	);
	if (summary.total === 0) {
		return null;
	}
	return (
		<div className="flex flex-wrap items-center gap-2 rounded-lg border bg-card p-4 text-sm">
			<span className="font-medium">
				{untouched === summary.total
					? `All ${summary.total} practices and areas follow Hephaestus.`
					: `${summary.total} practices and areas. ${untouched} ${untouched === 1 ? "follows" : "follow"} Hephaestus.`}
			</span>
			{summary.updatesChangingDetection > 0 && (
				<Badge variant="warning">{summary.updatesChangingDetection} would change detection</Badge>
			)}
			{summary.updatesChangingWordingOnly > 0 && (
				<Badge variant="secondary">{summary.updatesChangingWordingOnly} wording only</Badge>
			)}
			{summary.editedHere > 0 && (
				<Badge variant="secondary">{summary.editedHere} edited here</Badge>
			)}
			{summary.yours > 0 && <Badge variant="secondary">{summary.yours} added here</Badge>}
			{summary.noLongerShipped > 0 && (
				<Badge variant="outline">{summary.noLongerShipped} no longer shipped</Badge>
			)}
			{summary.retired > 0 && <Badge variant="outline">{summary.retired} not offered</Badge>}
		</div>
	);
}
