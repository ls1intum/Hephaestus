import type { ReactNode } from "react";

import { cn } from "cn";
import { InlineLink } from "@/components/common/InlineLink";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { levelPathAt } from "@/components/layout/detail-drawer/level-path";
import { LevelHeader } from "@/components/layout/detail-drawer/LevelHeader";
import { DrawerBody } from "@/components/ui/drawer";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { ARTIFACT_KIND, artifactKindNoun } from "@/lib/artifact-kinds";
import { formatCalendarDate, formatDayRange } from "@/lib/dates";
import { getProviderTerms, type ProviderType } from "@/lib/provider/provider-terms";
import { capitalise, hasText } from "@/lib/text";

import { contributionsNote, type PeopleRow } from "./activity-people-rows";
import type { ActivityStackEntry } from "./activity-search";
import { STALE } from "./activity-tones";
import { ActivityEmpty, HistoryMark } from "./ActivityEmpty";
import type { PeopleTableState } from "./ActivityPeopleTable";
import { MemberAvatar } from "./MemberAvatar";

export interface PublicPersonDrawerProps {
	stack: ActivityStackEntry[];
	onClose: (depth: number) => void;
	providerType: ProviderType;
	/** The people the page shows; the person a level opens is read from them, never fetched apart. */
	people: PeopleTableState;
}

/**
 * The public page's person level: what the published page already counts for one person, and their
 * profile at the provider as a secondary link. No work item, and nothing the page does not show.
 */
export function PublicPersonDrawer({
	stack,
	onClose,
	providerType,
	people,
}: PublicPersonDrawerProps) {
	const rows = people.status === "ready" ? people.people.people : [];
	const rowOf = (login: string) => rows.find((row) => row.person.login === login);
	const pathAt = levelPathAt(stack, {
		pageLabel: "Public activity",
		labelOf: ({ target }) =>
			target.kind === "person" ? (rowOf(target.login)?.person.name ?? target.login) : "",
		onClose,
	});
	return (
		<DetailDrawerStack stack={stack} size="detail" onClose={onClose}>
			{(_entry, level): ReactNode => {
				const target = stack[level.depth]?.target;
				if (target?.kind !== "person") {
					return null;
				}
				return (
					<PublicPersonLevel
						nested={level.nested}
						path={pathAt(level.depth)}
						login={target.login}
						providerType={providerType}
						people={people}
					/>
				);
			}}
		</DetailDrawerStack>
	);
}

/** "1 Sep 2026 – 30 Sep 2026": the days the page counted, as the response states them. */
function spanLabel(from: Date, to: Date): string {
	return formatDayRange(from, new Date(to.getTime() - 1));
}

function PublicPersonLevel({
	nested,
	path,
	login,
	providerType,
	people,
}: {
	nested: boolean;
	path: LevelPath;
	login: string;
	providerType: ProviderType;
	people: PeopleTableState;
}) {
	const ready = people.status === "ready" ? people : undefined;
	const row = ready?.people.people.find((candidate) => candidate.person.login === login);
	const span = ready && spanLabel(ready.people.from, ready.people.to);
	return (
		<>
			<LevelHeader
				nested={nested}
				path={path}
				current={row?.person.name ?? login}
				mark={row && <MemberAvatar user={row.person} size="lg" />}
				description={span === undefined ? login : `${login} · ${span}`}
			/>
			<DrawerBody
				className={cn("flex flex-col gap-6 pt-2", ready?.stale === true && STALE)}
				aria-busy={people.status === "loading" || ready?.stale === true || undefined}
			>
				{people.status === "loading" && <PersonSkeleton />}
				{people.status === "error" && (
					<QueryErrorAlert
						error={people.error}
						title="We could not load this person’s activity"
						onRetry={people.onRetry}
					/>
				)}
				{ready && row === undefined && (
					<ActivityEmpty
						icon={<HistoryMark providerType={providerType} />}
						title="No activity in this range"
					/>
				)}
				{ready && row !== undefined && (
					<PersonFigures
						row={row}
						providerType={providerType}
						stale={ready.stale}
						span={{ from: ready.people.from, to: ready.people.to }}
					/>
				)}
			</DrawerBody>
		</>
	);
}

function PersonSkeleton() {
	return (
		<div className="space-y-3">
			{Array.from({ length: 4 }, (_, index) => (
				<Skeleton key={index} className="h-5 w-full" />
			))}
		</div>
	);
}

function PersonFigures({
	row,
	providerType,
	stale,
	span,
}: {
	row: PeopleRow;
	providerType: ProviderType;
	stale: boolean;
	span: { from: Date; to: Date };
}) {
	const pullRequests = artifactKindNoun(ARTIFACT_KIND.pullRequest, 2, providerType);
	const { counts, person } = row;
	const totals: [string, number][] = [
		["Contributions", counts.contributions],
		[`${capitalise(pullRequests)} opened`, counts.pullRequestsOpened],
		[`${capitalise(pullRequests)} merged`, counts.pullRequestsMerged],
		[`Distinct ${pullRequests} reviewed`, counts.pullRequestsReviewed],
		["People helped", counts.peopleHelped],
		["Issues opened", counts.issuesOpened],
		["Active weeks", counts.activeWeeks],
	];
	return (
		<>
			{stale && (
				<p className="text-sm text-muted-foreground">
					These figures are for {spanLabel(span.from, span.to)} while the new range loads.
				</p>
			)}
			{hasText(person.htmlUrl) && (
				<p className="text-sm">
					<InlineLink href={person.htmlUrl} external>
						{person.login} on {getProviderTerms(providerType).displayName}
					</InlineLink>
				</p>
			)}
			<section aria-labelledby="public-person-totals" className="space-y-2">
				<h3 id="public-person-totals" className="font-medium">
					In this range
				</h3>
				<dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
					{totals.map(([label, value]) => (
						<div key={label} className="contents">
							<dt className="text-muted-foreground">{label}</dt>
							<dd className="text-right tabular-nums">{value.toLocaleString("en-GB")}</dd>
						</div>
					))}
				</dl>
				<p className="text-xs text-muted-foreground">{contributionsNote(providerType)}</p>
			</section>
			<section aria-labelledby="public-person-weeks" className="space-y-2">
				<h3 id="public-person-weeks" className="font-medium">
					Contributions by week
				</h3>
				{row.weeks.length === 0 ? (
					<p className="text-sm text-muted-foreground">No weekly activity in this range.</p>
				) : (
					<Table aria-labelledby="public-person-weeks">
						<TableHeader>
							<TableRow variant="static">
								<TableHead>Week of</TableHead>
								<TableHead numeric className="text-right">
									Contributions
								</TableHead>
							</TableRow>
						</TableHeader>
						<TableBody>
							{row.weeks.map((week) => (
								<TableRow key={week.start.getTime()} variant="static">
									<TableCell>{formatCalendarDate(week.start)}</TableCell>
									<TableCell numeric className="text-right">
										{week.contributions.toLocaleString("en-GB")}
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				)}
				<p className="text-xs text-muted-foreground">
					Weeks with activity are listed. Each week counts its distinct reviews on its own, so the
					weeks can add up to more than the range’s total.
				</p>
			</section>
		</>
	);
}
