import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

import type { CatalogChangeCounts } from "./catalog-entry-state";

export interface CatalogSummaryProps {
	counts: CatalogChangeCounts;
	reviewing?: boolean;
	onReviewChanges: () => void;
}

export function CatalogSummary({
	counts,
	reviewing = false,
	onReviewChanges,
}: CatalogSummaryProps) {
	const changes =
		counts.reviewRules + counts.wording + counts.groupAppearance + counts.removedDefaults;
	if (changes === 0) {
		return null;
	}

	return (
		<div className="flex flex-wrap items-center gap-2 rounded-lg border border-warning/50 bg-warning/5 p-4 text-sm">
			<span className="font-medium">
				{changes} Hephaestus {changes === 1 ? "change needs" : "changes need"} review
			</span>
			{counts.reviewRules > 0 && (
				<Badge variant="warning">
					{counts.reviewRules} {counts.reviewRules === 1 ? "update would" : "updates would"} change
					review rules
				</Badge>
			)}
			{counts.wording > 0 && (
				<Badge variant="secondary">
					{counts.wording} {counts.wording === 1 ? "update would" : "updates would"} change wording
					or guidance
				</Badge>
			)}
			{counts.groupAppearance > 0 && (
				<Badge variant="secondary">
					{counts.groupAppearance} {counts.groupAppearance === 1 ? "update would" : "updates would"}{" "}
					change group appearance
				</Badge>
			)}
			{counts.removedDefaults > 0 && (
				<Badge variant="outline">
					{counts.removedDefaults} {counts.removedDefaults === 1 ? "entry is" : "entries are"} no
					longer in Hephaestus defaults
				</Badge>
			)}
			{!reviewing && (
				<Button variant="outline" size="sm" className="ml-auto" onClick={onReviewChanges}>
					Review changes
				</Button>
			)}
		</div>
	);
}
