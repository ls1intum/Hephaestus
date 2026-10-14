import { HttpResponse, http } from "msw";

import type {
	AccountRef,
	CatalogOrigin,
	FeedbackModeAssignment,
	Practice,
	PracticeDefinition,
	PracticeGroup,
	PracticeGuidanceDto,
} from "@/api/types.gen";
import { mockDefinitionOptions } from "@/components/admin/practice-editor/fixtures";
import type { Wire } from "@/lib/dates";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";


const ADMIN: Wire<AccountRef> = {
	id: "7",
	displayName: "Ada Lovelace",
	avatarUrl: null,
	status: "ACTIVE",
	identities: [{ provider: "GITHUB", login: "ada", displayName: "Ada Lovelace", avatarUrl: null }],
};

const WRITTEN_HERE: CatalogOrigin = {
	catalogSlug: null,
	state: "YOURS",
	localEdits: [],
	offeredDigest: null,
};

const FOLLOWS_WORKSPACE: FeedbackModeAssignment = {
	effective: "APPROVE_FIRST",
	override: null,
	source: "WORKSPACE",
};

interface WirePracticeInput {
	slug: string;
	definition: PracticeDefinition;
	kind?: string;
	groupSlug?: string | null;
	position?: number;
	versionId?: string;
	versionNumber?: number;
	catalogOrigin?: CatalogOrigin;
}

export function wirePractice({
	slug,
	definition,
	kind = "scm.pull_request",
	groupSlug = null,
	position = 0,
	versionId = "501",
	versionNumber = 1,
	catalogOrigin = WRITTEN_HERE,
}: WirePracticeInput): Wire<Practice> {
	return {
		id: "50",
		slug,
		kind,
		groupSlug,
		position,
		currentVersion: {
			id: versionId,
			number: versionNumber,
			name: definition.name,
			definition,
			definitionAvailable: true,
			historicalDefinition: null,
			reviewDigest: `sha256:${versionId}`,
			createdAt: "2026-10-01T09:00:00Z",
			createdBy: ADMIN,
			changed: [],
			reviewChanged: false,
		},
		feedbackMode: FOLLOWS_WORKSPACE,
		catalogOrigin,
		createdAt: "2026-09-01T09:00:00Z",
		updatedAt: "2026-10-01T09:00:00Z",
	};
}

export function wireGroup(
	slug: string,
	name: string,
	overrides: Partial<Wire<PracticeGroup>> = {},
): Wire<PracticeGroup> {
	return {
		id: "60",
		slug,
		name,
		description: null,
		icon: null,
		color: null,
		position: 0,
		shownToDevelopers: true,
		feedbackMode: FOLLOWS_WORKSPACE,
		catalogOrigin: WRITTEN_HERE,
		createdAt: "2026-09-01T09:00:00Z",
		updatedAt: null,
		...overrides,
	};
}

/** Install defaults first; tests prepend their specific overrides through server.use. */
export function practiceSetupHandlers() {
	return [
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership("ADMIN")),
		),
		http.get("*/workspaces", () => HttpResponse.json([workspaceListItem("acme")])),
		http.get("*/workspaces/:workspaceSlug/practice-groups", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices/definition-options", () =>
			HttpResponse.json(mockDefinitionOptions),
		),
		http.get("*/workspaces/:workspaceSlug/practices/releases", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices/practice-script-needs", () =>
			HttpResponse.json([]),
		),
	];
}

// Register fixed practice paths before the slug handler, which can match them too.
export function practicesHandlers(practices: Wire<Practice>[]) {
	return [
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(practices)),
		http.get("*/workspaces/:workspaceSlug/practices/definition-options", () =>
			HttpResponse.json(mockDefinitionOptions),
		),
		http.get("*/workspaces/:workspaceSlug/practices/releases", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices/practice-script-needs", () =>
			HttpResponse.json([]),
		),
		http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug", ({ params }) => {
			const practice = practices.find(({ slug }) => slug === params.practiceSlug);
			return practice
				? HttpResponse.json(practice)
				: HttpResponse.json({ title: "Not Found", status: 404 }, { status: 404 });
		}),
	];
}

export function wireGuidance(
	practiceSlug: string,
	guide: string | null,
): Wire<PracticeGuidanceDto> {
	return {
		practiceSlug,
		visual: null,
		guide: guide === null ? null : { markdown: guide, figures: {} },
	};
}
