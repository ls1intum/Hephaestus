// The palette this page shares with the practice profile is `webapp/AGENTS.md` § Practice surfaces palette.

import type { ReactNode } from "react";

import { useSpinDelay } from "spin-delay";
import type { PracticesAcrossWorkspace, PracticesAcrossWorkspaceTiles } from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RangeControls } from "@/components/common/RangeControls";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageLayout } from "@/components/layout/PageLayout";
import { Section } from "@/components/layout/Section";
import {
	ALL_PRACTICE_GROUPS,
	NO_PRACTICE_GROUPS,
} from "@/components/practice-profile/practice-profile-search";
import { getGroupVisual } from "@/components/practice-vocabulary/group-visuals";
import { GroupName } from "@/components/practice-vocabulary/GroupName";

import { InfoGroups, InfoLines, InfoTip } from "@/components/common/InfoTip";
import {
	type AcrossWorkspaceWindow,
	FIGURES_PLAIN,
	GROUPS_LOAD_ERROR,
	LOADING_LINE_AFTER_MS,
	LOADING_STATUS,
	PAGE_INTRO,
	PAGE_INTRO_SHORT,
	SLOW_LOAD_AFTER_MS,
	SLOW_LOAD_NOTE,
	SLOW_LOAD_SHORT,
	WINDOW_OPTIONS,
	windowHeading,
} from "./across-workspace-copy";
import { type ComparisonRow, WorkspaceComparisonTable } from "./WorkspaceComparisonTable";
import { SplitLegend } from "./WorkspaceSplitBar";
import { WorkspaceTiles } from "./WorkspaceTiles";

export interface PracticesAcrossTheWorkspacePageProps {
	/** The splits and the open feedback, which read no window. */
	state: PanelState<{ overview: PracticesAcrossWorkspace }>;
	/** `stale` while the tiles shown are the previous window's and the chosen window's are on their way. */
	tiles: PanelState<{ tiles: PracticesAcrossWorkspaceTiles; stale?: boolean }>;
	window: AcrossWorkspaceWindow;
	onWindowChange: (window: AcrossWorkspaceWindow) => void;
	/** The group whose level is open over the page. */
	openGroupSlug?: string;
	onOpenGroup: (groupSlug: string) => void;
	/** The way to the same list of every group in the reader's Practice profile, under the table. */
	focusFooter?: ReactNode;
}

/**
 * A view of the workspace, not the reader's profile: the reader's figures beside the workspace's
 * typical range over the chosen window, then every practice group's split by current standing,
 * with the reader only as the You marker. Only the tiles read the window.
 */
export function PracticesAcrossTheWorkspacePage({
	state,
	tiles,
	window,
	onWindowChange,
	openGroupSlug,
	onOpenGroup,
	focusFooter,
}: PracticesAcrossTheWorkspacePageProps) {
	const overview = state.status === "ready" ? state.overview : undefined;
	const windowTiles = tiles.status === "ready" ? tiles.tiles : undefined;
	const stale = tiles.status === "ready" && tiles.stale === true;
	const loading = state.status === "loading" || tiles.status === "loading";
	return (
		<PageLayout className="space-y-8">
			{/* The loading line hangs in the gap under the header, so nothing moves when it goes. */}
			<div className="relative">
				<PageHeader
					title="Practices across the workspace"
					description={
						<>
							{PAGE_INTRO_SHORT}{" "}
							<InfoTip label="About this page">
								<InfoLines lines={PAGE_INTRO} />
							</InfoTip>
						</>
					}
				/>
				<LoadingLine loading={loading} />
			</div>

			{state.status === "error" ? (
				// Every region reads the overview, so no control on the page can fix this but Retry.
				<QueryErrorAlert error={state.error} title={GROUPS_LOAD_ERROR} onRetry={state.onRetry} />
			) : (
				<>
					<Section
						size="lg"
						title="Your figures"
						description={
							<>
								{windowHeading(window)}{" "}
								<InfoTip label="About your figures">
									<InfoGroups groups={FIGURES_PLAIN} />
								</InfoTip>
							</>
						}
						aria-busy={loading || stale || undefined}
						actions={
							<RangeControls
								options={WINDOW_OPTIONS}
								range={window}
								onRangeChange={onWindowChange}
								updating={stale}
							/>
						}
					>
						{tiles.status === "error" ? (
							<QueryErrorAlert
								error={tiles.error}
								title="We could not load your figures"
								onRetry={tiles.onRetry}
							/>
						) : (
							<WorkspaceTiles
								tiles={windowTiles}
								openFeedback={overview?.openFeedback}
								stale={stale}
							/>
						)}
					</Section>

					<Section
						size="lg"
						title={ALL_PRACTICE_GROUPS}
						aria-busy={overview === undefined || undefined}
					>
						<SplitLegend scope="group" />
						<GroupsTable
							overview={overview}
							openGroupSlug={openGroupSlug}
							onOpenGroup={onOpenGroup}
						/>
						{focusFooter}
					</Section>
				</>
			)}
		</PageLayout>
	);
}

/**
 * One calm line while the server counts: a pulsing dot, what is happening and an (i) on the wait.
 * After {@link SLOW_LOAD_AFTER_MS} it says the count takes longer than usual. The live region is in
 * the page from the start and receives one message, once: the slow wording is only drawn, so a
 * screen reader hears the page is counting once, not again when the wording changes.
 */
function LoadingLine({ loading }: { loading: boolean }) {
	// A read that is in within the delay only flashes the skeletons, never the line.
	const counting = useSpinDelay(loading, { delay: LOADING_LINE_AFTER_MS, minDuration: 400 });
	const slow = useSpinDelay(loading, { delay: SLOW_LOAD_AFTER_MS, minDuration: 500 });
	return (
		<>
			<p role="status" className="sr-only">
				{counting ? LOADING_STATUS : null}
			</p>
			{counting && (
				<div
					data-slot="loading-line"
					className="absolute top-full left-0 mt-1.5 flex max-w-full items-center gap-2 text-sm text-muted-foreground"
				>
					<span
						aria-hidden
						className="size-2 shrink-0 animate-pulse rounded-full bg-mentor motion-reduce:animate-none"
					/>
					<span aria-hidden className="truncate">
						{slow ? SLOW_LOAD_SHORT : LOADING_STATUS}
					</span>
					<InfoTip label="About the wait" className="my-0">
						<InfoLines lines={SLOW_LOAD_NOTE} />
					</InfoTip>
				</div>
			)}
		</>
	);
}

function GroupsTable({
	overview,
	openGroupSlug,
	onOpenGroup,
}: {
	overview?: PracticesAcrossWorkspace;
	openGroupSlug?: string;
	onOpenGroup: (groupSlug: string) => void;
}) {
	const rows: ComparisonRow[] = (overview?.groups ?? []).map((group) => {
		const { Icon, pill } = getGroupVisual(group.groupIcon, group.groupColor);
		return {
			key: group.groupSlug,
			name: group.groupName,
			subject: <GroupName name={group.groupName} icon={Icon} pill={pill} />,
			yourStanding: group.yourStanding,
			split: group.split,
		};
	});
	return (
		<WorkspaceComparisonTable
			aria-label={ALL_PRACTICE_GROUPS}
			subjectHead="Practice group"
			state={overview === undefined ? { status: "loading" } : { status: "ready", rows }}
			openKey={openGroupSlug}
			empty={NO_PRACTICE_GROUPS}
			rowLink={(row) => ({
				text: "Open group",
				onOpen: () => onOpenGroup(row.key),
			})}
		/>
	);
}
