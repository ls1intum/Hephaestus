import { Link } from "@tanstack/react-router";
import { AlertCircleIcon, ArrowLeftIcon, DownloadIcon, InfoIcon, OctagonXIcon } from "lucide-react";
import type { ReactNode } from "react";

import type { ScanDetail, ScanReport, ScanReportItem, ScanReportProblem } from "@/api/types.gen";
import {
	PracticeTabsList,
	PracticeTabsRail,
	PracticeTabsTrigger,
} from "@/components/common/practice-tabs";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import { PageLayout } from "@/components/layout/PageLayout";
import {
	REVIEW_STATUS_DEFS,
	REVIEW_STOP_REASON_SENTENCES,
} from "@/components/practice-vocabulary/review-status-defs";
import {
	type ProblemBaselineState,
	REMOVAL_GUARD_DEFS,
	SCAN_COMPARISON_DEFS,
} from "@/components/practice-vocabulary/scan-baseline-defs";
import type { ScanEntryStatus } from "@/components/practice-vocabulary/scan-entry-defs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Tabs, TabsContent } from "@/components/ui/tabs";
import { formatDate } from "@/lib/dates";
import { problemDetailOf } from "@/lib/problem-detail";

import {
	isActiveScan,
	knownReasonSentence,
	repositoryName,
	scanStartedBy,
	shortVersion,
	withheldSentence,
} from "./scan-display";
import type { ScanListState } from "./scan-list-state";
import { ScanCounts } from "./ScanCounts";
import { ScanItemTable } from "./ScanItemTable";
import { ScanPracticeTable } from "./ScanPracticeTable";
import { ScanProblemTable } from "./ScanProblemTable";
import { ScanReportSkeleton } from "./ScanReportSkeleton";
import { SCAN_REPORT_TABS, type ScanReportTab } from "./scans-search";

export type ScanReportState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "ready"; scan: ScanDetail; report: ScanReport };

export interface ScanAction {
	pending: boolean;
	error?: unknown;
	onRun: () => void;
}

export interface ScanReportPageProps {
	workspaceSlug: string;
	state: ScanReportState;
	tab: ScanReportTab;
	onTabChange: (tab: ScanReportTab) => void;
	problems: ScanListState<ScanReportProblem>;
	problemFilter: ProblemBaselineState | undefined;
	onProblemFilterChange: (filter: ProblemBaselineState | undefined) => void;
	files: ScanListState<ScanReportItem>;
	fileFilter: ScanEntryStatus | undefined;
	onFileFilterChange: (filter: ScanEntryStatus | undefined) => void;
	cancel: ScanAction;
	exportSarif: ScanAction;
}

export const PARTIAL_SENTENCE =
	"The scan did not finish. These counts cover only the work it reached.";

const TAB_LABELS: Record<ScanReportTab, string> = {
	problems: "Problems",
	files: "Files",
	practices: "Practices",
};

export function ScanReportPage(props: ScanReportPageProps) {
	const { state, workspaceSlug } = props;
	if (state.status === "loading") {
		return <ScanReportSkeleton />;
	}
	if (state.status === "error") {
		return (
			<PageLayout>
				<BackToScans workspaceSlug={workspaceSlug} />
				<QueryErrorAlert
					error={state.error}
					title="We could not load this scan"
					onRetry={state.onRetry}
				/>
			</PageLayout>
		);
	}
	return <LoadedReport {...props} scan={state.scan} report={state.report} />;
}

function BackToScans({ workspaceSlug }: { workspaceSlug: string }) {
	return (
		<Link
			to="/w/$workspaceSlug/admin/practices/scans"
			params={{ workspaceSlug }}
			className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
		>
			<ArrowLeftIcon aria-hidden className="size-4" />
			Scans
		</Link>
	);
}

interface LoadedReportProps extends ScanReportPageProps {
	scan: ScanDetail;
	report: ScanReport;
}

function LoadedReport({
	workspaceSlug,
	scan,
	report,
	tab,
	onTabChange,
	problems,
	problemFilter,
	onProblemFilterChange,
	files,
	fileFilter,
	onFileFilterChange,
	cancel,
	exportSarif,
}: LoadedReportProps) {
	const active = isActiveScan(scan.status);
	const unfinished = !active && scan.status !== "COMPLETE";
	const { counts } = report;
	return (
		<PageLayout>
			<div className="space-y-2">
				<BackToScans workspaceSlug={workspaceSlug} />
				<header className="flex flex-wrap items-start justify-between gap-4">
					<div className="min-w-0 space-y-1">
						<h1 className="text-2xl font-semibold tracking-tight break-words">
							{repositoryName(scan.repository)}
						</h1>
						<p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
							<StatusBadge def={REVIEW_STATUS_DEFS[scan.status]} />
							<span>
								{scan.ref}
								<span className="font-mono"> · {shortVersion(scan.itemVersion)}</span>
							</span>
							<span>{formatDate(scan.startedAt ?? scan.createdAt)}</span>
							<span>Started by {scanStartedBy(scan)}</span>
						</p>
					</div>
					<div className="flex flex-col items-end gap-2">
						{active ? (
							<Button
								type="button"
								variant="outline"
								disabled={cancel.pending}
								onClick={cancel.onRun}
							>
								{cancel.pending ? <Spinner /> : <OctagonXIcon aria-hidden />}
								{cancel.pending ? "Cancelling scan…" : "Cancel scan"}
							</Button>
						) : (
							<Button
								type="button"
								variant="outline"
								disabled={exportSarif.pending || report.availability.expired}
								onClick={exportSarif.onRun}
							>
								{exportSarif.pending ? <Spinner /> : <DownloadIcon aria-hidden />}
								{exportSarif.pending ? "Exporting SARIF…" : "Export SARIF"}
							</Button>
						)}
					</div>
				</header>
				{active && cancel.error !== undefined && (
					<ActionError title="We could not cancel the scan" error={cancel.error} />
				)}
				{!active && exportSarif.error !== undefined && (
					<ActionError title="We could not export the SARIF file" error={exportSarif.error} />
				)}
			</div>

			<ReportNotes scan={scan} report={report} active={active} unfinished={unfinished} />

			<ScanCounts counts={counts} recorded={!active && report.recordedCounts !== null} />

			<Tabs
				value={tab}
				onValueChange={(next) => {
					const chosen = SCAN_REPORT_TABS.find((candidate) => candidate === next);
					if (chosen !== undefined) {
						onTabChange(chosen);
					}
				}}
				className="gap-4"
			>
				<PracticeTabsRail>
					<PracticeTabsList aria-label="Report">
						{SCAN_REPORT_TABS.map((value) => (
							<PracticeTabsTrigger key={value} value={value}>
								{TAB_LABELS[value]}
							</PracticeTabsTrigger>
						))}
					</PracticeTabsList>
				</PracticeTabsRail>
				<TabsContent value="problems">
					<ScanProblemTable
						state={problems}
						filter={problemFilter}
						onFilterChange={onProblemFilterChange}
						changes={report.problemChanges}
						pairsWithoutResult={counts.withheld + counts.notReviewed}
					/>
				</TabsContent>
				<TabsContent value="files">
					<ScanItemTable state={files} filter={fileFilter} onFilterChange={onFileFilterChange} />
				</TabsContent>
				<TabsContent value="practices">
					<ScanPracticeTable practices={report.practices} />
				</TabsContent>
			</Tabs>
		</PageLayout>
	);
}

function ActionError({ title, error }: { title: string; error: unknown }) {
	return (
		<Alert variant="destructive">
			<AlertCircleIcon />
			<AlertTitle>{title}</AlertTitle>
			<AlertDescription>{problemDetailOf(error, "Try again in a moment.")}</AlertDescription>
		</Alert>
	);
}

function Note({ title, children }: { title: string; children: ReactNode }) {
	return (
		<Alert>
			<InfoIcon />
			<AlertTitle>{title}</AlertTitle>
			<AlertDescription>{children}</AlertDescription>
		</Alert>
	);
}

function progressSentence(scan: ScanDetail): string {
	const { listedPairs, terminalPairs, activeJobs, queuedJobs } = scan.progress;
	const finished =
		listedPairs === null
			? `${terminalPairs} practice and file pairs finished so far. The total is known once every file is listed.`
			: `${terminalPairs} of ${listedPairs} practice and file pairs finished.`;
	return `${finished} ${activeJobs} running, ${queuedJobs} waiting.`;
}

function ReportNotes({
	scan,
	report,
	active,
	unfinished,
}: {
	scan: ScanDetail;
	report: ScanReport;
	active: boolean;
	unfinished: boolean;
}) {
	const comparison = SCAN_COMPARISON_DEFS[report.comparison];
	const guard = REMOVAL_GUARD_DEFS[report.removalGuard.state];
	return (
		<div className="space-y-3">
			{active && (
				<Note title={REVIEW_STATUS_DEFS[scan.status].label}>{progressSentence(scan)}</Note>
			)}
			{unfinished && (
				<Note title={REVIEW_STATUS_DEFS[scan.status].label}>
					{PARTIAL_SENTENCE}
					{scan.stopReason !== null && ` ${REVIEW_STOP_REASON_SENTENCES[scan.stopReason]}`}
				</Note>
			)}
			{report.comparison !== "COMPARABLE" && (
				<Note title={comparison.label}>{comparison.description}</Note>
			)}
			{report.removalGuard.state !== "NOT_NEEDED" && (
				<Note title={guard.label}>
					{guard.description}
					{report.removalGuard.state === "SUSPECT_LISTING" &&
						` ${report.removalGuard.wouldRemove} of ${report.removalGuard.previousFiles} earlier files were missing.`}
				</Note>
			)}
			{report.availability.expired && (
				<Note title="Details no longer kept">
					This scan’s file and problem lists and its SARIF export are past their retention. The
					counts stay.
				</Note>
			)}
			{report.withheldReasons.length > 0 && (
				<ReasonList
					title="Why pairs were withheld"
					reasons={report.withheldReasons.map((reason) => ({
						key: reason.reason,
						sentence: withheldSentence(reason.reason),
						count: reason.count,
					}))}
				/>
			)}
			{report.notReviewedReasons.length > 0 && !active && (
				<ReasonList
					title="Why pairs were not reviewed"
					reasons={report.notReviewedReasons.map((reason) => ({
						key: reason.reason,
						sentence:
							knownReasonSentence(reason.reason) ??
							"The scan ended before these files were reviewed.",
						count: reason.count,
					}))}
				/>
			)}
		</div>
	);
}

function ReasonList({
	title,
	reasons,
}: {
	title: string;
	reasons: { key: string; sentence: string; count: number }[];
}) {
	return (
		<section aria-label={title} className="space-y-1 text-sm">
			<h2 className="font-medium">{title}</h2>
			<ul className="space-y-0.5 text-muted-foreground">
				{reasons.map((reason) => (
					<li key={reason.key}>
						<span className="tabular-nums">{reason.count}</span> · {reason.sentence}
					</li>
				))}
			</ul>
		</section>
	);
}
