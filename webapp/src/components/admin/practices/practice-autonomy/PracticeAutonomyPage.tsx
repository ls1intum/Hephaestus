import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type { AutonomyRollup, Practice, PracticeReviewSettings } from "@/api/types.gen";
import { automatedReviewLimitationLabel } from "@/components/admin/practice-catalog/evidence-presentation";
import { PracticeDetailHoverCard } from "@/components/admin/practice-catalog/PracticeDetailHoverCard";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ButtonGroup } from "@/components/ui/button-group";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuGroup,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Field, FieldDescription, FieldTitle } from "@/components/ui/field";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import {
	autonomyDistributionSentence,
	inheritedAutonomySourceSentence,
	PRACTICE_AUTONOMY_LABELS,
	PRACTICE_AUTONOMY_ORDER,
	type PracticeAutonomy,
	WORKSPACE_DEFAULT_SOURCE,
} from "@/lib/practice-autonomy";
import { cn } from "@/lib/utils";
import { AutonomyLadder } from "./AutonomyLadder";
import {
	type AutonomyGroup,
	countOverrides,
	groupPracticesByArea,
	isOverridden,
	reviewableByHephaestus,
} from "./practice-autonomy-model";

const DECISION_COLUMN = "sm:w-80";

const AREA_GRID = "sm:grid-cols-[minmax(0,1fr)_20rem]";

const ROW_GRID = "grid-cols-[auto_minmax(0,1fr)] sm:grid-cols-[auto_minmax(0,1fr)_20rem]";

export interface PracticeAutonomyPendingState {
	workspace: boolean;
	areaSlugs: ReadonlySet<string>;
	practiceSlugs: ReadonlySet<string>;
	bulk: { done: number; total: number } | null;
}

export interface PracticeAutonomyPageProps {
	workspaceSlug: string;
	settings: PracticeReviewSettings;
	rollup: AutonomyRollup;
	practices: Practice[];
	pending: PracticeAutonomyPendingState;
	overridesOnly: boolean;
	onOverridesOnlyChange: (next: boolean) => void;
	onSetWorkspaceDefault: (autonomy: PracticeAutonomy) => void;
	onClearWorkspaceDefault: () => void;
	onSetAreaAutonomy: (areaSlug: string, autonomy: PracticeAutonomy) => void;
	onClearAreaAutonomy: (areaSlug: string) => void;
	onSetPracticeAutonomy: (practiceSlug: string, autonomy: PracticeAutonomy) => void;
	onClearPracticeAutonomy: (practiceSlug: string) => void;
	onBulkSetAutonomy: (practiceSlugs: string[], autonomy: PracticeAutonomy | null) => void;
}

export function PracticeAutonomyPage({
	workspaceSlug,
	settings,
	rollup,
	practices,
	pending,
	overridesOnly,
	onOverridesOnlyChange,
	onSetWorkspaceDefault,
	onClearWorkspaceDefault,
	onSetAreaAutonomy,
	onClearAreaAutonomy,
	onSetPracticeAutonomy,
	onClearPracticeAutonomy,
	onBulkSetAutonomy,
}: PracticeAutonomyPageProps) {
	const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
	const [openAreas, setOpenAreas] = useState<string[]>([]);

	const groups = groupPracticesByArea(rollup, practices, { overridesOnly });
	const overrides = countOverrides(rollup);
	const openValue = overridesOnly ? groups.map((group) => group.key) : openAreas;

	const selectableSlugs = new Set(
		groups.flatMap((group) =>
			group.practices
				.filter((practice) => reviewableByHephaestus(practice.automatedReviewPolicy))
				.map((practice) => practice.slug),
		),
	);
	const actionable = [...selected].filter((slug) => selectableSlugs.has(slug));

	const toggle = (slug: string, checked: boolean) => {
		setSelected((current) => {
			const next = new Set(current);
			if (checked) next.add(slug);
			else next.delete(slug);
			return next;
		});
	};

	return (
		<div className="space-y-6">
			<WorkspaceDecisionCard
				settings={settings}
				saving={pending.workspace}
				onSetWorkspaceDefault={onSetWorkspaceDefault}
				onClearWorkspaceDefault={onClearWorkspaceDefault}
			/>
			<div className="sticky top-0 z-20 space-y-3 border-b bg-background/95 py-3 backdrop-blur supports-backdrop-filter:bg-background/80">
				<div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
					<AutonomySummary counts={rollup.counts} overrides={overrides} />
					<ScopeFilter value={overridesOnly} onChange={onOverridesOnlyChange} />
				</div>
				<BulkActionBar
					count={actionable.length}
					bulk={pending.bulk}
					onSet={(autonomy) => onBulkSetAutonomy(actionable, autonomy)}
					onClear={() => setSelected(new Set())}
				/>
			</div>

			{groups.length === 0 ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyTitle>
							{overridesOnly ? "Nothing was set by hand" : "No practices yet"}
						</EmptyTitle>
						<EmptyDescription>
							{overridesOnly
								? "Every area and practice follows the workspace default above. Switch the filter off to see them."
								: "Add practices in Practice setup, then decide how far reviews go on them."}
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<Accordion
					value={openValue}
					onValueChange={(next) => setOpenAreas(next as string[])}
					className="space-y-2"
				>
					{groups.map((group) => (
						<AreaGroup
							key={group.key}
							workspaceSlug={workspaceSlug}
							group={group}
							pending={pending}
							selected={selected}
							onToggle={toggle}
							onSelectMany={(slugs, checked) =>
								setSelected((current) => {
									const next = new Set(current);
									for (const slug of slugs) {
										if (checked) next.add(slug);
										else next.delete(slug);
									}
									return next;
								})
							}
							onSetAreaAutonomy={onSetAreaAutonomy}
							onClearAreaAutonomy={onClearAreaAutonomy}
							onSetPracticeAutonomy={onSetPracticeAutonomy}
							onClearPracticeAutonomy={onClearPracticeAutonomy}
						/>
					))}
				</Accordion>
			)}
		</div>
	);
}

function WorkspaceDecisionCard({
	settings,
	saving,
	onSetWorkspaceDefault,
	onClearWorkspaceDefault,
}: {
	settings: PracticeReviewSettings;
	saving: boolean;
	onSetWorkspaceDefault: (autonomy: PracticeAutonomy) => void;
	onClearWorkspaceDefault: () => void;
}) {
	const autonomyChosen = settings.defaultAutonomyOverride != null;

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Workspace default</h2>
				</CardTitle>
				<CardDescription>
					One decision for the whole workspace. Every area and every practice below follows it
					unless somebody says otherwise.
				</CardDescription>
			</CardHeader>
			<CardContent>
				<Field>
					<FieldTitle>How far reviews go without you</FieldTitle>
					<FieldDescription>
						Off stops the review. Review before sending prepares a proposal for a person to decide.
						Send automatically delivers eligible feedback without waiting.
					</FieldDescription>
					<AutonomyLadder
						label="How far reviews go without you"
						variant="full"
						value={settings.defaultAutonomy}
						disabled={saving}
						onChange={onSetWorkspaceDefault}
					/>
					<DecisionNote
						follows={
							autonomyChosen
								? null
								: `Not chosen yet, so ${PRACTICE_AUTONOMY_LABELS[settings.defaultAutonomy]} applies.`
						}
						resetLabel="Use the default for how far reviews go without you"
						disabled={saving}
						onClear={onClearWorkspaceDefault}
					/>
				</Field>
			</CardContent>
		</Card>
	);
}

function AutonomySummary({
	counts,
	overrides,
}: {
	counts: Record<string, number>;
	overrides: { practices: number; areas: number };
}) {
	return (
		<p className="min-w-0 text-muted-foreground text-sm" aria-live="polite" aria-atomic="true">
			{autonomyDistributionSentence(counts)} {byHandSentence(overrides)}
		</p>
	);
}

function byHandSentence(overrides: { practices: number; areas: number }): string {
	const parts: string[] = [];
	if (overrides.practices > 0) {
		parts.push(`${overrides.practices} ${overrides.practices === 1 ? "practice" : "practices"}`);
	}
	if (overrides.areas > 0) {
		parts.push(`${overrides.areas} ${overrides.areas === 1 ? "area" : "areas"}`);
	}
	return parts.length === 0 ? "" : `${parts.join(" and ")} set by hand.`;
}

function ScopeFilter({ value, onChange }: { value: boolean; onChange: (next: boolean) => void }) {
	return (
		<ToggleGroup
			role="toolbar"
			variant="outline"
			size="sm"
			aria-label="Filter practices"
			value={[value ? "OVERRIDES" : "ALL"]}
			onValueChange={(next) => next[0] && onChange(next[0] === "OVERRIDES")}
		>
			<ToggleGroupItem value="ALL">All</ToggleGroupItem>
			<ToggleGroupItem value="OVERRIDES">Set by hand</ToggleGroupItem>
		</ToggleGroup>
	);
}

function BulkActionBar({
	count,
	bulk,
	onSet,
	onClear,
}: {
	count: number;
	bulk: { done: number; total: number } | null;
	onSet: (autonomy: PracticeAutonomy | null) => void;
	onClear: () => void;
}) {
	if (count === 0 && bulk === null) return null;

	return (
		<Item variant="muted" size="sm" role="group" aria-label="Selected practices">
			<ItemContent>
				<ItemTitle aria-live="polite" aria-atomic="true" className="font-normal">
					{bulk
						? `Changing ${bulk.done} of ${bulk.total}…`
						: `${count} ${count === 1 ? "practice" : "practices"} selected`}
				</ItemTitle>
			</ItemContent>
			<ItemActions>
				<ButtonGroup>
					<DropdownMenu>
						<DropdownMenuTrigger
							render={
								<Button size="sm" variant="outline" disabled={bulk !== null || count === 0}>
									Change the selected
								</Button>
							}
						/>
						<DropdownMenuContent align="end">
							<DropdownMenuGroup>
								<DropdownMenuLabel>Set every selected practice to</DropdownMenuLabel>
								{PRACTICE_AUTONOMY_ORDER.map((autonomy) => {
									const { icon: Icon, label } = AUTONOMY_DEFS[autonomy];
									return (
										<DropdownMenuItem key={autonomy} onClick={() => onSet(autonomy)}>
											<Icon aria-hidden />
											{label}
										</DropdownMenuItem>
									);
								})}
							</DropdownMenuGroup>
							<DropdownMenuSeparator />
							<DropdownMenuItem onClick={() => onSet(null)}>
								Use the inherited setting
							</DropdownMenuItem>
						</DropdownMenuContent>
					</DropdownMenu>
					<Button size="sm" variant="outline" disabled={bulk !== null} onClick={onClear}>
						Clear selection
					</Button>
				</ButtonGroup>
			</ItemActions>
		</Item>
	);
}

function AreaGroup({
	workspaceSlug,
	group,
	pending,
	selected,
	onToggle,
	onSelectMany,
	onSetAreaAutonomy,
	onClearAreaAutonomy,
	onSetPracticeAutonomy,
	onClearPracticeAutonomy,
}: {
	workspaceSlug: string;
	group: AutonomyGroup;
	pending: PracticeAutonomyPendingState;
	selected: ReadonlySet<string>;
	onToggle: (slug: string, checked: boolean) => void;
	onSelectMany: (slugs: string[], checked: boolean) => void;
	onSetAreaAutonomy: (areaSlug: string, autonomy: PracticeAutonomy) => void;
	onClearAreaAutonomy: (areaSlug: string) => void;
	onSetPracticeAutonomy: (practiceSlug: string, autonomy: PracticeAutonomy) => void;
	onClearPracticeAutonomy: (practiceSlug: string) => void;
}) {
	const areaSlug = group.areaSlug;
	const areaPending = areaSlug !== null && pending.areaSlugs.has(areaSlug);
	const selectableSlugs = group.practices
		.filter((practice) => reviewableByHephaestus(practice.automatedReviewPolicy))
		.map((practice) => practice.slug);
	const allSelected =
		selectableSlugs.length > 0 && selectableSlugs.every((slug) => selected.has(slug));

	return (
		<AccordionItem value={group.key} className="scroll-mt-24 rounded-lg border bg-card px-3">
			<div className={cn("grid gap-2 py-1 sm:items-center sm:gap-4", AREA_GRID)}>
				<AccordionTrigger>
					<span className="flex min-w-0 flex-col gap-1">
						<span className="flex flex-wrap items-center gap-2">
							<span className="break-words">{group.name}</span>
							{group.overriddenCount > 0 && (
								<Badge variant="outline">{group.overriddenCount} set by hand</Badge>
							)}
						</span>
						<span className="font-normal text-muted-foreground text-xs">
							{autonomyDistributionSentence(group.counts)}
						</span>
					</span>
				</AccordionTrigger>
				{areaSlug === null ? (
					<span className={cn("min-w-0 text-muted-foreground text-xs", DECISION_COLUMN)}>
						Follows the workspace default
					</span>
				) : (
					<div className={cn("min-w-0 space-y-1", DECISION_COLUMN)}>
						<AutonomyLadder
							label={`How far reviews go in ${group.name}`}
							value={group.autonomy.effective}
							muted={!isOverridden(group.autonomy)}
							disabled={areaPending}
							onChange={(autonomy) => onSetAreaAutonomy(areaSlug, autonomy)}
						/>
						<DecisionNote
							follows={inheritedAutonomySourceSentence(group.autonomy, WORKSPACE_DEFAULT_SOURCE)}
							resetLabel={`Use the default for ${group.name}`}
							disabled={areaPending}
							onClear={() => onClearAreaAutonomy(areaSlug)}
						/>
					</div>
				)}
			</div>
			<AccordionContent className="pb-3">
				{group.practices.length === 0 ? (
					<p className="py-2 text-muted-foreground text-sm">
						{group.totalPractices === 0
							? "No practices in this area."
							: "No practices here were set by hand."}
					</p>
				) : (
					<>
						{selectableSlugs.length > 0 && (
							<Button
								variant="link"
								size="inline"
								className="text-xs"
								aria-label={`${allSelected ? "Deselect" : "Select"} all ${selectableSlugs.length} practices in ${group.name}`}
								onClick={() => onSelectMany(selectableSlugs, !allSelected)}
							>
								{allSelected ? "Deselect" : "Select"} all {selectableSlugs.length}
							</Button>
						)}
						<ul className="mt-2 divide-y border-t">
							{group.practices.map((practice) => (
								<PracticeAutonomyRow
									key={practice.slug}
									workspaceSlug={workspaceSlug}
									practice={practice}
									areaName={group.areaSlug === null ? null : group.name}
									pending={pending.practiceSlugs.has(practice.slug)}
									selected={selected.has(practice.slug)}
									onToggle={onToggle}
									onSetAutonomy={onSetPracticeAutonomy}
									onClearAutonomy={onClearPracticeAutonomy}
								/>
							))}
						</ul>
					</>
				)}
			</AccordionContent>
		</AccordionItem>
	);
}

function PracticeAutonomyRow({
	workspaceSlug,
	practice,
	areaName,
	pending,
	selected,
	onToggle,
	onSetAutonomy,
	onClearAutonomy,
}: {
	workspaceSlug: string;
	practice: Practice;
	areaName: string | null;
	pending: boolean;
	selected: boolean;
	onToggle: (slug: string, checked: boolean) => void;
	onSetAutonomy: (practiceSlug: string, autonomy: PracticeAutonomy) => void;
	onClearAutonomy: (practiceSlug: string) => void;
}) {
	const reviewable = reviewableByHephaestus(practice.automatedReviewPolicy);
	const limitation = automatedReviewLimitationLabel(practice.automatedReviewPolicy.automatedReview);

	return (
		<Item
			render={<li />}
			variant="default"
			className={cn("grid items-start gap-2 rounded-none px-0 py-3 sm:gap-4", ROW_GRID)}
		>
			<ItemMedia>
				<Checkbox
					checked={selected}
					disabled={!reviewable}
					aria-label={`Select ${practice.name}`}
					onCheckedChange={(checked) => onToggle(practice.slug, checked)}
				/>
			</ItemMedia>
			<ItemContent className="min-w-0 gap-0.5">
				<ItemTitle className="w-full min-w-0 line-clamp-none">
					<PracticeDetailHoverCard practice={practice}>
						<Link
							to="/w/$workspaceSlug/admin/practices/$practiceSlug"
							params={{ workspaceSlug, practiceSlug: practice.slug }}
							className="break-words rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
						>
							{practice.name}
						</Link>
					</PracticeDetailHoverCard>
				</ItemTitle>
				<ItemDescription className="flex flex-wrap items-center gap-1.5">
					<span>{artifactKindLabel(practice.artifactKind)}</span>
					{limitation && <Badge variant="warning">{limitation}</Badge>}
				</ItemDescription>
			</ItemContent>
			<ItemActions
				className={cn(
					"col-span-2 min-w-0 flex-col items-stretch gap-1 sm:col-span-1",
					DECISION_COLUMN,
				)}
			>
				<AutonomyLadder
					label={`How far reviews go on ${practice.name}`}
					value={practice.autonomy.effective}
					muted={!isOverridden(practice.autonomy)}
					disabled={pending || !reviewable}
					onChange={(autonomy) => onSetAutonomy(practice.slug, autonomy)}
				/>
				{reviewable ? (
					<DecisionNote
						follows={inheritedAutonomySourceSentence(
							practice.autonomy,
							areaName ?? WORKSPACE_DEFAULT_SOURCE,
						)}
						resetLabel={`Use the default for ${practice.name}`}
						disabled={pending}
						onClear={() => onClearAutonomy(practice.slug)}
					/>
				) : (
					<p className="text-muted-foreground text-xs">
						This practice can't be reviewed automatically, so it stays off.
					</p>
				)}
			</ItemActions>
		</Item>
	);
}

export interface DecisionNoteProps {
	follows: string | null;
	resetLabel: string;
	disabled: boolean;
	onClear: () => void;
}

function DecisionNote({ follows, resetLabel, disabled, onClear }: DecisionNoteProps) {
	return (
		<p className="text-muted-foreground text-xs">
			{follows ?? (
				<>
					Set here.{" "}
					<Button
						variant="link"
						size="inline"
						aria-label={resetLabel}
						disabled={disabled}
						onClick={onClear}
					>
						Use the default
					</Button>
				</>
			)}
		</p>
	);
}
