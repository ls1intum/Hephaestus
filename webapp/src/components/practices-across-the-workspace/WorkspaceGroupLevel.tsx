import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { LevelHeader } from "@/components/layout/detail-drawer/LevelHeader";
import { NoSuchGroup } from "@/components/practice-profile/practice-profile-blocks";
import { GroupPill } from "@/components/practice-vocabulary/GroupPill";
import { PracticePill } from "@/components/practice-vocabulary/PracticePill";
import { DrawerBody } from "@/components/ui/drawer";

import { developersText, GROUPS_LOAD_ERROR, OPEN_IN_YOUR_PROFILE } from "./across-workspace-copy";
import type { WorkspaceGroup } from "./workspace-groups";
import { type ComparisonRow, WorkspaceComparisonTable } from "./WorkspaceComparisonTable";

/** `missing` when the page lists no group by the slug in the address. */
export type WorkspaceGroupLevelState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "missing" }
	| { status: "ready"; group: WorkspaceGroup };

export interface WorkspaceGroupLevelProps {
	nested?: boolean;
	path: LevelPath;
	state: WorkspaceGroupLevelState;
	onGoToPractice: (practiceSlug: string) => void;
	/** The practice the reader came from in their profile, marked among the group's practices. */
	focusPracticeSlug?: string;
}

/**
 * One practice group over Practices across the workspace: each of its practices with how many
 * developers have results in it and how those results came out. The reader's own results stay in
 * their Practice profile, which every row link and the level's footer open.
 */
export function WorkspaceGroupLevel({
	nested,
	path,
	state,
	onGoToPractice,
	focusPracticeSlug,
}: WorkspaceGroupLevelProps) {
	const group = state.status === "ready" ? state.group : undefined;
	return (
		<>
			<LevelHeader
				nested={nested}
				path={path}
				current="Group"
				title={group?.name ?? "Practice group"}
				loading={state.status === "loading"}
				mark={
					group && (
						<GroupPill
							size="lg"
							slug={group.slug}
							name={group.name}
							icon={group.icon ?? undefined}
							color={group.color ?? undefined}
						/>
					)
				}
			/>
			<DrawerBody
				className="flex flex-col gap-4 pt-2"
				aria-busy={state.status === "loading" || undefined}
			>
				{state.status === "error" && (
					<QueryErrorAlert error={state.error} title={GROUPS_LOAD_ERROR} onRetry={state.onRetry} />
				)}
				{state.status === "missing" && <NoSuchGroup />}
				{(state.status === "loading" || state.status === "ready") && (
					<GroupPractices
						state={state}
						onGoToPractice={onGoToPractice}
						focusPracticeSlug={focusPracticeSlug}
					/>
				)}
			</DrawerBody>
		</>
	);
}

function GroupPractices({
	state,
	onGoToPractice,
	focusPracticeSlug,
}: {
	state: Extract<WorkspaceGroupLevelState, { status: "loading" | "ready" }>;
	onGoToPractice: (practiceSlug: string) => void;
	focusPracticeSlug?: string;
}) {
	const group = state.status === "ready" ? state.group : undefined;
	const rows: ComparisonRow[] = (group?.practices ?? []).map((practice) => ({
		key: practice.practiceSlug,
		name: practice.practiceName,
		subject: <PracticePill name={practice.practiceName} />,
		count: developersText(practice.developers),
		outcomes: practice.outcomes,
	}));
	return (
		<WorkspaceComparisonTable
			aria-label={group === undefined ? "Practices" : `Practices of ${group.name}`}
			subjectHead="Practices"
			countHead="Developers with results"
			state={state.status === "ready" ? { status: "ready", rows } : { status: "loading" }}
			empty={{
				title: "No practices yet",
				description: "Once your workspace reviews a practice in this group, it appears here.",
			}}
			rowLink={(row) => ({
				text: OPEN_IN_YOUR_PROFILE,
				onOpen: () => onGoToPractice(row.key),
			})}
			markedKey={focusPracticeSlug}
		/>
	);
}
