import type { FacetSource } from "@/components/common/FacetMultiSelect";

import { practiceGroups, workspaceMembers, workspacePractices } from "./fixtures";
import {
	groupFacetOptions,
	type PracticeFacetOption,
	practiceFacetOptions,
} from "./ObservationFilters";
import type { ReviewPeople } from "./ReviewPersonFacet";

/** The options of the shared scope's facets, loaded, for the stories of every review list. */
export const STORY_PEOPLE: ReviewPeople = {
	options: workspaceMembers.map((member) => ({
		userId: member.userId,
		label: member.userName ?? `#${member.userId}`,
		secondary: member.userLogin,
	})),
	capped: false,
	isLoading: false,
	isError: false,
};

export const STORY_GROUPS: FacetSource = {
	options: groupFacetOptions(practiceGroups),
	isLoading: false,
	isError: false,
};

export const STORY_PRACTICES: FacetSource<PracticeFacetOption> = {
	options: practiceFacetOptions(workspacePractices, practiceGroups),
	isLoading: false,
	isError: false,
};
