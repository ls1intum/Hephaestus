import type { ReactElement } from "react";

import { cn } from "cn";

import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Section } from "@/components/layout/Section";
import { Skeleton } from "@/components/ui/skeleton";
import type { ProviderType } from "@/lib/provider/provider-terms";

import type { ActivityOverviewState } from "./activity-buckets";
import { type ActivityPeriod, periodLabel } from "./activity-period";
import { STALE } from "./activity-tones";
import { ActivityRepositoryTable, type ActivityRepositoryRow } from "./ActivityRepositoryTable";
import { ActivityWorkLog, type ActivityWorkLogState, type WorkLogSubject } from "./ActivityWorkLog";
import { CopyMarkdownButton } from "./CopyMarkdownButton";

export type ActivityRepositoryState = PanelState<{
	rows: readonly ActivityRepositoryRow[];
	stale: boolean;
}>;

/** Member counts share the summary read; its pending and error states already appear there. */
export function memberRepositoryState(
	state: ActivityOverviewState,
): ActivityRepositoryState | undefined {
	return state.status === "ready" && state.overview.repositories.length > 0
		? {
				status: "ready",
				rows: state.overview.repositories.map(({ repository, counts }) => ({
					rowKey: String(repository.id),
					repository: { key: repository.key, name: repository.name },
					counts,
				})),
				stale: state.stale,
			}
		: undefined;
}

export interface PersonActivitySectionsProps {
	/** The heading level of the sections: 2 on a page, 3 in a drawer level under its title. */
	level: 2 | 3;
	period: ActivityPeriod;
	/** Beside the period's title: the controls that pick it, on a page that has them. */
	periodActions?: ReactElement;
	summary: ReactElement;
	/** Omitted when the summary already owns an unavailable repository read. */
	repositories?: ActivityRepositoryState;
	workLog: ActivityWorkLogState;
	providerType: ProviderType;
	subject: WorkLogSubject;
}

/**
 * One person's activity in a period: a tile for each kind of work with its weekly bars, the counts
 * in each repository, and the timeline of the work itself.
 */
export function PersonActivitySections({
	level,
	period,
	periodActions,
	summary,
	repositories,
	workLog,
	providerType,
	subject,
}: PersonActivitySectionsProps) {
	const stale = repositories?.status === "ready" && repositories.stale;
	return (
		<>
			<Section level={level} size="lg" title={periodLabel(period)} actions={periodActions}>
				{summary}
			</Section>
			{repositories !== undefined &&
				(repositories.status !== "ready" || repositories.rows.length > 0) && (
					<Section
						level={level}
						size="lg"
						title="Repositories"
						description={stale ? "Updating repository counts…" : undefined}
						aria-busy={stale || undefined}
						className={cn(stale && STALE)}
					>
						{repositories.status === "ready" && (
							<ActivityRepositoryTable
								repositories={repositories.rows}
								providerType={providerType}
							/>
						)}
						{repositories.status === "loading" && (
							<div className="space-y-3" aria-busy="true">
								<span className="sr-only">Loading repository counts…</span>
								{Array.from({ length: 3 }, (_, index) => (
									<Skeleton key={index} className="h-10 w-full" />
								))}
							</div>
						)}
						{repositories.status === "error" && (
							<QueryErrorAlert
								error={repositories.error}
								title="We could not load repository counts"
								onRetry={repositories.onRetry}
							/>
						)}
					</Section>
				)}
			<Section
				level={level}
				size="lg"
				title="Timeline"
				actions={
					workLog.status === "ready" && workLog.items.length > 0 && workLog.onCopy !== undefined ? (
						<CopyMarkdownButton onCopy={workLog.onCopy} />
					) : undefined
				}
			>
				<ActivityWorkLog state={workLog} providerType={providerType} subject={subject} />
			</Section>
		</>
	);
}
