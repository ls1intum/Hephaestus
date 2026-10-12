import { Link } from "@tanstack/react-router";

import type { Practice, PracticeDefinitionOptions, PracticeRevision } from "@/api/types.gen";
import { PRACTICE_DEFINITION_FIELDS } from "@/components/admin/practices/practice-definition-fields";
import { practiceFormLevel } from "@/components/admin/practices/practice-search";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { WorkspacePracticeDefinition } from "@/components/admin/practices/WorkspacePracticeDefinition";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import { detailStackKey } from "@/components/layout/detail-drawer/detail-stack";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import { AutonomySourceNote } from "@/components/practice-vocabulary/AutonomySourceNote";
import { DrawerBody } from "@/components/ui/drawer";
import { autonomySourceOf } from "@/lib/practice-autonomy";
import { hasText } from "@/lib/text";

import {
	type SheetEvent,
	SheetHistory,
	SheetSection,
	SheetStatusLine,
	SheetTechnical,
} from "./RecordSheet";
import { ReviewLevelHeader } from "./ReviewLevelHeader";

export interface PracticeLevelProps {
	workspaceSlug: string;
	nested?: boolean;
	path: LevelPath;
	/**
	 * The practice as Practice setup defines it, with the options its definition is read against;
	 * `practice` is `undefined` for one the workspace no longer has.
	 */
	practice: PanelState<{
		practice: Practice | undefined;
		definitionOptions: PracticeDefinitionOptions;
		/** The group's display name, for the autonomy it inherits. Absent when unassigned. */
		groupName?: string;
	}>;
	/** Every stored version of its definition, newest first, with what changed in each. */
	revisions?: PanelState<{ revisions: PracticeRevision[] }>;
}

/**
 * One practice's sheet: its full definition and nothing else, as Practice setup's panel shows it —
 * one component for both. The status line says its autonomy, where that comes from and when it last
 * changed; the history says when it was created, each version of its definition with what changed
 * in it, and whether its automated review was withdrawn. It counts nothing: the row it was opened from has the counts. Changing it is Practice
 * setup's form, which the header links to.
 */
export function PracticeLevel({
	workspaceSlug,
	nested,
	path,
	practice: state,
	revisions,
}: PracticeLevelProps) {
	const practice = state.status === "ready" ? state.practice : undefined;
	const groupName = state.status === "ready" ? state.groupName : undefined;
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="practice"
			loading={state.status === "loading"}
			chips={
				practice && (
					<SheetStatusLine
						status={<StatusBadge def={AUTONOMY_DEFS[practice.autonomy.effective]} />}
						flag={
							<AutonomySourceNote
								source={autonomySourceOf(practice.autonomy, groupName ?? null)}
								className="text-muted-foreground"
							/>
						}
						at={{ label: "changed", date: practice.updatedAt }}
					/>
				)
			}
			title={practice?.name}
			description={
				practice && (
					<InlineLink
						render={
							<Link
								to="/w/$workspaceSlug/admin/practices"
								params={{ workspaceSlug }}
								search={{ detail: [detailStackKey(practiceFormLevel(practice.slug))] }}
							/>
						}
					>
						Edit in Practice setup
					</InlineLink>
				)
			}
		/>
	);
	if (state.status !== "ready") {
		return (
			<>
				{header}
				<DrawerBody className="pt-2">
					{state.status === "error" ? (
						<QueryErrorAlert
							error={state.error}
							title="We could not load this practice"
							onRetry={state.onRetry}
						/>
					) : (
						<PracticeDefinitionSkeleton />
					)}
				</DrawerBody>
			</>
		);
	}
	if (practice === undefined) {
		return (
			<>
				{header}
				<DrawerBody className="pt-2">
					<p className="text-sm text-muted-foreground">
						This workspace no longer has this practice.
					</p>
				</DrawerBody>
			</>
		);
	}
	return (
		<>
			{header}
			<DrawerBody className="flex flex-col gap-8 pt-2">
				<SheetSection part="what">
					<WorkspacePracticeDefinition
						practice={practice}
						definitionOptions={state.definitionOptions}
						groupName={groupName}
						showStanding={false}
					/>
				</SheetSection>
				<SheetSection part="state">
					<SheetHistory
						events={[
							...practiceHistory(practice),
							...(revisions?.status === "ready" ? revisions.revisions.map(revisionEvent) : []),
						]}
					/>
					{revisions?.status === "error" && (
						<QueryErrorAlert
							error={revisions.error}
							title="We could not load the versions of this practice"
							onRetry={revisions.onRetry}
						/>
					)}
				</SheetSection>
				<SheetTechnical
					facts={[
						{ label: "Slug", value: <code>{practice.slug}</code>, copy: practice.slug },
						{ label: "Practice ID", value: String(practice.id), copy: String(practice.id) },
						{
							label: "Group",
							value: hasText(practice.groupSlug) ? <code>{practice.groupSlug}</code> : "Unassigned",
						},
						{
							label: "Current version",
							value:
								revisions?.status === "ready" && revisions.revisions.length > 0
									? String(revisions.revisions[0]?.revisionNumber)
									: "—",
						},
						{
							label: "Precompute script",
							value: hasText(practice.precomputeScript) ? "Present" : "None",
						},
					]}
				/>
			</DrawerBody>
		</>
	);
}

function practiceHistory(practice: Practice): SheetEvent[] {
	const events: SheetEvent[] = [
		{
			key: "created",
			at: practice.createdAt,
			title: "Created",
			detail:
				practice.catalogOrigin === undefined
					? "Written in this workspace."
					: `Adopted from the catalog (${practice.catalogOrigin.slug}).`,
		},
	];
	if (practice.updatedAt.getTime() !== practice.createdAt.getTime()) {
		events.push({ key: "changed", at: practice.updatedAt, title: "Last changed" });
	}
	if (practice.automatedReviewWithdrawal !== undefined) {
		events.push({
			key: "withdrawn",
			at: undefined,
			title: "Automated review withdrawn",
			detail: practice.automatedReviewWithdrawal.description,
		});
	}
	return events;
}

/** One version of the definition: what changed in it, and whether what a review checks changed. */
function revisionEvent(revision: PracticeRevision): SheetEvent {
	const changed = revision.changed.map((field) => PRACTICE_DEFINITION_FIELDS[field].label);
	let detail: string | undefined;
	if (revision.revisionNumber === 1) {
		detail = "The first version of the definition.";
	} else if (changed.length === 0) {
		detail = "Recorded again without a change to the definition.";
	} else {
		detail = `Changed: ${changed.join(", ")}.`;
	}
	return {
		key: `revision-${revision.id}`,
		at: revision.createdAt,
		title: `Version ${revision.revisionNumber}`,
		detail: (
			<>
				<span className="block">{detail}</span>
				{revision.reviewRulesChanged && (
					<span className="block">
						What a review checks changed, so earlier observations no longer reflect it.
					</span>
				)}
			</>
		),
	};
}
