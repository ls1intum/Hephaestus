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
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import {
	FEEDBACK_REACH_DESCRIPTIONS,
	FEEDBACK_REACH_LABELS,
	FEEDBACK_REACH_ORDER,
	type FeedbackReach,
	REVIEW_TIER_LABELS,
	REVIEW_TIER_ORDER,
	REVIEW_TIER_SELECTABLE,
	type ReviewTier,
	tierDistribution,
	tierDistributionSentence,
	tierTotal,
} from "@/lib/review-tiers";
import { ReviewTierLadder } from "./ReviewTierLadder";
import {
	type AutonomyGroup,
	countOverrides,
	groupPracticesByArea,
	isOverridden,
	reviewableByHephaestus,
} from "./review-autonomy-model";

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
							    read (WCAG 2.2 SC 2.5.3). Spelled as an `aria-label` rather than an `sr-only`
							    tail: name computation trims each text node it walks, so " for …" in a second
							    node is announced welded to the word before it. */}
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
		<div
			className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/50 px-3 py-2"
			role="group"
			aria-label="Selected practices"
		>
			<p aria-live="polite" aria-atomic="true" className="mr-auto text-sm">
				{bulk
					? `Changing ${bulk.done} of ${bulk.total}…`
					: `${count} ${count === 1 ? "practice" : "practices"} selected`}
			</p>
			<DropdownMenu>
				<DropdownMenuTrigger
					render={
						<Button size="sm" variant="outline" disabled={bulk !== null || count === 0}>
							Change the selected
						</Button>
					}
				/>
				<DropdownMenuContent align="end">
					{/* The label has to live inside a group: it renders Base UI's `Menu.GroupLabel`, which
					    needs a `Menu.Group` above it and takes the whole popup down without one. */}
					<DropdownMenuGroup>
						<DropdownMenuLabel>Set every selected practice to</DropdownMenuLabel>
						{REVIEW_TIER_ORDER.filter((tier) => REVIEW_TIER_SELECTABLE[tier]).map((tier) => (
							<DropdownMenuItem key={tier} onClick={() => onSet(tier)}>
								{REVIEW_TIER_LABELS[tier]}
							</DropdownMenuItem>
						))}
					</DropdownMenuGroup>
					<DropdownMenuSeparator />
					<DropdownMenuItem onClick={() => onSet(null)}>Use the inherited setting</DropdownMenuItem>
				</DropdownMenuContent>
			</DropdownMenu>
			<Button size="sm" variant="ghost" disabled={bulk !== null} onClick={onClear}>
				Clear selection
			</Button>
		</div>
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
		<AccordionItem value={group.key} className="px-3 last:border-b-0">
			<div className="flex flex-col gap-2 py-1 sm:flex-row sm:items-center sm:gap-4">
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
					<span className="min-w-0 text-muted-foreground text-xs sm:w-80">
						Follows the workspace default
					</span>
				) : (
					<div className="min-w-0 space-y-1 sm:w-80">
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
						{selectableSlugs.length > 0 && (
							<Button
								variant="link"
								size="sm"
								className="h-auto p-0 text-xs"
								aria-label={`${allSelected ? "Deselect" : "Select"} all ${selectableSlugs.length} practices in ${group.name}`}
								onClick={() => onSelectMany(selectableSlugs, !allSelected)}
							>
								{allSelected ? "Deselect" : "Select"} all {selectableSlugs.length}
							</Button>
						)}
						<ul className="mt-2 divide-y rounded-md border">
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
		<li className="flex flex-col gap-2 p-3 sm:flex-row sm:items-center sm:gap-4">
			<div className="flex min-w-0 flex-1 items-start gap-3">
				<Checkbox
					checked={selected}
					disabled={!reviewable}
					aria-label={`Select ${practice.name}`}
					className="mt-0.5"
					onCheckedChange={(checked) => onToggle(practice.slug, checked === true)}
				/>
				<div className="min-w-0 space-y-0.5">
					<Link
						to="/w/$workspaceSlug/admin/practices/$practiceSlug"
						params={{ workspaceSlug, practiceSlug: practice.slug }}
						className="break-words rounded-sm text-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
					>
						{practice.name}
					</Link>
					{limitation && (
						<Badge variant="warning" className="ml-2 font-normal">
							{limitation}
						</Badge>
					)}
				</div>
			</div>
			<div className="min-w-0 space-y-1 sm:w-80">
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
			</div>
		</li>
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
	if (!isOverridden(assignment)) {
		return (
			<p className="text-muted-foreground text-xs">
				Follows{" "}
				{assignment.source === "WORKSPACE" && inheritedFrom !== "the workspace default"
					? "the workspace default"
					: inheritedFrom}
			</p>
		);
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
