import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type { Practice, PracticeReviewSettings, ReviewTierRollup } from "@/api/types.gen";
import { automatedReviewLimitationLabel } from "@/components/admin/practice-catalog/evidence-presentation";
import { PracticeDetailHoverCard } from "@/components/admin/practice-catalog/PracticeDetailHoverCard";
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
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
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
	tierDistributionSentence,
	WORKSPACE_DEFAULT_SOURCE,
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
 * An area's ladder and its practices' are the same control at two levels of one chain, so they have
 * to share a left edge. A stated width is what puts them there; laid out after a content-width
 * accordion header instead, an area's ladder starts at a different x under every area name.
 * `DecisionsShareOneColumn` in the stories is the assertion.
 */
const DECISION_COLUMN = "sm:w-80";

const AREA_GRID = "sm:grid-cols-[minmax(0,1fr)_20rem]";

/**
 * The decision track is a fixed 20rem at the end of this grid and of {@link AREA_GRID}, and both are
 * laid out across the same width, so the row's extra leading track for the checkbox moves where the
 * practice *name* starts and not where the ladder does.
 */
const ROW_GRID = "grid-cols-[auto_minmax(0,1fr)] sm:grid-cols-[auto_minmax(0,1fr)_20rem]";

export interface ReviewAutonomyPendingState {
	workspace: boolean;
	areaSlugs: ReadonlySet<string>;
	practiceSlugs: ReadonlySet<string>;
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
	// A selection survives the filter being switched on, so it can name rows that are now hidden.
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

			{/* No horizontal padding: at the narrowest viewport a strip wider than its parent drags the
			    whole page sideways, which `expectNoPageOverflow` in the stories is what catches. */}
			<div className="sticky top-0 z-20 space-y-3 border-b bg-background/95 py-3 backdrop-blur supports-backdrop-filter:bg-background/80">
				<div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
					<TierSummary counts={rollup.counts} overrides={overrides} />
					<ScopeFilter value={overridesOnly} onChange={onOverridesOnlyChange} />
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
					<DecisionNote
						follows={
							tierChosen
								? null
								: `Not chosen yet, so ${REVIEW_TIER_LABELS[settings.defaultReviewTier]} applies.`
						}
						resetLabel="Use the default for how far Hephaestus may go without you"
						disabled={saving}
						onClear={onClearWorkspaceDefault}
					/>
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
						{/* The dot leads rather than trails: its hit area is an `::after` box hanging past the
						    control's own edge, which drags the page sideways when it sits against the right
						    margin. */}
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
					<DecisionNote
						follows={
							reachChosen
								? null
								: `Not chosen yet, so ${FEEDBACK_REACH_LABELS[settings.feedbackReach].toLowerCase()} applies.`
						}
						resetLabel="Use the default for where feedback may go"
						disabled={saving}
						onClear={onClearFeedbackReach}
					/>
				</Field>
			</CardContent>
		</Card>
	);
}

/**
 * Read straight off the server's rollup. Counting the rendered rows instead would put a second
 * implementation of the inheritance chain here, and would answer for the rows that happen to be
 * loaded rather than for the workspace.
 */
function TierSummary({
	counts,
	overrides,
}: {
	counts: Record<string, number>;
	overrides: { practices: number; areas: number };
}) {
	return (
		<p className="min-w-0 text-muted-foreground text-sm" aria-live="polite" aria-atomic="true">
			{tierDistributionSentence(counts)} {byHandSentence(overrides)}
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

/**
 * A toggle group rather than a switch, because this control only narrows the list while every other
 * control on the screen writes a setting — and because both states are then named on screen, rather
 * than one being the unlabelled absence of the other.
 */
function ScopeFilter({ value, onChange }: { value: boolean; onChange: (next: boolean) => void }) {
	return (
		// `role="toolbar"`: `ToggleGroup` emits `aria-orientation`, which ARIA allows on toolbar but not
		// on group, and the items are `aria-pressed` buttons rather than radios.
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

/**
 * A menu rather than the ladder the rows use: a selection can hold practices at different tiers, so
 * there is no current value for a segmented control to show without misreporting most of them.
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
							    which throws without a `Menu.Group` ancestor and takes the whole popup down. */}
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
		// `scroll-mt-24` clears the sticky strip above: tabbing to an area's trigger scrolls it into view,
		// and `scroll-margin` is what that scroll respects — without it the heading a keyboard user just
		// moved to lands underneath the strip.
		<AccordionItem value={group.key} className="scroll-mt-24 rounded-lg border bg-card px-3">
			<div className={cn("grid gap-2 py-1 sm:items-center sm:gap-4", AREA_GRID)}>
				<AccordionTrigger>
					{/* Spans, not `ItemTitle`/`ItemDescription`: this is inside a `<button>`, which may only
					    contain phrasing content, and those primitives render `<div>`s. */}
					<span className="flex min-w-0 flex-col gap-1">
						<span className="flex flex-wrap items-center gap-2">
							<span className="break-words">{group.name}</span>
							{group.overriddenCount > 0 && (
								<Badge variant="outline">{group.overriddenCount} set by hand</Badge>
							)}
						</span>
						<span className="font-normal text-muted-foreground text-xs">
							{tierDistributionSentence(group.counts)}
						</span>
					</span>
				</AccordionTrigger>
				{areaSlug === null ? (
					// The no-area bucket is not an area, so there is nothing for a tier to be stored against;
					// its practices inherit the workspace default directly.
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
						<DecisionNote
							follows={inheritedTierSourceSentence(group.reviewTier, WORKSPACE_DEFAULT_SOURCE)}
							resetLabel={`Use the default for ${group.name}`}
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
						{/* Ruled, not boxed: a bordered card here would inset the practice ladders from the
						    area ladder above them, breaking the shared decision column. */}
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
			// `px-0`: the row's grid tracks have to end where the area header's tracks end, or the two
			// ladders do not share a column.
			className={cn("grid items-start gap-2 rounded-none px-0 py-3 sm:gap-4", ROW_GRID)}
		>
			<ItemMedia>
				<Checkbox
					checked={selected}
					disabled={!reviewable}
					aria-label={`Select ${practice.name}`}
					onCheckedChange={(checked) => onToggle(practice.slug, checked === true)}
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
				{/* The kind of work is what changes the cost of this decision: Deliver on a pull-request
				    practice writes on every PR the team opens, on a document practice it may never fire. */}
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
				<ReviewTierLadder
					label={`How far Hephaestus may go on ${practice.name}`}
					value={practice.reviewTier.effective}
					muted={!isOverridden(practice.reviewTier)}
					disabled={pending || !reviewable}
					onChange={(tier) => onSetTier(practice.slug, tier)}
				/>
				{reviewable ? (
					<DecisionNote
						follows={inheritedTierSourceSentence(
							practice.reviewTier,
							areaName ?? WORKSPACE_DEFAULT_SOURCE,
						)}
						resetLabel={`Use the default for ${practice.name}`}
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

export interface DecisionNoteProps {
	/**
	 * What this level follows when nobody set it here, ready to print — or null when it was set here,
	 * which is the only case that offers a way back.
	 */
	follows: string | null;
	/**
	 * The reset's accessible name. It has to open with the visible words (WCAG 2.2 SC 2.5.3) and name
	 * what it resets: the screen renders one of these per area and one per practice, so "Use the
	 * default" alone identifies none of them.
	 */
	resetLabel: string;
	disabled: boolean;
	onClear: () => void;
}

/**
 * One paragraph rather than a flex row holding a span and a button: `Field` stretches every child to
 * its full width, so a link-styled button laid out beside the sentence spans the whole card.
 */
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
