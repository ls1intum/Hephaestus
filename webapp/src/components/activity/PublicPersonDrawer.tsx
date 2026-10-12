import type { ReactNode } from "react";

import { cn } from "cn";
import { InlineLink } from "@/components/common/InlineLink";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { STAT_TILE_GRID, StatTile, StatTileSkeleton } from "@/components/common/StatTile";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { levelPathAt } from "@/components/layout/detail-drawer/level-path";
import { LevelHeader } from "@/components/layout/detail-drawer/LevelHeader";
import { Section } from "@/components/layout/Section";
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

import {
	ACTIVITY_CATEGORY_DEFS,
	ACTIVITY_KIND_DEFS,
	type ActivityCategoryDef,
} from "./activity-kind-defs";
import { contributionsNote, type PeopleRow } from "./activity-people-rows";
import type { ActivityStackEntry } from "./activity-search";
import { contributionWeekRows } from "./activity-tally";
import { ACTIVITY_TONES, STALE } from "./activity-tones";
import { ActivityEmpty, HistoryMark } from "./ActivityEmpty";
import type { PeopleTableState } from "./ActivityPeopleTable";
import { BucketBars } from "./BucketBars";
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
		<DetailDrawerStack stack={stack} onClose={onClose}>
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
	// The profile link sits where a member's level has it; only a person the page lists has one.
	const profile =
		row !== undefined && hasText(row.person.htmlUrl) ? (
			<InlineLink href={row.person.htmlUrl} external>
				{login} on {getProviderTerms(providerType).displayName}
			</InlineLink>
		) : (
			login
		);
	return (
		<>
			<LevelHeader
				nested={nested}
				path={path}
				current={row?.person.name ?? login}
				mark={row && <MemberAvatar user={row.person} size="lg" />}
				description={
					<p>
						{profile}
						{span !== undefined && ` · ${span}`}
					</p>
				}
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

/** The three work totals beside Contributions. */
const OTHER_TOTALS_COUNT = 3;

const SKELETON_WEEKS = 3;

/** The level's regions before the page's people arrive: the tiles and the weeks. */
function PersonSkeleton() {
	return (
		<>
			<span className="sr-only">Loading this person’s activity…</span>
			<div className="space-y-6" aria-hidden inert>
				<div className="@container space-y-2">
					<Skeleton className="h-6 w-28" />
					<ul className={STAT_TILE_GRID}>
						<li className="flex">
							<StatTileSkeleton>
								<Skeleton className="h-16 w-full" />
							</StatTileSkeleton>
						</li>
						{Array.from({ length: OTHER_TOTALS_COUNT }, (_, index) => (
							<li key={index} className="flex">
								<StatTileSkeleton />
							</li>
						))}
					</ul>
				</div>
				<div className="space-y-2">
					<Skeleton className="h-6 w-48" />
					<Table aria-hidden>
						<TableHeader>
							<TableRow variant="static">
								<TableHead>
									<Skeleton className="h-4 w-16" />
								</TableHead>
								<TableHead numeric>
									<Skeleton className="ml-auto h-4 w-24" />
								</TableHead>
							</TableRow>
						</TableHeader>
						<TableBody>
							{Array.from({ length: SKELETON_WEEKS }, (_, index) => (
								<TableRow key={index} variant="static">
									<TableCell>
										<Skeleton className="h-5 w-32" />
									</TableCell>
									<TableCell numeric>
										<Skeleton className="ml-auto h-5 w-8" />
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</div>
			</div>
		</>
	);
}

/** A published total as a tile: the registry's glyph where one means the same thing, else none. */
interface Total {
	title: string;
	value: number;
	qualifier: string;
	secondary?: { count: number; label: string };
	mark?: Pick<ActivityCategoryDef, "icon" | "tone">;
}

/**
 * The published totals as the stat tiles a member's level uses, without what the page does not
 * publish: no breakdown by kind, no comparison with the period before, and no tile that opens a
 * level, since the page has none to open.
 */
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
	const { counts } = row;
	const others: Total[] = [
		{
			title: capitalise(pullRequests),
			value: counts.pullRequestsMerged,
			qualifier: ACTIVITY_CATEGORY_DEFS["pull-requests"].headline.qualifier,
			secondary: { count: counts.pullRequestsOpened, label: "opened" },
			mark: ACTIVITY_CATEGORY_DEFS["pull-requests"],
		},
		{
			title: "Reviews",
			value: counts.pullRequestsReviewed,
			qualifier: `distinct ${pullRequests}`,
			secondary: {
				count: counts.peopleHelped,
				label: `${counts.peopleHelped === 1 ? "person" : "people"} helped`,
			},
			mark: ACTIVITY_CATEGORY_DEFS.reviews,
		},
		{
			title: "Issues",
			value: counts.issuesOpened,
			qualifier: "opened",
			mark: ACTIVITY_KIND_DEFS.ISSUE_OPENED,
		},
	];
	return (
		<>
			{stale && (
				<p className="text-sm text-muted-foreground">
					These figures are for {spanLabel(span.from, span.to)} while activity updates.
				</p>
			)}
			<Section
				id="public-person-totals"
				level={3}
				size="lg"
				title="In this range"
				className="@container"
			>
				<ul aria-labelledby="public-person-totals-title" className={STAT_TILE_GRID}>
					<li className="flex">
						<StatTile
							variant={counts.contributions > 0 || counts.activeWeeks > 0 ? undefined : "muted"}
							icon={null}
							title="Contributions"
							value={counts.contributions.toLocaleString("en-GB")}
							detail={
								<p className="text-xs text-muted-foreground">
									{counts.activeWeeks.toLocaleString("en-GB")} active{" "}
									{counts.activeWeeks === 1 ? "week" : "weeks"}
								</p>
							}
						>
							{counts.contributions > 0 && (
								<BucketBars
									rows={contributionWeekRows(row.weeks, span)}
									bucket="WEEK"
									utcWeeks
									span={span}
									name="Contributions"
									fill={ACTIVITY_TONES.accent.fill}
								/>
							)}
						</StatTile>
					</li>
					{others.map((total) => (
						<li key={total.title} className="flex">
							<TotalTile total={total} providerType={providerType} />
						</li>
					))}
				</ul>
				<p className="text-xs text-muted-foreground">{contributionsNote(providerType)}</p>
			</Section>
			<Section id="public-person-weeks" level={3} size="lg" title="Contributions by week">
				{row.weeks.length === 0 ? (
					<p className="text-sm text-muted-foreground">No weekly activity in this range.</p>
				) : (
					<Table aria-labelledby="public-person-weeks-title">
						<TableHeader>
							<TableRow variant="static">
								<TableHead>Week of (UTC)</TableHead>
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
			</Section>
		</>
	);
}

/** One total's tile, muted at zero as a member's tile with nothing behind it is. */
function TotalTile({ total, providerType }: { total: Total; providerType: ProviderType }) {
	const { title, value, qualifier, secondary, mark } = total;
	const active = value > 0 || (secondary !== undefined && secondary.count > 0);
	const Icon = mark?.icon(providerType);
	return (
		<StatTile
			variant={active ? undefined : "muted"}
			icon={
				Icon !== undefined &&
				mark !== undefined && (
					<Icon
						size={16}
						className={cn(
							"shrink-0",
							active ? ACTIVITY_TONES[mark.tone].text : "text-muted-foreground",
						)}
					/>
				)
			}
			title={title}
			value={value.toLocaleString("en-GB")}
			qualifier={qualifier}
			detail={
				secondary === undefined ? undefined : (
					<p className="text-xs text-muted-foreground">
						{secondary.count.toLocaleString("en-GB")} {secondary.label}
					</p>
				)
			}
		/>
	);
}
