import type { ReactElement } from "react";

import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import type { ProviderType } from "@/lib/provider/provider-terms";

import type { ActivityPersonIdentity } from "./activity-people-rows";
import type { ActivityPeriod } from "./activity-period";
import { ActivityEmpty, HistoryMark } from "./ActivityEmpty";
import type { ActivityWorkLogState } from "./ActivityWorkLog";
import { PersonActivityHeader } from "./PersonActivityHeader";
import { PersonActivitySections, type ActivityRepositoryState } from "./PersonActivitySections";

export interface PersonActivityLevelProps {
	nested?: boolean;
	path: LevelPath;
	login: string;
	/** The person as the people list carries them; the login stands in until it arrives. */
	user?: ActivityPersonIdentity;
	identityLoading?: boolean;
	/** Nobody by this login contributed in the period and scope, so there is nothing to count. */
	absent?: boolean;
	providerType: ProviderType;
	scope?: string;
	period: ActivityPeriod;
	summary: ReactElement;
	repositories?: ActivityRepositoryState;
	workLog: ActivityWorkLogState;
	/** An admin's way to count the account as automation or as a person, where they may. */
	automationAction?: ReactElement;
	/** An admin's way to leave the person out of the activity pages, where they may. */
	hideAction?: ReactElement;
}

/**
 * One person's activity over workspace activity: their tiles, repositories and timeline in the
 * page's period and scope. Their provider profile is one link away.
 */
export function PersonActivityLevel({
	nested,
	path,
	login,
	user,
	identityLoading,
	absent = false,
	providerType,
	scope,
	period,
	summary,
	repositories,
	workLog,
	automationAction,
	hideAction,
}: PersonActivityLevelProps) {
	return (
		<>
			<PersonActivityHeader
				nested={nested}
				path={path}
				login={login}
				person={user}
				loading={identityLoading}
				providerType={providerType}
				scope={scope}
			/>
			<DrawerBody className="flex flex-col gap-8 pt-2">
				{absent ? (
					<ActivityEmpty
						icon={<HistoryMark providerType={providerType} />}
						title="No activity in this range"
					/>
				) : (
					<PersonActivitySections
						level={3}
						period={period}
						summary={summary}
						repositories={repositories}
						workLog={workLog}
						providerType={providerType}
						subject={{ people: "one", login }}
					/>
				)}
			</DrawerBody>
			{(automationAction ?? hideAction) && (
				<DrawerFooter>
					{automationAction}
					{hideAction}
				</DrawerFooter>
			)}
		</>
	);
}
