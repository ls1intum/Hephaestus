import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type {
	Practice,
	PracticeReviewSettings,
	ReviewTierAssignment,
	ReviewTierRollup,
} from "@/api/types.gen";
import { automatedReviewLimitationLabel } from "@/components/admin/practice-catalog/evidence-presentation";
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
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldLabel,
	FieldTitle,
} from "@/components/ui/field";
import { Item, ItemActions, ItemContent, ItemDescription, ItemTitle } from "@/components/ui/item";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import {
	FEEDBACK_REACH_DESCRIPTIONS,
	FEEDBACK_REACH_LABELS,
	FEEDBACK_REACH_ORDER,
	type FeedbackReach,
	inheritedTierSourceSentence,
	REVIEW_TIER_LABELS,
	REVIEW_TIER_ORDER,
	type ReviewTier,
	tierDistribution,
	tierDistributionSentence,
	tierTotal,
} from "@/lib/review-tiers";
import { cn } from "@/lib/utils";
import { ReviewTierLadder } from "./ReviewTierLadder";
import {
	type AutonomyGroup,
	countOverrides,
	groupPracticesByArea,
	isOverridden,
	reviewableByHephaestus,
} from "./review-autonomy-model";

/**
 * The width of the decision column, shared by an area's ladder and its practices'.
 *
 * <p>They are the same control at two levels of the same chain, so they belong in one column. They used
 * not to be: an area's ladder was laid out after a content-width accordion header, which put it at a
 * different x for every area — 285px under "Documentation", 416px under "Pull request hygiene" — while
 * the practice rows underneath pinned theirs to the right edge at 950px. Twenty-five areas, twenty-five
 * left edges, and no column anywhere on the page.
 */
const DECISION_COLUMN = "sm:w-80";

/** Area header and practice row lay out on the same two-track grid so the tracks line up. */
const DECISION_GRID = "sm:grid-cols-[minmax(0,1fr)_20rem]";

export interface ReviewAutonomyPendingState {
	workspace: boolean;
	areaSlugs: ReadonlySet<string>;
	practiceSlugs: ReadonlySet<string>;
	/** Non-null while a bulk change is running, so the bar can say how far it has got. */
	bulk: { done: number; total: number } | null;
}

export interface ReviewAutonomyPageProps {
	workspaceSlug: string;
	settings: PracticeReviewSettings;
	/** Server-resolved counts for the whole workspace and every area, in catalogue order. */
	rollup: ReviewTierRollup;
	practices: Practice[];
	pending: ReviewAutonomyPendingState;
	overridesOnly: boolean;
	onOverridesOnlyChange: (next: boolean) => void;
	onSetWorkspaceDefault: (tier: ReviewTier) => void;
	onClearWorkspaceDefault: () => void;
	onSetFeedbackReach: (reach: FeedbackReach) => void;
	onClearFeedbackReach: () => void;
	onSetAreaTier: (areaSlug: string, tier: ReviewTier) => void;
	onClearAreaTier: (areaSlug: string) => void;
	onSetPracticeTier: (practiceSlug: string, tier: ReviewTier) => void;
	onClearPracticeTier: (practiceSlug: string) => void;
	/** A null tier clears every named practice back to inheriting. */
	onBulkSetTier: (practiceSlugs: string[], tier: ReviewTier | null) => void;
}

/**
 * One screen for the decision that used to take a hundred edits.
 *
 * <p>Ordered by how much reach a decision has, because that is the order an admin should try them in:
 * the workspace default first, then an area, then one practice. Every level below the first is an
 * exception, and the screen says so — inherited rows recede, overridden rows are marked and carry the
 * way back.
 */
export function ReviewAutonomyPage({
	workspaceSlug,
	settings,
	rollup,
	practices,
	pending,
	overridesOnly,
	onOverridesOnlyChange,
	onSetWorkspaceDefault,
	onClearWorkspaceDefault,
	onSetFeedbackReach,
	onClearFeedbackReach,
	onSetAreaTier,
	onClearAreaTier,
	onSetPracticeTier,
	onClearPracticeTier,
	onBulkSetTier,
}: ReviewAutonomyPageProps) {
	const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
	const [openAreas, setOpenAreas] = useState<string[]>([]);

	const groups = groupPracticesByArea(rollup, practices, { overridesOnly });
	const overrides = countOverrides(rollup);
	// Filtering is a reading mode, not a navigation one: a narrowed list whose groups are all shut
	// hides the very rows it was opened to show.
	const openValue = overridesOnly ? groups.map((group) => group.key) : openAreas;

	const selectableSlugs = new Set(
		groups.flatMap((group) =>
			group.practices
				.filter((practice) => reviewableByHephaestus(practice.automatedReviewPolicy))
				.map((practice) => practice.slug),
		),
	);
	// A selection survives the filter being switched on, and the rows it named may now be hidden;
	// acting on what is no longer on screen is how a bulk change surprises somebody.
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
				onSetFeedbackReach={onSetFeedbackReach}
				onClearFeedbackReach={onClearFeedbackReach}
			/>

			{/* Sticky, because "what is my workspace doing right now" is the question the screen answers
			    and the answer must survive scrolling past the twentieth area. No negative margin: at
			    320px a strip wider than its parent drags the whole page sideways. */}
			<div className="sticky top-0 z-20 space-y-3 border-b bg-background/95 px-4 py-3 backdrop-blur supports-backdrop-filter:bg-background/80">
				<div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
					<TierSummary counts={rollup.counts} overrides={overrides} />
					{/* Hidden below `sm`, where the two stack and a rule between them is the gap already
					    there. Vertical separators need a height to draw at all. */}
					<Separator orientation="vertical" className="hidden h-8 sm:block" />
					<Field orientation="horizontal" className="w-auto sm:justify-end">
						<FieldLabel htmlFor="autonomy-overrides-only" className="font-normal text-sm">
							Only what was set by hand
						</FieldLabel>
						<Switch
							id="autonomy-overrides-only"
							checked={overridesOnly}
							onCheckedChange={onOverridesOnlyChange}
						/>
					</Field>
				</div>
				<BulkActionBar
					count={actionable.length}
					bulk={pending.bulk}
					onSet={(tier) => onBulkSetTier(actionable, tier)}
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
								: "Add practices in Practice setup, then decide how much Hephaestus does with them."}
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<Accordion
					value={openValue}
					onValueChange={(next) => setOpenAreas(next as string[])}
					className="rounded-lg border"
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
							onSetAreaTier={onSetAreaTier}
							onClearAreaTier={onClearAreaTier}
							onSetPracticeTier={onSetPracticeTier}
							onClearPracticeTier={onClearPracticeTier}
						/>
					))}
				</Accordion>
			)}
		</div>
	);
}

/**
 * The one decision that replaces a hundred, at the top of the screen where it is the first thing read.
 */
function WorkspaceDecisionCard({
	settings,
	saving,
	onSetWorkspaceDefault,
	onClearWorkspaceDefault,
	onSetFeedbackReach,
	onClearFeedbackReach,
}: {
	settings: PracticeReviewSettings;
	saving: boolean;
	onSetWorkspaceDefault: (tier: ReviewTier) => void;
	onClearWorkspaceDefault: () => void;
	onSetFeedbackReach: (reach: FeedbackReach) => void;
	onClearFeedbackReach: () => void;
}) {
	const tierChosen = settings.defaultReviewTierOverride != null;
	const reachChosen = settings.feedbackReachOverride != null;

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>What Hephaestus does on its own</h2>
				</CardTitle>
				<CardDescription>
					Two decisions for the whole workspace. Every area and every practice below follows them
					unless somebody says otherwise.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-6">
				<Field>
					<FieldTitle>How far it may go without you</FieldTitle>
					<FieldDescription>
						Each step keeps everything to its left and adds one thing. Turning this down leaves the
						reviews running — it only changes what happens next.
					</FieldDescription>
					<ReviewTierLadder
						label="How far Hephaestus may go without you"
						variant="full"
						value={settings.defaultReviewTier}
						disabled={saving}
						onChange={onSetWorkspaceDefault}
					/>
					{tierChosen ? (
						<div className="text-left">
							{/* The name opens with the visible words, so a voice-control user can say what they
							    read (WCAG 2.2 SC 2.5.3). Spelled as an `aria-label` rather than the house
							    `sr-only` tail because it states the whole name in one place, independent of
							    CSS. The `sr-only` tail is NOT broken — `position: absolute` blockifies the
							    span, and every engine inserts a separating space between non-inline children,
							    so "Add" + " to target branches" is announced "Add to target branches". It
							    only reads welded in jsdom, which loads no stylesheet; do not "fix" the
							    sr-only sites on the strength of a jsdom-only reproduction. */}
							<Button
								variant="link"
								size="sm"
								className="h-auto p-0 text-xs"
								aria-label="Use the default for how far Hephaestus may go without you"
								disabled={saving}
								onClick={onClearWorkspaceDefault}
							>
								Use the default
							</Button>
						</div>
					) : (
						<span className="text-muted-foreground text-xs">
							Not chosen yet, so {REVIEW_TIER_LABELS[settings.defaultReviewTier]} applies.
						</span>
					)}
				</Field>

				<Field>
					<FieldTitle>Where feedback may go</FieldTitle>
					<FieldDescription>
						Narrows where feedback can land. It never gives a practice more autonomy than its own
						tier allows.
					</FieldDescription>
					<RadioGroup
						aria-label="Where feedback may go"
						value={settings.feedbackReach}
						disabled={saving}
						onValueChange={(next) => {
							const reach = next as FeedbackReach;
							if (reach && reach !== settings.feedbackReach) onSetFeedbackReach(reach);
						}}
						className="gap-3"
					>
						{/* The dot leads, as it does on every rung of the ladder above — and a control whose
						    24px hit area is a `::after` box hanging 12px past its edge drags the page sideways
						    when it sits flush against the right margin. */}
						{FEEDBACK_REACH_ORDER.map((reach) => (
							<FieldLabel key={reach}>
								<Field orientation="horizontal">
									<RadioGroupItem value={reach} aria-label={FEEDBACK_REACH_LABELS[reach]} />
									<FieldContent>
										<span className="font-medium text-sm">{FEEDBACK_REACH_LABELS[reach]}</span>
										<FieldDescription>{FEEDBACK_REACH_DESCRIPTIONS[reach]}</FieldDescription>
									</FieldContent>
								</Field>
							</FieldLabel>
						))}
					</RadioGroup>
					{reachChosen ? (
						<div className="text-left">
							<Button
								variant="link"
								size="sm"
								className="h-auto p-0 text-xs"
								aria-label="Use the default for where feedback may go"
								disabled={saving}
								onClick={onClearFeedbackReach}
							>
								Use the default
							</Button>
						</div>
					) : (
						<span className="text-muted-foreground text-xs">
							Not chosen yet, so {FEEDBACK_REACH_LABELS[settings.feedbackReach].toLowerCase()}{" "}
							applies.
						</span>
					)}
				</Field>
			</CardContent>
		</Card>
	);
}

/**
 * What the workspace is doing right now, in one line that stays on screen.
 *
 * <p>Read straight off the rollup. Counting a hundred practice rows in the browser would put a second
 * implementation of the inheritance chain here, and it would answer for the rows that happen to be
 * loaded rather than for the workspace.
 */
function TierSummary({
	counts,
	overrides,
}: {
	counts: Record<string, number>;
	overrides: { practices: number; areas: number };
}) {
	const distribution = tierDistribution(counts);
	const total = tierTotal(counts);
	const byHand = overrides.practices + overrides.areas;

	return (
		<div className="min-w-0">
			{/* Announced as a sentence when a change lands: the visible line is separated by middots,
			    which a screen reader either swallows or reads out as punctuation. */}
			<p className="sr-only" aria-live="polite" aria-atomic="true">
				{tierDistributionSentence(counts)}
			</p>
			<p aria-hidden="true" className="flex flex-wrap items-baseline gap-x-2 gap-y-1 text-sm">
				{total === 0 ? (
					<span className="text-muted-foreground">No practices yet</span>
				) : (
					distribution.map(({ tier, count }, index) => (
						<span key={tier} className="flex items-baseline gap-2">
							{index > 0 && <span className="text-muted-foreground/50">·</span>}
							<span>
								<span className="font-semibold tabular-nums">{count}</span>{" "}
								<span className="text-muted-foreground">
									{REVIEW_TIER_LABELS[tier].toLowerCase()}
								</span>
							</span>
						</span>
					))
				)}
			</p>
			{byHand > 0 && (
				<p className="text-muted-foreground text-xs">
					{overrides.practices > 0 &&
						`${overrides.practices} ${overrides.practices === 1 ? "practice" : "practices"}`}
					{overrides.practices > 0 && overrides.areas > 0 && " and "}
					{overrides.areas > 0 && `${overrides.areas} ${overrides.areas === 1 ? "area" : "areas"}`}
					{" set by hand"}
				</p>
			)}
		</div>
	);
}

/**
 * Moving an area's worth in one action, which is the task the screen exists for.
 *
 * <p>A menu rather than a ladder here: the selection holds practices at different tiers, so there is no
 * current value for a segmented control to show, and a control that displayed one would be lying about
 * three quarters of the rows it is about to change.
 */
function BulkActionBar({
	count,
	bulk,
	onSet,
	onClear,
}: {
	count: number;
	bulk: { done: number; total: number } | null;
	onSet: (tier: ReviewTier | null) => void;
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
			{/* Welded, because they are one decision taken two ways — act on the selection, or drop it.
			    Two free-floating buttons of different variants at slightly different widths were the
			    ragged right edge of this strip. */}
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
							{/* The label has to live inside a group: it renders Base UI's `Menu.GroupLabel`,
							    which needs a `Menu.Group` above it and takes the whole popup down without one. */}
							<DropdownMenuGroup>
								<DropdownMenuLabel>Set every selected practice to</DropdownMenuLabel>
								{REVIEW_TIER_ORDER.map((tier) => (
									<DropdownMenuItem key={tier} onClick={() => onSet(tier)}>
										{REVIEW_TIER_LABELS[tier]}
									</DropdownMenuItem>
								))}
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
	onSetAreaTier,
	onClearAreaTier,
	onSetPracticeTier,
	onClearPracticeTier,
}: {
	workspaceSlug: string;
	group: AutonomyGroup;
	pending: ReviewAutonomyPendingState;
	selected: ReadonlySet<string>;
	onToggle: (slug: string, checked: boolean) => void;
	onSelectMany: (slugs: string[], checked: boolean) => void;
	onSetAreaTier: (areaSlug: string, tier: ReviewTier) => void;
	onClearAreaTier: (areaSlug: string) => void;
	onSetPracticeTier: (practiceSlug: string, tier: ReviewTier) => void;
	onClearPracticeTier: (practiceSlug: string) => void;
}) {
	const areaSlug = group.areaSlug;
	const areaPending = areaSlug !== null && pending.areaSlugs.has(areaSlug);
	const selectableSlugs = group.practices
		.filter((practice) => reviewableByHephaestus(practice.automatedReviewPolicy))
		.map((practice) => practice.slug);
	const allSelected =
		selectableSlugs.length > 0 && selectableSlugs.every((slug) => selected.has(slug));

	return (
		// `scroll-mt-24` clears the sticky summary. Tabbing to an area's trigger scrolls it into view, and
		// `scroll-margin` is what that scroll respects; without it the heading a keyboard user just moved
		// to landed under the strip — measured at 320px, the strip covers y 0–93.
		<AccordionItem value={group.key} className="scroll-mt-24 px-3 last:border-b-0">
			{/* A grid, not a flex row. `AccordionTrigger` wraps its button in a `Header` with a hardcoded
			    `className="flex"`, so the `flex-1` handed to the trigger landed on the button *inside* a
			    content-sized header and never widened it — which is why every area's ladder started at a
			    different x. A grid track sizes the header from outside, so it cannot be opted out of. */}
			<div className={cn("grid gap-2 py-1 sm:items-center sm:gap-4", DECISION_GRID)}>
				<AccordionTrigger className="min-w-0 flex-1">
					<span className="flex min-w-0 flex-col gap-1">
						<span className="flex flex-wrap items-center gap-2">
							<span className="break-words">{group.name}</span>
							{group.overriddenCount > 0 && (
								<Badge variant="outline" className="font-normal">
									{group.overriddenCount} set by hand
								</Badge>
							)}
						</span>
						<span className="font-normal text-muted-foreground text-xs">
							{tierDistributionSentence(group.counts)}
						</span>
					</span>
				</AccordionTrigger>
				{areaSlug === null ? (
					// The no-area bucket is not a row in any table, so it has nothing to hold a decision.
					// Its practices inherit the workspace default directly.
					<span className={cn("min-w-0 text-muted-foreground text-xs", DECISION_COLUMN)}>
						Follows the workspace default
					</span>
				) : (
					<div className={cn("min-w-0 space-y-1", DECISION_COLUMN)}>
						<ReviewTierLadder
							label={`How far Hephaestus may go in ${group.name}`}
							value={group.reviewTier.effective}
							muted={!isOverridden(group.reviewTier)}
							disabled={areaPending}
							onChange={(tier) => onSetAreaTier(areaSlug, tier)}
						/>
						<InheritanceNote
							assignment={group.reviewTier}
							inheritedFrom="the workspace default"
							name={group.name}
							disabled={areaPending}
							onClear={() => onClearAreaTier(areaSlug)}
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
						{/* Sits in the first grid track, so it starts on the same left edge as the practice
						    names it acts on rather than floating loose above the list. */}
						{selectableSlugs.length > 0 && (
							<div className={cn("grid gap-2 sm:gap-4", DECISION_GRID)}>
								<Button
									variant="link"
									size="sm"
									className="h-auto w-fit p-0 text-xs"
									aria-label={`${allSelected ? "Deselect" : "Select"} all ${selectableSlugs.length} practices in ${group.name}`}
									onClick={() => onSelectMany(selectableSlugs, !allSelected)}
								>
									{allSelected ? "Deselect" : "Select"} all {selectableSlugs.length}
								</Button>
							</div>
						)}
						{/* Ruled, not boxed. A bordered card here drew a second box inside the area's own, and
						    its border plus the rows' padding inset the practice ladders 14px from the area
						    ladder directly above them — near enough to look like a mistake rather than a
						    nesting. Rules separate the rows and the decision column runs straight. */}
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
									onSetTier={onSetPracticeTier}
									onClearTier={onClearPracticeTier}
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
	onSetTier,
	onClearTier,
}: {
	workspaceSlug: string;
	practice: Practice;
	areaName: string | null;
	pending: boolean;
	selected: boolean;
	onToggle: (slug: string, checked: boolean) => void;
	onSetTier: (practiceSlug: string, tier: ReviewTier) => void;
	onClearTier: (practiceSlug: string) => void;
}) {
	const reviewable = reviewableByHephaestus(practice.automatedReviewPolicy);
	const limitation = automatedReviewLimitationLabel(practice.automatedReviewPolicy.automatedReview);

	return (
		<Item
			render={<li />}
			variant="default"
			// `px-0`: the row's grid tracks have to be the area header's tracks, or the two ladders do
			// not share a column. Vertical padding stays — the rhythm is the row's, not the list's.
			className={cn("grid items-start gap-2 rounded-none px-0 py-3 sm:gap-4", DECISION_GRID)}
		>
			<div className="flex min-w-0 items-start gap-3">
				{/* `mt-0.5` puts the box on the first line's cap height rather than its box top, which is
				    where a reader expects a control that belongs to the title beside it. */}
				<Checkbox
					checked={selected}
					disabled={!reviewable}
					aria-label={`Select ${practice.name}`}
					className="mt-0.5 shrink-0"
					onCheckedChange={(checked) => onToggle(practice.slug, checked === true)}
				/>
				<ItemContent className="min-w-0 gap-0.5">
					<ItemTitle className="w-full min-w-0 line-clamp-none">
						<Link
							to="/w/$workspaceSlug/admin/practices/$practiceSlug"
							params={{ workspaceSlug, practiceSlug: practice.slug }}
							className="break-words rounded-sm font-normal hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
						>
							{practice.name}
						</Link>
					</ItemTitle>
					{/* The kind of work, because it is the one fact that changes what this decision costs:
					    Deliver on a pull-request practice writes on every PR the team opens, and Deliver on
					    a document practice may never fire at all. Same vocabulary as the practice catalogue,
					    so the two screens name a kind the same way. */}
					<ItemDescription className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
						<span>{artifactKindLabel(practice.artifactKind)}</span>
						{limitation && (
							<Badge variant="warning" className="font-normal">
								{limitation}
							</Badge>
						)}
					</ItemDescription>
					{/* Why the practice exists, in the catalogue's own words. An admin setting autonomy on a
					    name like "Scope the change to one concern" cannot tell from the name what Hephaestus
					    would be saying to their team; this is the sentence that tells them, and it is the
					    only human-written prose a practice carries. Clamped to two lines: unbounded it makes
					    a hundred-row list unscannable, and a prior pass tripped axe's
					    `scrollable-region-focusable` on exactly this kind of overlong description. */}
					{practice.whyItMatters && (
						// `max-w-prose` because the first track is half a 1440px screen: unconstrained, this
						// sentence set 105 characters to the line, well past the measure anyone reads
						// comfortably. `mt-1` makes the step down from the meta line deliberate rather than
						// whatever the two line-heights happened to leave.
						<ItemDescription className="mt-1 max-w-prose text-xs">
							{practice.whyItMatters}
						</ItemDescription>
					)}
				</ItemContent>
			</div>
			<ItemActions className={cn("min-w-0 flex-col items-stretch gap-1", DECISION_COLUMN)}>
				<ReviewTierLadder
					label={`How far Hephaestus may go on ${practice.name}`}
					value={practice.reviewTier.effective}
					muted={!isOverridden(practice.reviewTier)}
					disabled={pending || !reviewable}
					onChange={(tier) => onSetTier(practice.slug, tier)}
				/>
				{reviewable ? (
					<InheritanceNote
						assignment={practice.reviewTier}
						inheritedFrom={areaName ? `${areaName}` : "the workspace default"}
						name={practice.name}
						disabled={pending}
						onClear={() => onClearTier(practice.slug)}
					/>
				) : (
					<p className="text-muted-foreground text-xs">
						Hephaestus can't review this practice, so it stays off.
					</p>
				)}
			</ItemActions>
		</Item>
	);
}

/**
 * Inherited or set here, and the way back.
 *
 * <p>The source is named rather than badged with the level's name: "Deliver, from Code review" tells an
 * admin where to go and change it once, which "AREA" does not. The reset repeats the wording the rest of
 * the review settings already use, so the two screens do not teach two idioms for the same act.
 */
function InheritanceNote({
	assignment,
	inheritedFrom,
	name,
	disabled,
	onClear,
}: {
	assignment: ReviewTierAssignment;
	inheritedFrom: string;
	name: string;
	disabled: boolean;
	onClear: () => void;
}) {
	const follows = inheritedTierSourceSentence(assignment, inheritedFrom);
	if (follows) {
		return <p className="text-muted-foreground text-xs">{follows}</p>;
	}

	return (
		<div className="flex flex-wrap items-center gap-2 text-left">
			<span className="text-foreground text-xs">Set here</span>
			<Button
				variant="link"
				size="sm"
				className="h-auto p-0 text-xs"
				aria-label={`Use the default for ${name}`}
				disabled={disabled}
				onClick={onClear}
			>
				Use the default
			</Button>
		</div>
	);
}
