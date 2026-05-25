import { InfoIcon } from "@primer/octicons-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { ProviderType } from "@/lib/provider";
import { cn } from "@/lib/utils";
import { type ActivityLegendItem, getActivityLegendItems } from "./activity-badge-metadata";
import { ScoringExplanationDialog } from "./ScoringExplanationDialog";

export function LeaderboardLegend({ providerType = "GITHUB" }: { providerType?: ProviderType }) {
	const [showScoringModal, setShowScoringModal] = useState(false);
	const legendItems = getActivityLegendItems(providerType);
	const scoredItems = legendItems.filter((item) => item.countsTowardScore);
	const contextItems = legendItems.filter((item) => !item.countsTowardScore);

	return (
		<>
			<Card>
				<CardHeader>
					<CardTitle>
						<InfoIcon className="inline mr-2 h-4 w-4" /> Activity Legend
					</CardTitle>
					<CardDescription>What counts toward score, and what is just shown.</CardDescription>
				</CardHeader>
				<CardContent>
					<div className="space-y-3">
						<div className="space-y-2">
							<p className="text-sm font-medium">Counts toward score</p>
							<div className="grid grid-cols-1 gap-2">
								{scoredItems.map((item) => (
									<LegendItem key={item.key} item={item} />
								))}
							</div>
						</div>

						<div className="space-y-2 pt-2 border-t">
							<p className="text-sm font-medium">Also shown</p>
							<div className="grid grid-cols-1 gap-2">
								{contextItems.map((item) => (
									<LegendItem key={item.key} item={item} />
								))}
							</div>
						</div>

						<div className="pt-2 border-t">
							<p className="text-sm text-provider-muted-foreground mb-2">
								Only reviews affect score. Everything else is shown for context.
							</p>
							<Button
								variant="outline"
								size="sm"
								className="text-provider-link-foreground"
								onClick={() => setShowScoringModal(true)}
							>
								How scoring works
							</Button>
						</div>
					</div>
				</CardContent>
			</Card>

			<ScoringExplanationDialog
				open={showScoringModal}
				onOpenChange={setShowScoringModal}
				providerType={providerType}
			/>
		</>
	);
}

function LegendItem({ item }: { item: ActivityLegendItem }) {
	const Icon = item.icon;

	return (
		<div className={cn("flex items-center gap-2", item.colorClass)}>
			<Icon className="h-4 w-4" size={16} />
			<span>{item.label}</span>
		</div>
	);
}
