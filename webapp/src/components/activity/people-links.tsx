import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";

import { personLevel } from "./activity-search";
import type { ActivityPeopleTableProps } from "./ActivityPeopleTable";

type PersonLink = ActivityPeopleTableProps["personLink"];

/** On workspace activity a name opens the person's level over the page. */
export const personLevelLink: PersonLink = ({ login }) => (
	<DetailStackLink entry={personLevel(login)} />
);
