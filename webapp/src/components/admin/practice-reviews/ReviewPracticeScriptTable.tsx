import type { PracticeScriptModel, PracticeScriptRun, ReviewPractice } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { PracticeScriptFixLink } from "@/components/practice-trace/PracticeScriptFixLink";
import type { AgentPurpose } from "@/components/practice-vocabulary/agent-purpose-defs";
import { DataHandlingMark } from "@/components/practice-vocabulary/DataHandlingMark";
import { ModelKindMark } from "@/components/practice-vocabulary/ModelKindMark";
import {
	PRACTICE_SCRIPT_RUN_STATUS_DEFS,
	practiceScriptFix,
} from "@/components/practice-vocabulary/practice-script-run-status-defs";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { hasText } from "@/lib/text";

import { practiceLevel } from "./review-levels";

const count = new Intl.NumberFormat("en-GB");
const seconds = new Intl.NumberFormat("en-GB", {
	minimumFractionDigits: 1,
	maximumFractionDigits: 1,
});

/**
 * A script's run time as `4.2 s`, or `2 min 5 s` from a minute on. A run under 0.1 s does not read
 * as `0.0 s`, as if it did not run.
 */
function formatScriptTime(durationMs: number): string {
	if (durationMs < 100) {
		return "< 0.1 s";
	}
	const tenths = Math.round(durationMs / 100);
	if (tenths < 600) {
		return `${seconds.format(tenths / 10)} s`;
	}
	const total = Math.round(durationMs / 1000);
	return `${count.format(Math.floor(total / 60))} min ${total % 60} s`;
}

export interface ReviewPracticeScriptTableProps {
	workspaceSlug: string;
	/** The review's practices whose program ran, each with its run. */
	practices: ReviewPractice[];
}

/** The model a script called, as the workspace binds it. */
const SCRIPT_MODEL_PURPOSE = {
	CHAT: "PRACTICE_REVIEW",
	DECISION: "PRACTICE_DECISION",
	EMBEDDING: "PRACTICE_EMBEDDING",
	RERANKING: "PRACTICE_RERANKING",
} satisfies Record<PracticeScriptModel["kind"], AgentPurpose>;

/**
 * How each practice's program ran in one review: how it ended, which models it had, how many calls
 * the proxy counted, and how long it ran. The calls to the review's own model are the review's, and
 * spend is on AI usage, so neither is here.
 */
export function ReviewPracticeScriptTable({
	workspaceSlug,
	practices,
}: ReviewPracticeScriptTableProps) {
	// On a narrow screen, the practice and its result fit the screen's width and wrap there. The
	// figures to their right scroll into view.
	return (
		<Table bordered className="sm:min-w-160">
			<caption className="sr-only">How the program of each practice ran in this review</caption>
			<TableHeader>
				<TableRow variant="static">
					<TableHead scope="col" className="sm:min-w-40">
						Practice
					</TableHead>
					<TableHead scope="col" className="min-w-44">
						Result
					</TableHead>
					<TableHead scope="col">Models</TableHead>
					<TableHead scope="col" numeric className="text-right">
						Calls
					</TableHead>
					<TableHead scope="col" numeric className="text-right">
						Time
					</TableHead>
				</TableRow>
			</TableHeader>
			<TableBody>
				{practices.map(
					(practice) =>
						practice.script !== null && (
							<ScriptRow
								key={practice.practiceSlug}
								workspaceSlug={workspaceSlug}
								practice={practice}
								run={practice.script}
							/>
						),
				)}
			</TableBody>
		</Table>
	);
}

/**
 * One script. The result carries its fix and, for a failed script, its error, so the way out and
 * what went wrong sit together and wrap inside the result's own column.
 */
function ScriptRow({
	workspaceSlug,
	practice,
	run,
}: {
	workspaceSlug: string;
	practice: ReviewPractice;
	run: PracticeScriptRun;
}) {
	const fix = practiceScriptFix(run);
	const error = run.status === "FAILED" && hasText(run.error) ? run.error : undefined;
	const calls = run.models.reduce((sum, model) => sum + model.calls, 0);
	return (
		<TableRow variant="static">
			<TableHead scope="row" variant="body">
				<InlineLink
					className="font-medium"
					render={<DetailStackLink entry={practiceLevel(practice.practiceSlug)} />}
				>
					{practice.practiceName}
				</InlineLink>
			</TableHead>
			<TableCell className="align-top whitespace-normal">
				<span className="flex flex-col items-start gap-1">
					{PRACTICE_SCRIPT_RUN_STATUS_DEFS[run.status].result(run)}
					{fix !== undefined && (
						<PracticeScriptFixLink
							workspaceSlug={workspaceSlug}
							practiceSlug={practice.practiceSlug}
							practiceName={practice.practiceName}
							fix={fix}
						/>
					)}
					{error !== undefined && (
						<code className="max-w-sm font-mono text-xs wrap-anywhere whitespace-normal text-muted-foreground">
							{error}
						</code>
					)}
				</span>
			</TableCell>
			<TableCell className="align-top">
				<ScriptModels models={run.models} />
			</TableCell>
			<TableCell numeric className="text-right align-top">
				{run.models.length === 0 ? <Nothing /> : count.format(calls)}
			</TableCell>
			<TableCell numeric className="text-right align-top">
				{run.durationMs === null ? <Nothing /> : formatScriptTime(run.durationMs)}
			</TableCell>
		</TableRow>
	);
}

/**
 * Each model with its data-handling tier. The tier's icon is enough beside the kind's name, and a
 * screen reader hears its label. A model with counted calls ran even when its tier is unknown, as
 * for a script that did not finish, so it claims nothing about its assignment.
 */
function ScriptModels({ models }: { models: PracticeScriptModel[] }) {
	if (models.length === 0) {
		return <Nothing />;
	}
	return (
		<ul className="flex flex-col gap-1">
			{models.map((model) => (
				<li key={model.kind} className="flex items-center gap-2">
					<ModelKindMark purpose={SCRIPT_MODEL_PURPOSE[model.kind]} />
					{model.dataHandlingTier === null ? (
						model.calls === 0 && <span className="text-muted-foreground">No model</span>
					) : (
						<DataHandlingMark tier={model.dataHandlingTier} />
					)}
				</li>
			))}
		</ul>
	);
}

function Nothing() {
	return (
		<>
			<span aria-hidden className="text-muted-foreground">
				—
			</span>
			<span className="sr-only">none</span>
		</>
	);
}
