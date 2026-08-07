import { AlertCircle, History } from "lucide-react";
import { useState } from "react";
import type { CreateReviewBackfillRunRequest, ReviewBackfillRun } from "@/api/types.gen";
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
import { Progress } from "@/components/ui/progress";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { ARTIFACT_KIND, artifactKindLabel, artifactKindPluralLabel } from "@/lib/artifact-kinds";
import { asDate } from "@/lib/dates";
import { formatCostUsd } from "@/lib/money";

export interface PracticeReviewBackfillProps {
	runs: ReviewBackfillRun[];
	isLoading: boolean;
	isError: boolean;
	onRetry: () => void;
	isEstimating: boolean;
	onEstimate: (request: CreateReviewBackfillRunRequest) => void;
	isUpdating: boolean;
	onConfirm: (runId: string) => void;
	onCancel: (runId: string) => void;
}

/**
 * The kinds a backfill can walk. Not every artifact kind qualifies — a backfill re-reads work as it
 * stands today, which only makes sense where the work is still there to be re-read — so this is a
 * chosen subset of {@link ARTIFACT_KIND} rather than every kind the instance knows. The words for
 * each kind come from the shared vocabulary, so a workspace on GitLab is not told about "pull
 * requests" here while every other screen calls them merge requests.
 */
const WORK_KINDS = [ARTIFACT_KIND.pullRequest, ARTIFACT_KIND.issue] as const;

// `items` is what lets the closed trigger show a choice's words. Without it Base UI has nothing to
// resolve the selected value against and prints the value itself — "scm.pull_request", not "Pull or
// merge requests".
const WORK_KIND_ITEMS = WORK_KINDS.map((kind) => ({
	value: kind as string,
	label: artifactKindPluralLabel(kind),
}));

const WINDOWS = [
	{ value: "7", label: "The last 7 days" },
	{ value: "30", label: "The last 30 days" },
	{ value: "90", label: "The last 90 days" },
	{ value: "180", label: "The last 180 days" },
] as const;

const PAUSE_EXPLANATIONS: Record<NonNullable<ReviewBackfillRun["pauseReason"]>, string> = {
	BUDGET_EXHAUSTED:
		"The monthly AI budget funding these reviews is used up. Nothing has been skipped — the backfill continues from where it stopped once the budget resets or the cap is raised.",
	BINDING_DISABLED:
		"There is no enabled review model for this workspace. Nothing has been skipped — the backfill continues once a model is bound.",
	WORKSPACE_UNAVAILABLE:
		"Practice reviews are off for this workspace, or the workspace is not active. Nothing has been skipped — the backfill continues once they are back on.",
};

/** The kind as it reads mid-sentence: "each pull or merge request", "each issue". */
const nounFor = (artifactKind: string) => artifactKindLabel(artifactKind).toLowerCase();

/** "1 issue", "128 pull or merge requests" — the plural is the vocabulary's, not an appended "s". */
const countOf = (count: number, artifactKind: string) =>
	`${count} ${(count === 1 ? artifactKindLabel(artifactKind) : artifactKindPluralLabel(artifactKind)).toLowerCase()}`;

// `fromAt`/`toAt` are typed `Date` and arrive as ISO strings, so they are read through `asDate`.
const formatWindow = (run: ReviewBackfillRun) => {
	const from = asDate(run.fromAt);
	const to = asDate(run.toAt);
	if (!from || !to) return "Dates unavailable";
	return `${from.toLocaleDateString()} – ${to.toLocaleDateString()}`;
};

/**
 * Reviewing work that already existed, as a decision rather than a switch.
 *
 * <p>The screen is deliberately two steps. Choosing a range only produces an estimate; a second,
 * explicitly-worded confirmation is what starts spending. A backfill is the one action here that can
 * consume a workspace's whole monthly AI budget from a single click, so the count and the cost are on
 * screen before anything is submitted.
 */
export function PracticeReviewBackfill({
	runs,
	isLoading,
	isError,
	onRetry,
	isEstimating,
	onEstimate,
	isUpdating,
	onConfirm,
	onCancel,
}: PracticeReviewBackfillProps) {
	const pending = runs.find((run) => run.status === "AWAITING_CONFIRMATION");
	const active = runs.find((run) => run.status === "RUNNING" || run.status === "PAUSED");
	const history = runs.filter((run) => run.status === "COMPLETED" || run.status === "CANCELLED");

	return (
		<div className="space-y-6">
			{isError ? (
				<Alert variant="destructive">
					<AlertCircle />
					<AlertTitle>Backfills couldn't be loaded</AlertTitle>
					<AlertDescription>
						<Button variant="outline" size="sm" onClick={onRetry}>
							Try again
						</Button>
					</AlertDescription>
				</Alert>
			) : null}

			{active ? (
				<ActiveRunCard run={active} isUpdating={isUpdating} onCancel={onCancel} />
			) : pending ? (
				<ConfirmationCard
					run={pending}
					isUpdating={isUpdating}
					onConfirm={onConfirm}
					onCancel={onCancel}
				/>
			) : (
				<EstimateCard isLoading={isLoading} isEstimating={isEstimating} onEstimate={onEstimate} />
			)}

			<HistoryCard runs={history} isLoading={isLoading} />
		</div>
	);
}

function EstimateCard({
	isLoading,
	isEstimating,
	onEstimate,
}: {
	isLoading: boolean;
	isEstimating: boolean;
	onEstimate: (request: CreateReviewBackfillRunRequest) => void;
}) {
	const [artifactKind, setArtifactKind] =
		useState<CreateReviewBackfillRunRequest["artifactKind"]>("scm.pull_request");
	const [days, setDays] = useState("30");

	const estimate = () => {
		const toAt = new Date();
		const fromAt = new Date(toAt.getTime() - Number(days) * 24 * 60 * 60 * 1000);
		onEstimate({ artifactKind, fromAt, toAt });
	};

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Review past work</h2>
				</CardTitle>
				<CardDescription>
					Reviews normally start when work happens, so anything from before this workspace was set
					up has never been measured. A backfill measures it once, as it stands today.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="backfill-kind">Kind of work</FieldLabel>
						<FieldDescription>
							One kind per backfill, so the estimate means one thing.
						</FieldDescription>
					</FieldContent>
					<Select
						items={WORK_KIND_ITEMS}
						value={artifactKind}
						onValueChange={(value) => {
							if (value) {
								setArtifactKind(value as CreateReviewBackfillRunRequest["artifactKind"]);
							}
						}}
					>
						<SelectTrigger id="backfill-kind" className="w-56">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							{WORK_KIND_ITEMS.map((kind) => (
								<SelectItem key={kind.value} value={kind.value}>
									{kind.label}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</Field>

				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="backfill-window">How far back</FieldLabel>
						<FieldDescription>Counted from when the work was opened.</FieldDescription>
					</FieldContent>
					<Select items={WINDOWS} value={days} onValueChange={(value) => setDays(value ?? days)}>
						<SelectTrigger id="backfill-window" className="w-56">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							{WINDOWS.map((window) => (
								<SelectItem key={window.value} value={window.value}>
									{window.label}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</Field>

				<div className="flex items-center gap-3">
					<Button onClick={estimate} disabled={isEstimating || isLoading}>
						{isEstimating ? <Spinner /> : null}
						Estimate this backfill
					</Button>
					<p className="text-muted-foreground text-sm">Nothing is reviewed until you confirm.</p>
				</div>
			</CardContent>
		</Card>
	);
}

function ConfirmationCard({
	run,
	isUpdating,
	onConfirm,
	onCancel,
}: {
	run: ReviewBackfillRun;
	isUpdating: boolean;
	onConfirm: (runId: string) => void;
	onCancel: (runId: string) => void;
}) {
	const noun = nounFor(run.artifactKind);
	const plural = artifactKindPluralLabel(run.artifactKind).toLowerCase();
	// `formatCostUsd` renders a fraction of a cent as "<$0.01" rather than rounding it to "$0.00";
	// this is the one screen where a rounded-to-nothing forecast would invite the unconsidered spend
	// it exists to prevent. An absent estimate is a different statement and keeps its own copy.
	const cost = run.estimatedCostUsd === undefined ? undefined : formatCostUsd(run.estimatedCostUsd);
	const nothingToDo = run.estimatedArtifacts === 0;

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Confirm this backfill</h2>
				</CardTitle>
				<CardDescription>{formatWindow(run)}</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<div className="grid gap-4 sm:grid-cols-2">
					<div>
						<p className="text-muted-foreground text-sm">Work to review</p>
						<p className="font-semibold text-2xl">
							{countOf(run.estimatedArtifacts, run.artifactKind)}
						</p>
					</div>
					<div>
						<p className="text-muted-foreground text-sm">Estimated AI spend</p>
						<p className="font-semibold text-2xl">{cost ?? "Unknown"}</p>
						{cost ? null : (
							<p className="text-muted-foreground text-sm">
								This workspace has no priced reviews yet, so there is nothing to base an estimate
								on.
							</p>
						)}
					</div>
				</div>

				<Alert>
					<AlertCircle />
					<AlertTitle>What a backfill does and does not do</AlertTitle>
					<AlertDescription>
						Each {noun} is measured once, as it stands now — there is no record of how it looked
						while it was being worked on. Nothing is posted on the work itself and nobody is
						notified: commenting on {plural} that are already finished would notify everyone
						involved about work nobody can act on. The measurements are kept separate from your live
						trends, because older work has been polished since and comparing the two would invent an
						improvement nobody made.
					</AlertDescription>
				</Alert>

				<div className="flex flex-wrap items-center gap-3">
					<Button
						onClick={() => onConfirm(run.id)}
						disabled={isUpdating || nothingToDo}
						aria-disabled={isUpdating || nothingToDo}
					>
						{isUpdating ? <Spinner /> : null}
						{nothingToDo
							? "Nothing in range"
							: `Review ${countOf(run.estimatedArtifacts, run.artifactKind)}`}
					</Button>
					<Button variant="outline" onClick={() => onCancel(run.id)} disabled={isUpdating}>
						Discard
					</Button>
				</div>
			</CardContent>
		</Card>
	);
}

function ActiveRunCard({
	run,
	isUpdating,
	onCancel,
}: {
	run: ReviewBackfillRun;
	isUpdating: boolean;
	onCancel: (runId: string) => void;
}) {
	const walked = run.submittedCount + run.passedCount;
	const total = Math.max(run.estimatedArtifacts, walked);
	const percent = total === 0 ? 100 : Math.round((walked / total) * 100);

	return (
		<Card>
			<CardHeader>
				<CardTitle className="flex items-center gap-2">
					<h2>Backfill in progress</h2>
					<Badge variant={run.status === "PAUSED" ? "outline" : "secondary"}>
						{run.status === "PAUSED" ? "Paused" : "Running"}
					</Badge>
				</CardTitle>
				<CardDescription>{formatWindow(run)}</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<div className="space-y-2">
					<Progress value={percent} aria-label="Backfill progress" />
					<p className="text-muted-foreground text-sm">
						{walked} of {countOf(total, run.artifactKind)} looked at — {run.submittedCount} sent for
						review, {run.passedCount} already measured or outside your review rules.
					</p>
				</div>

				{run.status === "PAUSED" && run.pauseReason ? (
					<Alert>
						<AlertCircle />
						<AlertTitle>Paused, not skipping</AlertTitle>
						<AlertDescription>{PAUSE_EXPLANATIONS[run.pauseReason]}</AlertDescription>
					</Alert>
				) : null}

				<Button variant="outline" onClick={() => onCancel(run.id)} disabled={isUpdating}>
					{isUpdating ? <Spinner /> : null}
					Stop this backfill
				</Button>
			</CardContent>
		</Card>
	);
}

function HistoryCard({ runs, isLoading }: { runs: ReviewBackfillRun[]; isLoading: boolean }) {
	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Past backfills</h2>
				</CardTitle>
				<CardDescription>What has already been measured, and by whose decision.</CardDescription>
			</CardHeader>
			<CardContent>
				{isLoading ? (
					<div className="flex justify-center py-6">
						<Spinner />
					</div>
				) : runs.length === 0 ? (
					<Empty>
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<History />
							</EmptyMedia>
							<EmptyTitle>No backfills yet</EmptyTitle>
							<EmptyDescription>
								Past work has never been measured in this workspace.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<div className="space-y-2">
						{runs.map((run) => (
							<Item key={run.id} variant="outline">
								<ItemContent>
									<ItemTitle>
										{artifactKindPluralLabel(run.artifactKind)}
										{": "}
										{formatWindow(run)}
									</ItemTitle>
									<ItemDescription>
										{run.status === "CANCELLED"
											? `Stopped after reviewing ${countOf(run.submittedCount, run.artifactKind)}.`
											: `Reviewed ${countOf(run.submittedCount, run.artifactKind)}; ${run.passedCount} needed no new measurement.`}
									</ItemDescription>
								</ItemContent>
								<ItemActions>
									<Badge variant={run.status === "CANCELLED" ? "outline" : "secondary"}>
										{run.status === "CANCELLED" ? "Stopped" : "Finished"}
									</Badge>
								</ItemActions>
							</Item>
						))}
					</div>
				)}
			</CardContent>
		</Card>
	);
}
