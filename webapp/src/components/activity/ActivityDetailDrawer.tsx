import type { ReactElement, ReactNode } from "react";

import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { levelPathAt } from "@/components/layout/detail-drawer/level-path";
import type { ProviderType } from "@/lib/provider/provider-terms";

import type { ActivityOverviewState } from "./activity-buckets";
import { categoryChartStateOf } from "./activity-category-chart";
import { ACTIVITY_CATEGORY_DEFS } from "./activity-kind-defs";
import { activityPersonName, type ActivityPersonIdentity } from "./activity-people-rows";
import { type ActivityPeriod, periodLabel } from "./activity-period";
import type { ActivityStackEntry } from "./activity-search";
import { ActivityCategoryLevel } from "./ActivityCategoryLevel";
import { ActivityTiles } from "./ActivityTiles";
import type { ActivityWorkLogState } from "./ActivityWorkLog";
import { PersonActivityLevel } from "./PersonActivityLevel";
import { memberRepositoryState } from "./PersonActivitySections";

/** One owner's reads for its levels: the period's overview, its timeline, and one category's. */
export interface ActivityLevelReads {
	overview: ActivityOverviewState;
	workLog: ActivityWorkLogState;
	categoryWorkLog: ActivityWorkLogState;
}

export interface ActivityDetailDrawerProps {
	stack: ActivityStackEntry[];
	onClose: (depth: number) => void;
	/** The page under the stack, as the first crumb: "Activity" or "Workspace activity". */
	pageLabel: string;
	providerType: ProviderType;
	period: ActivityPeriod;
	/**
	 * Whose activity the levels show: the reader's own on Your Activity, or the person opened on
	 * Workspace activity, with an admin's automation action where it applies.
	 */
	owner: ActivityLevelReads & {
		login: string | undefined;
		user?: ActivityPersonIdentity;
		/** The people are in, and the person the URL opens is not among them. */
		absent?: boolean;
		automationAction?: ReactElement;
		hideAction?: ReactElement;
	};
}

/** The detail levels over an Activity page, addressed by the route's `detail` stack. */
export function ActivityDetailDrawer({
	stack,
	onClose,
	pageLabel,
	providerType,
	period,
	owner,
}: ActivityDetailDrawerProps) {
	const nameOf = (login: string): string =>
		activityPersonName(owner.user?.login === login ? owner.user : undefined, login);
	const labelOf = ({ target }: ActivityStackEntry): string =>
		target.kind === "person"
			? nameOf(target.login)
			: ACTIVITY_CATEGORY_DEFS[target.category].label(providerType);
	const pathAt = levelPathAt(stack, { pageLabel, labelOf, onClose });

	return (
		<DetailDrawerStack stack={stack} onClose={onClose}>
			{(_entry, level): ReactNode => {
				const target = stack[level.depth]?.target;
				switch (target?.kind) {
					case "activity": {
						const { person } = target;
						return (
							<ActivityCategoryLevel
								nested={level.nested}
								path={pathAt(level.depth)}
								category={target.category}
								description={
									person === undefined
										? periodLabel(period)
										: `${periodLabel(period)} · ${nameOf(person)}`
								}
								providerType={providerType}
								chart={categoryChartStateOf(owner.overview, target.category)}
								workLog={owner.categoryWorkLog}
								subject={{ people: "one", login: person ?? owner.login }}
								absent={owner.absent}
							/>
						);
					}
					case "person": {
						return (
							<PersonActivityLevel
								nested={level.nested}
								path={pathAt(level.depth)}
								login={target.login}
								user={owner.user}
								absent={owner.absent}
								providerType={providerType}
								period={period}
								summary={<ActivityTiles state={owner.overview} providerType={providerType} />}
								repositories={memberRepositoryState(owner.overview)}
								workLog={owner.workLog}
								automationAction={owner.automationAction}
								hideAction={owner.hideAction}
							/>
						);
					}
					case undefined: {
						return null;
					}
				}
			}}
		</DetailDrawerStack>
	);
}
