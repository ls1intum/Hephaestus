import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Navigate } from "@tanstack/react-router";
import { z } from "zod";

import { getPracticeWorkspaceOverviewOptions } from "@/api/@tanstack/react-query.gen";
import type { PracticeWorkspaceOverview } from "@/api/types.gen";
import { type PanelState, panelState, queryLoadState } from "@/components/common/panel-state";
import {
	detailStackSchema,
	parseDetailStack,
} from "@/components/layout/detail-drawer/detail-stack";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { levelPathAt } from "@/components/layout/detail-drawer/level-path";
import { useDetailStack } from "@/components/layout/detail-drawer/use-detail-stack";
import { ALL_GROUPS_FOCUS, isFocusSwitchStep } from "@/components/practice-focus/practice-focus";
import {
	FOCUS_FOOTER_COPY,
	focusSwitchName,
	PracticeFocusFooter,
} from "@/components/practice-focus/PracticeFocusFooter";
import { usePracticeFocusSwitch } from "@/components/practice-focus/use-practice-focus-switch";
import {
	ACROSS_THE_WORKSPACE,
	ACROSS_WORKSPACE_STALE_MS,
} from "@/components/practices-across-the-workspace/across-workspace-copy";
import { PracticesAcrossTheWorkspacePage } from "@/components/practices-across-the-workspace/PracticesAcrossTheWorkspacePage";
import {
	groupsOf,
	type WorkspaceGroup,
} from "@/components/practices-across-the-workspace/workspace-groups";
import {
	WorkspaceGroupLevel,
	type WorkspaceGroupLevelState,
} from "@/components/practices-across-the-workspace/WorkspaceGroupLevel";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { pageHead } from "@/lib/page-title";

/** The reader's own group and practice levels live in their Practice profile, which this level links to. */
const LEVEL_KINDS = ["practice-group"] as const;

const searchSchema = z
	.object({
		// The practice row the group level marks, written by a switch from the reader's profile.
		practice: z.string().min(1).max(200).optional().catch(undefined),
	})
	.extend(detailStackSchema(LEVEL_KINDS).shape);

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/practices-across-the-workspace",
)({
	component: PracticesAcrossTheWorkspace,
	head: pageHead("Practices across the workspace"),
	validateSearch: searchSchema,
});

function PracticesAcrossTheWorkspace() {
	const { workspaceSlug } = Route.useParams();
	const { detail, practice } = Route.useSearch();
	const featureState = useWorkspaceFeatures(workspaceSlug);
	const enabled = featureState.practicesEnabled === true;
	const query = useQuery({
		...getPracticeWorkspaceOverviewOptions({ path: { workspaceSlug } }),
		enabled,
		staleTime: ACROSS_WORKSPACE_STALE_MS,
	});
	const stack = parseDetailStack(detail, LEVEL_KINDS);
	// The marked practice belongs to the group level and leaves the address with it.
	const stackControls = useDetailStack({
		levelParams: ["practice"],
		replaceOnClose: isFocusSwitchStep,
	});
	const openGroupSlug = stack[0]?.id;
	const focusSwitch = usePracticeFocusSwitch(workspaceSlug);

	if (featureState.practicesEnabled === false) {
		return <Navigate to="/w/$workspaceSlug" params={{ workspaceSlug }} replace />;
	}

	const featureLoad = queryLoadState({ ...featureState, isPending: featureState.isLoading });
	const state: PanelState<{ overview: PracticeWorkspaceOverview }> =
		featureLoad.status === "error"
			? featureLoad
			: panelState(query, (overview) => ({ status: "ready" as const, overview }));
	const groups = state.status === "ready" ? groupsOf(state.overview.practices) : [];
	// Each level reads its own group: a hand-edited address can stack two.
	const groupOf = (slug: string) => groups.find((each) => each.slug === slug);
	const pathAt = levelPathAt(stack, {
		pageLabel: ACROSS_THE_WORKSPACE,
		labelOf: (entry) => groupOf(entry.id)?.name ?? "Group",
		onClose: stackControls.close,
	});

	return (
		<>
			<PracticesAcrossTheWorkspacePage
				state={state}
				openGroupSlug={openGroupSlug}
				onOpenGroup={(groupSlug) => stackControls.open({ kind: "practice-group", id: groupSlug })}
				focusFooter={
					<PracticeFocusFooter
						{...FOCUS_FOOTER_COPY.workspaceAllGroups}
						placement="page"
						accessibleLabel={FOCUS_FOOTER_COPY.workspaceAllGroups.label}
						onSwitch={() => focusSwitch.toProfile(ALL_GROUPS_FOCUS)}
					/>
				}
			/>
			<DetailDrawerStack
				stack={stack}
				onClose={stackControls.close}
				footer={(entry) => (
					<PracticeFocusFooter
						{...FOCUS_FOOTER_COPY.workspaceGroup}
						accessibleLabel={focusSwitchName(
							FOCUS_FOOTER_COPY.workspaceGroup.label,
							groupOf(entry.id)?.name,
						)}
						onSwitch={() => focusSwitch.toProfile({ groupSlug: entry.id })}
					/>
				)}
			>
				{(entry, level) => (
					<WorkspaceGroupLevel
						nested={level.nested}
						path={pathAt(level.depth)}
						state={levelState(state, groupOf(entry.id))}
						focusPracticeSlug={practice}
						onGoToPractice={(practiceSlug) =>
							focusSwitch.toProfile({ groupSlug: entry.id, practiceSlug })
						}
					/>
				)}
			</DetailDrawerStack>
		</>
	);
}

function levelState(
	state: PanelState<{ overview: PracticeWorkspaceOverview }>,
	group: WorkspaceGroup | undefined,
): WorkspaceGroupLevelState {
	if (state.status !== "ready") {
		return state;
	}
	return group === undefined ? { status: "missing" } : { status: "ready", group };
}
