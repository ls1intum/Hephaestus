import type { CuratedCatalogSummary as Summary } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";

export interface CuratedCatalogSummaryProps {
	summary: Summary;
}

export function CuratedCatalogSummary({ summary }: CuratedCatalogSummaryProps) {
	const untouched = Math.max(
		0,
		summary.total -
			summary.editedHere -
			summary.yours -
			summary.noLongerShipped -
			summary.updatesChangingDetection -
			summary.updatesChangingWordingOnly -
			summary.updatesChangingPresentation,
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
			{summary.updatesChangingPresentation > 0 && (
				<Badge variant="secondary">
					{summary.updatesChangingPresentation} would change presentation
				</Badge>
			)}
			{summary.editedHere > 0 && (
				<Badge variant="secondary">{summary.editedHere} edited here</Badge>
			)}
			{summary.yours > 0 && <Badge variant="secondary">{summary.yours} added here</Badge>}
			{summary.noLongerShipped > 0 && (
				<Badge variant="outline">{summary.noLongerShipped} no longer shipped</Badge>
			)}
			{summary.notOffered > 0 && <Badge variant="outline">{summary.notOffered} not offered</Badge>}
		</div>
	);
}
