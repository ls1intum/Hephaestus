import type { ReactNode } from "react";

import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { STAT_TILE_GRID, StatTileSkeleton } from "@/components/common/StatTile";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { levelPathAt } from "@/components/layout/detail-drawer/level-path";
import { Skeleton } from "@/components/ui/skeleton";
import type { ProviderType } from "@/lib/provider/provider-terms";

import { ACTIVITY_CATEGORY_DEFS } from "./activity-kind-defs";
import { activityPersonName, type ActivityPersonIdentity } from "./activity-people-rows";
import { type ActivityPeriod, periodLabel } from "./activity-period";
import type { ActivityStackEntry } from "./activity-search";
import { ActivityCategoryLevel } from "./ActivityCategoryLevel";
import type { ActivityWorkLogState } from "./ActivityWorkLog";
import { PersonActivityLevel } from "./PersonActivityLevel";
import {
	publicActivitySpanLabel,
	publicCategoryChartState,
	type PublicPersonActivityState,
} from "./public-person-activity";
import { PublicPersonSummary } from "./PublicPersonSummary";

export interface PublicPersonDrawerProps {
	stack: ActivityStackEntry[];
	onClose: (depth: number) => void;
	providerType: ProviderType;
	period: ActivityPeriod;
	person: PublicPersonActivityState;
	/** The same login already published by the page, while its independent detail loads. */
	seed?: ActivityPersonIdentity;
	workLog: ActivityWorkLogState;
	categoryWorkLog: ActivityWorkLogState;
}

/** Published data has its own read boundary; the person and category levels share their renderers. */
export function PublicPersonDrawer({
	stack,
	onClose,
	providerType,
	period,
	person,
	seed,
	workLog,
	categoryWorkLog,
}: PublicPersonDrawerProps) {
	let identity: ActivityPersonIdentity | undefined;
	if (person.status === "ready") {
		identity = person.person.person;
	} else if (person.status === "loading" && person.seedAllowed !== false) {
		identity = seed;
	}
	const allowedWork = person.status === "ready" ? workLog : person;
	const allowedCategoryWork = person.status === "ready" ? categoryWorkLog : person;
	const pathAt = levelPathAt(stack, {
		pageLabel: "Public activity",
		labelOf: ({ target }) =>
			target.kind === "person"
				? activityPersonName(identity?.login === target.login ? identity : undefined, target.login)
				: ACTIVITY_CATEGORY_DEFS[target.category].label(providerType),
		onClose,
	});
	return (
		<DetailDrawerStack stack={stack} onClose={onClose}>
			{(_entry, level): ReactNode => {
				const target = stack[level.depth]?.target;
				if (target?.kind === "activity") {
					if (target.category === "comments") {
						return null;
					}
					return (
						<ActivityCategoryLevel
							nested={level.nested}
							path={pathAt(level.depth)}
							category={target.category}
							description={`${periodLabel(period)} · ${activityPersonName(identity, target.person ?? "")}`}
							providerType={providerType}
							chart={publicCategoryChartState(person, target.category)}
							workLog={allowedCategoryWork}
							subject={{ people: "one", login: target.person }}
						/>
					);
				}
				if (target?.kind !== "person") {
					return null;
				}
				const ready = person.status === "ready" ? person : undefined;
				return (
					<PersonActivityLevel
						nested={level.nested}
						path={pathAt(level.depth)}
						login={target.login}
						user={identity}
						identityLoading={person.status === "loading" && identity === undefined}
						providerType={providerType}
						period={period}
						scope={ready && publicActivitySpanLabel(ready.person.from, ready.person.to)}
						summary={<PublicPersonSummaryRead state={person} providerType={providerType} />}
						repositories={
							ready && { status: "ready", rows: ready.person.repositories, stale: ready.stale }
						}
						workLog={allowedWork}
					/>
				);
			}}
		</DetailDrawerStack>
	);
}

function PersonSummarySkeleton() {
	return (
		<div className="@container" aria-busy>
			<span className="sr-only">Loading this person’s activity…</span>
			<ul className={STAT_TILE_GRID} aria-hidden inert>
				{Array.from({ length: 4 }, (_, index) => (
					<li key={index} className="flex">
						<StatTileSkeleton>
							<Skeleton className="h-16 w-full" />
						</StatTileSkeleton>
					</li>
				))}
			</ul>
		</div>
	);
}

function PublicPersonSummaryRead({
	state,
	providerType,
}: {
	state: PublicPersonActivityState;
	providerType: ProviderType;
}) {
	if (state.status === "error") {
		return (
			<QueryErrorAlert
				error={state.error}
				title="We could not load this person’s activity"
				onRetry={state.onRetry}
			/>
		);
	}
	if (state.status === "loading") {
		return <PersonSummarySkeleton />;
	}
	return (
		<PublicPersonSummary
			row={{
				counts: state.person.counts,
				weeks: state.person.weeks.map(({ start, counts }) => ({
					start,
					contributions: counts.contributions,
				})),
			}}
			providerType={providerType}
			stale={state.stale}
			span={{ from: state.person.from, to: state.person.to }}
		/>
	);
}
