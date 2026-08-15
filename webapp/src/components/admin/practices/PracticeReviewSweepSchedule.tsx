import { format } from "date-fns";
import { AlertCircle, CalendarClock } from "lucide-react";
import { useState } from "react";
import type {
	CreateReviewSweepScheduleRequest,
	ReviewSweepSchedule,
	UpdateReviewSweepScheduleRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Item, ItemActions, ItemContent, ItemDescription, ItemTitle } from "@/components/ui/item";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { ARTIFACT_KIND, artifactKindPluralLabel } from "@/lib/artifact-kinds";
import { asDate } from "@/lib/dates";

export interface PracticeReviewSweepScheduleProps {
	schedules: ReviewSweepSchedule[];
	isLoading: boolean;
	isError: boolean;
	onRetry: () => void;
	isSaving: boolean;
	onCreate: (request: CreateReviewSweepScheduleRequest) => void;
	onReplace: (scheduleId: string, request: UpdateReviewSweepScheduleRequest) => void;
	onDelete: (scheduleId: string) => void;
}

/** The kinds a campaign can enumerate, which is what a sweep opens one of. */
const WORK_KINDS = [ARTIFACT_KIND.pullRequest, ARTIFACT_KIND.issue] as const;

const WORK_KIND_ITEMS = WORK_KINDS.map((kind) => ({
	value: kind as string,
	label: artifactKindPluralLabel(kind),
}));

const CADENCES = [
	{ value: "DAILY", label: "Every day" },
	{ value: "WEEKLY", label: "Every week" },
] as const;

type Cadence = (typeof CADENCES)[number]["value"];

/**
 * At most twice the cadence, never more than a week — the same ceiling the server enforces, offered
 * here so an admin picks from what is allowed instead of discovering the rule through a 400.
 *
 * <p>The ceiling is not a UI nicety. A sweep's observations are counted alongside reviews that the work
 * itself triggered, and that only holds while its window is "the last few days". Anything longer is a
 * stretch of history somebody chose, which is what the separate "review past work" backfill is for.
 */
const LOOKBACK_CEILING: Record<Cadence, number> = { DAILY: 2, WEEKLY: 7 };

const lookbackItems = (cadence: Cadence) =>
	Array.from({ length: LOOKBACK_CEILING[cadence] }, (_, index) => {
		const days = index + 1;
		return { value: String(days), label: days === 1 ? "The last day" : `The last ${days} days` };
	});

const describeCadence = (schedule: ReviewSweepSchedule) => {
	const every = schedule.cadence === "DAILY" ? "Every day" : "Every week";
	const window =
		schedule.lookbackDays === 1 ? "the last day" : `the last ${schedule.lookbackDays} days`;
	return `${every}, covering ${window}`;
};

/**
 * A moment as it reads mid-sentence: "Next check 15 Aug 2026, 03:00."
 *
 * `nextRunAt`/`lastRunAt` are typed `Date` but arrive as ISO strings, so they go through `asDate`.
 * The sentence is why `RelativeTime` does not fit: it renders a tooltip trigger, and this is one of
 * three clauses joined into a plain description.
 */
const formatMoment = (value: Date | undefined) => {
	const date = asDate(value);
	return date ? format(date, "d MMM yyyy, HH:mm") : undefined;
};

/**
 * Keeping new work reviewed even when nothing announced it.
 *
 * <p>Separate from the backfill card above it because the two answer different questions. A backfill
 * measures history once, on purpose, and its observations are kept out of the live trend. A sweep is the
 * standing safety net for work a missed webhook never told us about, and because its window is bounded
 * to the last few days, what it finds counts exactly like anything the work itself triggered.
 */
export function PracticeReviewSweepSchedule({
	schedules,
	isLoading,
	isError,
	onRetry,
	isSaving,
	onCreate,
	onReplace,
	onDelete,
}: PracticeReviewSweepScheduleProps) {
	const scheduledKinds = new Set(schedules.map((schedule) => schedule.artifactKind));
	const availableKinds = WORK_KIND_ITEMS.filter((kind) => !scheduledKinds.has(kind.value));

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Keep checking new work</h2>
				</CardTitle>
				<CardDescription>
					Reviews normally start the moment work happens. When a notification is lost, nothing ever
					arrives — and there is no record of the review that did not happen. A recurring check
					looks again over the last few days, so anything missed still gets reviewed.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				{isError ? (
					<Alert variant="destructive">
						<AlertCircle />
						<AlertTitle>Recurring checks couldn't be loaded</AlertTitle>
						<AlertDescription>
							{/* Says what did not happen, not what broke: any check already scheduled is still
							    running, and an admin reading only the title would set a second one up. */}
							<p>Whatever is scheduled is still running — this is only about showing it here.</p>
							<Button variant="outline" size="sm" onClick={onRetry}>
								Try again
							</Button>
						</AlertDescription>
					</Alert>
				) : null}

				{isLoading ? (
					<div className="flex justify-center py-6">
						<Spinner />
					</div>
				) : schedules.length === 0 ? (
					<Empty>
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<CalendarClock />
							</EmptyMedia>
							<EmptyTitle>Nothing is checked on a schedule</EmptyTitle>
							<EmptyDescription>
								Work that never raised a notification is never reviewed, and nothing says so. Add a
								check below and Hephaestus looks over the last few days again.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<div className="space-y-2">
						{schedules.map((schedule) => (
							<ScheduleRow
								key={schedule.id}
								schedule={schedule}
								isSaving={isSaving}
								onReplace={onReplace}
								onDelete={onDelete}
							/>
						))}
					</div>
				)}

				{availableKinds.length > 0 ? (
					<AddScheduleForm
						availableKinds={availableKinds}
						isSaving={isSaving}
						isLoading={isLoading}
						onCreate={onCreate}
					/>
				) : null}
			</CardContent>
		</Card>
	);
}

function ScheduleRow({
	schedule,
	isSaving,
	onReplace,
	onDelete,
}: {
	schedule: ReviewSweepSchedule;
	isSaving: boolean;
	onReplace: (scheduleId: string, request: UpdateReviewSweepScheduleRequest) => void;
	onDelete: (scheduleId: string) => void;
}) {
	const kind = artifactKindPluralLabel(schedule.artifactKind);
	const nextRun = formatMoment(schedule.nextRunAt);
	const lastRun = formatMoment(schedule.lastRunAt);

	// Joined rather than concatenated: an enabled schedule with no next run printed a double space, and
	// a paused one that had never run promised a first run "within the hour" while nothing was running.
	const description = [
		`${describeCadence(schedule)}.`,
		schedule.enabled
			? nextRun
				? `Next check ${nextRun}.`
				: "The first check happens within the hour."
			: "Paused, so nothing is being checked.",
		lastRun ? `Last checked ${lastRun}.` : "It has not checked anything yet.",
	].join(" ");

	return (
		<Item variant="outline">
			<ItemContent>
				<ItemTitle>{kind}</ItemTitle>
				<ItemDescription>{description}</ItemDescription>
			</ItemContent>
			<ItemActions>
				<Badge variant={schedule.enabled ? "secondary" : "outline"}>
					{schedule.enabled ? "On" : "Paused"}
				</Badge>
				{/* One row per kind, so every row would otherwise answer to the same three words. The visible
				    word still opens the name a voice-control user says (WCAG 2.2 SC 2.5.3). */}
				<Button
					variant="outline"
					size="sm"
					disabled={isSaving}
					onClick={() =>
						onReplace(schedule.id, {
							cadence: schedule.cadence,
							lookbackDays: schedule.lookbackDays,
							enabled: !schedule.enabled,
						})
					}
				>
					{schedule.enabled ? "Pause" : "Resume"}
					<span className="sr-only"> checking {kind.toLowerCase()}</span>
				</Button>
				<Button variant="ghost" size="sm" disabled={isSaving} onClick={() => onDelete(schedule.id)}>
					Remove
					<span className="sr-only"> the recurring check on {kind.toLowerCase()}</span>
				</Button>
			</ItemActions>
		</Item>
	);
}

function AddScheduleForm({
	availableKinds,
	isSaving,
	isLoading,
	onCreate,
}: {
	availableKinds: { value: string; label: string }[];
	isSaving: boolean;
	isLoading: boolean;
	onCreate: (request: CreateReviewSweepScheduleRequest) => void;
}) {
	const firstKind = availableKinds[0]?.value ?? ARTIFACT_KIND.pullRequest;
	const [artifactKind, setArtifactKind] = useState(firstKind);
	const [cadence, setCadence] = useState<Cadence>("DAILY");
	const [lookbackDays, setLookbackDays] = useState("2");

	// Switching to a tighter cadence can invalidate the chosen window, so the cadence owns it: pick the
	// widest the new cadence allows rather than leaving a value the server would refuse.
	const changeCadence = (next: Cadence) => {
		setCadence(next);
		setLookbackDays(String(LOOKBACK_CEILING[next]));
	};

	const kindItems = availableKinds;
	const windowItems = lookbackItems(cadence);
	const chosenKind = (
		availableKinds.find((kind) => kind.value === artifactKind)?.label ??
		artifactKindPluralLabel(artifactKind)
	).toLowerCase();

	return (
		// A named group rather than three loose controls after a list: without it a screen-reader user
		// arrives at a second "Kind of work" with nothing saying they have left the schedules behind.
		<section className="space-y-4 border-t pt-4" aria-labelledby="sweep-add-heading">
			<h3 id="sweep-add-heading" className="font-medium text-sm">
				Add a recurring check
			</h3>
			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="sweep-kind">Kind of work</FieldLabel>
					<FieldDescription>One schedule per kind.</FieldDescription>
				</FieldContent>
				<Select
					items={kindItems}
					value={artifactKind}
					onValueChange={(value) => setArtifactKind(value ?? artifactKind)}
				>
					<SelectTrigger id="sweep-kind" className="w-56">
						<SelectValue />
					</SelectTrigger>
					<SelectContent>
						{kindItems.map((kind) => (
							<SelectItem key={kind.value} value={kind.value}>
								{kind.label}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</Field>

			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="sweep-cadence">How often</FieldLabel>
					<FieldDescription>Runs on its own; there is nothing to start by hand.</FieldDescription>
				</FieldContent>
				<Select
					items={CADENCES}
					value={cadence}
					onValueChange={(value) => changeCadence((value as Cadence) ?? cadence)}
				>
					<SelectTrigger id="sweep-cadence" className="w-56">
						<SelectValue />
					</SelectTrigger>
					<SelectContent>
						{CADENCES.map((option) => (
							<SelectItem key={option.value} value={option.value}>
								{option.label}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</Field>

			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="sweep-window">How far back each time</FieldLabel>
					<FieldDescription>
						Overlapping on purpose: work missed once still gets a second chance, and work already
						reviewed is never paid for twice.
					</FieldDescription>
				</FieldContent>
				<Select
					items={windowItems}
					value={lookbackDays}
					onValueChange={(value) => setLookbackDays(value ?? lookbackDays)}
				>
					<SelectTrigger id="sweep-window" className="w-56">
						<SelectValue />
					</SelectTrigger>
					<SelectContent>
						{windowItems.map((option) => (
							<SelectItem key={option.value} value={option.value}>
								{option.label}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</Field>

			<div className="flex flex-wrap items-center gap-3">
				{/* The button names the work it commits to, not just the verb: this is the one control on
				    the page that authorises spending again and again without being asked. */}
				<Button
					disabled={isSaving || isLoading}
					onClick={() =>
						onCreate({
							artifactKind: artifactKind as CreateReviewSweepScheduleRequest["artifactKind"],
							cadence,
							lookbackDays: Number(lookbackDays),
						})
					}
				>
					{isSaving ? <Spinner /> : null}
					Start checking {chosenKind}
				</Button>
				<p className="text-muted-foreground text-sm">
					Every check can start reviews, so this authorises the AI spend for all of them — not just
					the first.
				</p>
			</div>
		</section>
	);
}
