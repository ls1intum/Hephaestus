import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon, ArrowUpIcon, ExternalLinkIcon, RadarIcon } from "lucide-react";
import { getArtifactTraceOptions } from "@/api/@tanstack/react-query.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Badge } from "@/components/ui/badge";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Item, ItemContent, ItemGroup, ItemTitle } from "@/components/ui/item";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import { TraceOutcomeBadge } from "./TraceOutcomeBadge";
import {
	artifactKindIcon,
	deliveryLabel,
	DISCOVERED_VIA_DESCRIPTIONS,
	DISCOVERED_VIA_LABELS,
	occurrenceDomId,
	REVIEW_TIER_DESCRIPTIONS,
	REVIEW_TIER_LABELS,
	SIGNAL_STATE_LABELS,
	SIGNAL_STATE_REASON_LABELS,
	WITHHELD_REASON_LABELS,
} from "./trace-format";

export interface TracePageProps {
	workspaceSlug: string;
	artifactKind: string;
	artifactId: number;
}

/**
 * One piece of work, everything recorded about it, and every practice's answer — the answered ones
 * and the quiet ones alike. Nothing here collapses behind a "show more": a practice that did
 * nothing is precisely what the reader came to find, so it is on screen by default with its reason
 * next to it.
 */
export function TracePage({ workspaceSlug, artifactKind, artifactId }: TracePageProps) {
	const query = useQuery({
		...getArtifactTraceOptions({ path: { workspaceSlug, artifactKind, artifactId } }),
	});
	const backLink = (
		<Link
			to="/w/$workspaceSlug/reviews"
			params={{ workspaceSlug }}
			className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground hover:underline"
		>
			<ArrowLeftIcon className="size-4" aria-hidden />
			Review activity
		</Link>
	);

	if (query.isLoading) {
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{backLink}
				<div role="status" className="space-y-3">
					<span className="sr-only">Loading review activity</span>
					<Skeleton className="h-8 w-2/3 max-w-md" />
					<Skeleton className="h-4 w-1/3 max-w-xs" />
					<Skeleton className="h-40 w-full" />
				</div>
			</article>
		);
	}
	if (query.isError || !query.data) {
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{backLink}
				<QueryErrorAlert
					error={query.error}
					title="Couldn't load this work's review activity"
					onRetry={() => void query.refetch()}
				/>
			</article>
		);
	}

	const trace = query.data;
	const KindIcon = artifactKindIcon(trace.artifactKind);
	// The same signal name recurs on every revision, so only the id identifies which occurrence a
	// practice's answer actually rests on.
	const signalsById = new Map(trace.signals.map((signal) => [signal.id, signal]));

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			{backLink}

			<header className="min-w-0 space-y-3">
				<p className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground">
					<KindIcon className="size-4 shrink-0" aria-hidden />
					{artifactKindLabel(trace.artifactKind)}
				</p>
				<h1 className="break-words text-2xl font-semibold tracking-tight">
					{trace.title}
					{trace.number != null && (
						<span className="ml-2 font-normal text-muted-foreground tabular-nums">
							#{trace.number}
						</span>
					)}
				</h1>
				<div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
					{trace.container && <span className="break-all">{trace.container}</span>}
					{trace.url && (
						<a
							href={trace.url}
							target="_blank"
							rel="noopener noreferrer"
							className="inline-flex items-center gap-1 font-medium text-foreground hover:underline"
						>
							Open the original
							<ExternalLinkIcon className="size-3.5 shrink-0" aria-hidden />
							<span className="sr-only"> (opens in a new tab)</span>
						</a>
					)}
				</div>
			</header>

			<section aria-labelledby="trace-signals-heading" className="min-w-0 space-y-3">
				<div className="space-y-1">
					<h2 id="trace-signals-heading" className="text-lg font-semibold">
						What we noticed
					</h2>
					<p className="text-sm text-muted-foreground">
						Everything recorded about this work, oldest first.
					</p>
				</div>
				{trace.signals.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyTitle>Nothing was recorded about this work</EmptyTitle>
							<EmptyDescription>
								Without an occurrence to react to, no practice was ever asked a question about it.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<ol className="min-w-0 space-y-0 border-l pl-4">
						{trace.signals.map((signal) => (
							// `tabIndex={-1}` is what makes the jump land somewhere: following the fragment moves
							// focus here, so a screen-reader or keyboard user arrives at the occurrence rather
							// than being told the page scrolled and left to find it.
							<li
								key={signal.id}
								id={occurrenceDomId(signal.id)}
								tabIndex={-1}
								className="relative min-w-0 scroll-mt-24 rounded-md py-2.5 outline-none target:bg-muted focus:bg-muted"
							>
								<span
									className="absolute -left-[1.3125rem] top-4 size-2 rounded-full bg-border ring-4 ring-background"
									aria-hidden
								/>
								<p className="break-words text-sm font-medium">{signal.displayName}</p>
								<p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
									<RelativeTime value={signal.occurredAt} className="text-xs" />
									<span aria-hidden>·</span>
									<Tooltip>
										<TooltipTrigger className="cursor-help underline decoration-dotted underline-offset-4">
											{DISCOVERED_VIA_LABELS[signal.discoveredVia]}
										</TooltipTrigger>
										<TooltipContent>
											{DISCOVERED_VIA_DESCRIPTIONS[signal.discoveredVia]}
										</TooltipContent>
									</Tooltip>
									<span aria-hidden>·</span>
									<span>{SIGNAL_STATE_LABELS[signal.state]}</span>
								</p>
								{signal.stateReason && (
									<p className="mt-1 break-words text-xs text-muted-foreground">
										{SIGNAL_STATE_REASON_LABELS[signal.stateReason]}.
									</p>
								)}
							</li>
						))}
					</ol>
				)}
			</section>

			<section aria-labelledby="trace-practices-heading" className="min-w-0 space-y-3">
				<div className="space-y-1">
					<h2 id="trace-practices-heading" className="text-lg font-semibold">
						What each practice made of it
					</h2>
					<p className="max-w-2xl text-sm text-muted-foreground">
						Every practice this workspace runs against this kind of work is listed, including the
						ones that stayed quiet. Whether a practice was measured and whether anything reached you
						are two separate things — a practice can be reviewed and still, by design, say nothing.
					</p>
				</div>
				{trace.practices.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyTitle>No practice covers this kind of work</EmptyTitle>
							<EmptyDescription>
								This workspace runs no practice against{" "}
								{artifactKindLabel(trace.artifactKind).toLowerCase()}, so nothing was ever going to
								be said about it.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<ItemGroup>
						{trace.practices.map((entry) => {
							const occurrence = entry.occasionedById
								? signalsById.get(entry.occasionedById)
								: undefined;
							return (
								<div key={entry.practiceSlug} role="listitem">
									<Item variant="outline" className="items-start">
										<ItemContent className="min-w-0 gap-2">
											<div className="flex w-full min-w-0 flex-wrap items-center gap-2">
												<TraceOutcomeBadge outcome={entry.outcome} />
												<ItemTitle className="min-w-0 line-clamp-none break-words">
													{entry.practiceName}
												</ItemTitle>
											</div>
											{/* Server-rendered and printed verbatim: it is phrased as what would change the
										    outcome, and re-wording it here is how a screen and a support answer drift. */}
											<p className="break-words text-sm text-muted-foreground">
												{entry.explanation}
											</p>
											<dl className="flex w-full min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
												<div className="flex min-w-0 items-center gap-1">
													<dt className="sr-only">Reached you</dt>
													<dd className="break-words">{deliveryLabel(entry)}</dd>
												</div>
												<div className="flex min-w-0 items-center gap-1">
													<dt className="sr-only">Loudness</dt>
													<dd>
														<Tooltip>
															<TooltipTrigger className="cursor-help underline decoration-dotted underline-offset-4">
																{REVIEW_TIER_LABELS[entry.reviewTier]}
															</TooltipTrigger>
															<TooltipContent>
																{REVIEW_TIER_DESCRIPTIONS[entry.reviewTier]}
															</TooltipContent>
														</Tooltip>
													</dd>
												</div>
												{entry.decidedAt && (
													<div className="flex min-w-0 items-center gap-1">
														<dt className="sr-only">Decided</dt>
														<dd>
															<RelativeTime value={entry.decidedAt} className="text-xs" />
														</dd>
													</div>
												)}
												{(occurrence || entry.occasionedBy) && (
													<div className="flex min-w-0 items-center gap-1">
														<dt>Rests on</dt>
														<dd className="min-w-0">
															{occurrence ? (
																// The visible words are the occurrence's own label, and the accessible
																// name only prefixes them, so the spoken name still starts with what a
																// speech-control user can see and say (WCAG 2.2 SC 2.5.3).
																<a
																	href={`#${occurrenceDomId(occurrence.id)}`}
																	className="inline-flex max-w-full items-center gap-1 font-medium text-foreground underline underline-offset-4 hover:no-underline"
																>
																	<span className="sr-only">Jump to: </span>
																	<span className="truncate">{occurrence.displayName}</span>
																	<ArrowUpIcon className="size-3 shrink-0" aria-hidden />
																</a>
															) : (
																// The id did not resolve to anything in this trace's timeline. The raw
																// signal name is worse copy than a label but far better than silence.
																<span className="break-all">{entry.occasionedBy}</span>
															)}
														</dd>
													</div>
												)}
											</dl>
											{entry.withheldReasons.length > 0 && (
												<ul
													className="flex w-full min-w-0 flex-wrap gap-1.5"
													aria-label="Held back because"
												>
													{entry.withheldReasons.map((reason) => (
														<li key={reason}>
															<Badge variant="outline" className="max-w-full">
																<span className="truncate">{WITHHELD_REASON_LABELS[reason]}</span>
															</Badge>
														</li>
													))}
												</ul>
											)}
											{entry.watches.length > 0 && (
												<p className="w-full min-w-0 break-words text-xs text-muted-foreground">
													Watches for: {entry.watches.join(", ")}
												</p>
											)}
										</ItemContent>
									</Item>
								</div>
							);
						})}
					</ItemGroup>
				)}
			</section>

			<p className="flex items-center gap-1.5 text-xs text-muted-foreground">
				<RadarIcon className="size-3.5 shrink-0" aria-hidden />
				Silence here is always a decision with a reason. If a reason looks wrong, a workspace admin
				can change the practice or its loudness.
			</p>
		</article>
	);
}
