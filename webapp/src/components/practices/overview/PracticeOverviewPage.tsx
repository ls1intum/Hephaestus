import { AlertTriangle, Radar, ShieldAlert } from "lucide-react";
import { useState } from "react";
import type { CohortPracticeStatus, PracticeReportSummary } from "@/api/types.gen";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CohortHealthCard } from "./CohortHealthCard";
import { DeveloperDrillDownDialog } from "./DeveloperDrillDownDialog";
import { RosterTable } from "./RosterTable";

export interface PracticeOverviewPageProps {
	workspaceSlug: string;
	cohort?: CohortPracticeStatus[];
	roster?: PracticeReportSummary[];
	isLoading: boolean;
	/** True when the server refused access (403) — non-admins must not see cohort/roster data. */
	isForbidden: boolean;
	/** True when the queries failed for a non-403 reason (transient/server error). */
	isError?: boolean;
	/** Regular members with cohort visibility EVERYONE can see cohort cards, but never the named roster. */
	showRoster?: boolean;
	/** Retry the underlying queries after a non-403 failure. */
	onRetry?: () => void;
}

export function PracticeOverviewPage({
	workspaceSlug,
	cohort,
	roster,
	isLoading,
	isForbidden,
	isError = false,
	showRoster = true,
	onRetry,
}: PracticeOverviewPageProps) {
	const [selected, setSelected] = useState<PracticeReportSummary | null>(null);

	if (isForbidden) {
		return (
			<div className="container mx-auto max-w-4xl py-6">
				<EmptyState
					icon={ShieldAlert}
					title="Practice overview isn't available"
					description="The feature may be disabled for this workspace, or cohort visibility may be limited to admins and owners."
				/>
			</div>
		);
	}

	if (isError) {
		return (
			<div className="container mx-auto max-w-4xl py-6">
				<EmptyState
					icon={AlertTriangle}
					title="Couldn't load the practice overview"
					description="Something went wrong fetching the cohort. This is usually temporary, so try again."
					action={
						onRetry ? (
							<Button variant="outline" onClick={onRetry}>
								Retry
							</Button>
						) : undefined
					}
				/>
			</div>
		);
	}

	const cohortItems = cohort ?? [];
	const rosterItems = roster ?? [];

	return (
		<div className="container mx-auto flex max-w-5xl flex-col gap-6 py-6">
			<header className="flex flex-col gap-1">
				<h1 className="text-2xl font-bold tracking-tight">Practice Overview</h1>
				<p className="text-sm text-muted-foreground">
					A mentoring view of how the cohort is doing across practices. Not a ranking.
				</p>
			</header>

			<Tabs defaultValue="cohort">
				<TabsList>
					<TabsTrigger value="cohort">Cohort health</TabsTrigger>
					{showRoster && <TabsTrigger value="roster">Roster</TabsTrigger>}
				</TabsList>

				<TabsContent value="cohort" className="pt-4">
					{isLoading ? (
						<div className="grid gap-4 sm:grid-cols-2">
							<Skeleton className="h-40 w-full" />
							<Skeleton className="h-40 w-full" />
						</div>
					) : cohortItems.length === 0 ? (
						<EmptyState
							icon={Radar}
							title="No cohort activity yet"
							description="Cohort health appears once developers have recent reviewed work."
						/>
					) : (
						<div className="grid gap-4 sm:grid-cols-2">
							{cohortItems.map((health) => (
								<CohortHealthCard key={health.slug} health={health} />
							))}
						</div>
					)}
				</TabsContent>

				{showRoster && (
					<TabsContent value="roster" className="pt-4">
						{isLoading ? (
							<Skeleton className="h-64 w-full" />
						) : rosterItems.length === 0 ? (
							<EmptyState
								icon={Radar}
								title="No developers to show"
								description="The roster appears once developers have activity in the current review cycle."
							/>
						) : (
							<RosterTable entries={rosterItems} onSelectDeveloper={setSelected} />
						)}
					</TabsContent>
				)}
			</Tabs>

			{showRoster && (
				<DeveloperDrillDownDialog
					workspaceSlug={workspaceSlug}
					developer={selected}
					onClose={() => setSelected(null)}
				/>
			)}
		</div>
	);
}
