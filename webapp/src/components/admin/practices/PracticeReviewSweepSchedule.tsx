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
 * <p>The ceiling is not a UI nicety. A sweep's findings are counted alongside reviews that the work
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

// `nextRunAt`/`lastRunAt` are typed `Date` but arrive as ISO strings.
const formatMoment = (value: Date | undefined) => {
	const date = asDate(value);
	return date ? date.toLocaleString() : undefined;
};

/**
 * Keeping new work reviewed even when nothing announced it.
 *
 * <p>Separate from the backfill card above it because the two answer different questions. A backfill
 * measures history once, on purpose, and its findings are kept out of the live trend. A sweep is the
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
								Work that never raised a notification is never reviewed, and nothing says so.
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
	const nextRun = formatMoment(schedule.nextRunAt);
	const lastRun = formatMoment(schedule.lastRunAt);

	return (
		<Item variant="outline">
			<ItemContent>
				<ItemTitle>{artifactKindPluralLabel(schedule.artifactKind)}</ItemTitle>
				<ItemDescription>
					{describeCadence(schedule)}.{" "}
					{schedule.enabled
						? nextRun
							? `Next check ${nextRun}.`
							: ""
						: "Paused — nothing is being checked."}{" "}
					{lastRun
						? `Last checked ${lastRun}.`
						: "It has not checked anything yet; the first run happens within the hour."}
				</ItemDescription>
			</ItemContent>
			<ItemActions>
				<Badge variant={schedule.enabled ? "secondary" : "outline"}>
					{schedule.enabled ? "On" : "Paused"}
				</Badge>
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
				</Button>
				<Button variant="ghost" size="sm" disabled={isSaving} onClick={() => onDelete(schedule.id)}>
					Remove
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

	return (
		<div className="space-y-4 border-t pt-4">
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
					<FieldDescription>Checked in the background; nothing to run by hand.</FieldDescription>
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
					Start checking
				</Button>
				<p className="text-muted-foreground text-sm">
					This authorises the AI spend for every future check, not just the first.
				</p>
			</div>
		</div>
	);
}
