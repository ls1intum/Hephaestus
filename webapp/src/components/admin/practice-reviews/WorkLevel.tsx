import type { ReactNode } from "react";

import type { ItemEvent, ItemFact, ItemRef } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { RefusalFixLink } from "@/components/practice-trace/RefusalFixLink";
import {
	ITEM_EVENT_FOUND_BY,
	ITEM_EVENT_REASON_SENTENCES,
	ITEM_EVENT_STATE_DEFS,
} from "@/components/practice-vocabulary/item-event-defs";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import { itemKindLabel, itemName } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

import { LevelBodySkeleton } from "./LevelBodySkeleton";
import {
	type SheetEvent,
	SheetHistory,
	SheetSection,
	SheetStatusLine,
	SheetTechnical,
} from "./RecordSheet";
import { RequestReviewAction } from "./RequestReviewAction";
import { itemLevel } from "./review-levels";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";
import { ReviewItemLink } from "./ReviewItem";
import { ReviewLevelHeader } from "./ReviewLevelHeader";

export interface WorkLevelProps {
	workspaceSlug: string;
	nested?: boolean;
	path: LevelPath;
	item: PanelState<{ item: ItemRef }>;
	events?: PanelState<{ events: ItemEvent[] }>;
	eventLabel: (name: string) => string;
	facts?: PanelState<{ facts: ItemFact[] }>;
	onRequestReview?: () => void;
	requesting?: boolean;
	requestBlocked?: string;
	refusal?: ReactNode;
}

export function WorkLevel({
	workspaceSlug,
	nested,
	path,
	item: state,
	events: eventsState,
	eventLabel,
	facts: factsState,
	onRequestReview,
	requesting = false,
	requestBlocked,
	refusal,
}: WorkLevelProps) {
	const item = state.status === "ready" ? state.item : undefined;
	const events = eventsState?.status === "ready" ? eventsState.events : [];
	const latest = latestEvent(events);
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="item"
			loading={state.status === "loading"}
			chips={
				latest && (
					<SheetStatusLine
						status={<StatusBadge def={ITEM_EVENT_STATE_DEFS[latest.state]} />}
						figure={eventsFigure(events)}
						at={{ label: "latest event", date: latest.occurredAt }}
					/>
				)
			}
			title={item && (item.title ?? item.label)}
			description={item && <ReviewItemLink item={item} />}
		/>
	);
	if (state.status !== "ready" || item === undefined) {
		return (
			<>
				{header}
				<DrawerBody className="pt-2">
					{state.status === "error" ? (
						<QueryErrorAlert
							error={state.error}
							title="We could not load this work"
							onRetry={state.onRetry}
						/>
					) : (
						<LevelBodySkeleton />
					)}
				</DrawerBody>
			</>
		);
	}
	const history: SheetEvent[] = [
		...events.map((event) => itemEvent(event, eventLabel, workspaceSlug)),
		...(item.removedAt === null
			? []
			: [{ key: "removed", at: item.removedAt, title: "Removed from the provider" }]),
	];
	return (
		<>
			{header}
			<DrawerBody className="flex flex-col gap-8 pt-2">
				{refusal}
				<SheetSection part="what">
					<ReviewFactGrid>
						<ReviewFact label="Kind">{itemKindLabel(item.kind, 1, item.provider)}</ReviewFact>
						{hasText(item.container) && !item.label.includes(item.container) && (
							<ReviewFact label="Where">{item.container}</ReviewFact>
						)}
						{hasText(item.path) && (
							<ReviewFact label="Path">
								<code className="break-all">{item.path}</code>
							</ReviewFact>
						)}
						{hasText(item.parentId) && (
							<ReviewFact label="Part of">
								<InlineLink render={<DetailStackLink entry={itemLevel(item.parentId)} />}>
									Open the work it belongs to
								</InlineLink>
							</ReviewFact>
						)}
						{factsState?.status === "ready" &&
							factsState.facts.map((fact) => (
								<ReviewFact key={fact.name} label={fact.name}>
									<FactValue fact={fact} />
								</ReviewFact>
							))}
					</ReviewFactGrid>
					{factsState?.status === "error" && (
						<QueryErrorAlert
							error={factsState.error}
							title="We could not load what the provider reports about this work"
							onRetry={factsState.onRetry}
						/>
					)}
				</SheetSection>
				<SheetSection
					part="state"
					description="Every event recorded on the work and what became of it."
				>
					{eventsState?.status === "loading" && <LevelBodySkeleton />}
					{eventsState?.status === "error" && (
						<QueryErrorAlert
							error={eventsState.error}
							title="We could not load the events on this work"
							onRetry={eventsState.onRetry}
						/>
					)}
					{(eventsState === undefined || eventsState.status === "ready") &&
						(history.length === 0 ? (
							<p className="text-sm text-muted-foreground">No event was recorded on this work.</p>
						) : (
							<SheetHistory events={history} />
						))}
				</SheetSection>
				<SheetTechnical
					facts={[
						{ label: "Work ID", value: <code>{item.id}</code>, copy: item.id },
						{ label: "Kind", value: <code>{item.kind}</code> },
						{
							label: "Latest version",
							value: hasText(latest?.itemVersion) ? <code>{latest.itemVersion}</code> : "—",
							copy: latest?.itemVersion ?? undefined,
						},
					]}
				/>
			</DrawerBody>
			{onRequestReview !== undefined && (
				<DrawerFooter>
					<RequestReviewAction
						label="Request review"
						request={{
							work: itemName(item),
							onRequest: onRequestReview,
							requesting,
							blocked: requestBlocked,
						}}
					/>
				</DrawerFooter>
			)}
		</>
	);
}

function latestEvent(events: readonly ItemEvent[]): ItemEvent | undefined {
	let latest: ItemEvent | undefined;
	for (const event of events) {
		if (latest === undefined || event.occurredAt > latest.occurredAt) {
			latest = event;
		}
	}
	return latest;
}

function eventsFigure(events: readonly ItemEvent[]): string {
	const count = events.length === 1 ? "1 event" : `${events.length} events`;
	const started = events.filter((event) => hasText(event.reviewId)).length;
	return `${count}, ${started === 0 ? "none" : String(started)} started a review`;
}

function FactValue({ fact }: { fact: ItemFact }) {
	const value = hasText(fact.value) ? fact.value : "None";
	return hasText(fact.url) ? (
		<InlineLink href={fact.url} external className="break-all">
			{value}
		</InlineLink>
	) : (
		<span className="break-words">{value}</span>
	);
}

function itemEvent(
	event: ItemEvent,
	eventLabel: (name: string) => string,
	workspaceSlug: string,
): SheetEvent {
	const foundBy = ITEM_EVENT_FOUND_BY[event.foundBy];
	return {
		key: event.id,
		at: event.occurredAt,
		title: eventLabel(event.name),
		detail: (
			<>
				<span className="block">
					<span title={foundBy.description}>{foundBy.label}</span>
					{" · "}
					{ITEM_EVENT_STATE_DEFS[event.state].label}
				</span>
				{event.reason !== null && (
					<span className="block">
						<span>{ITEM_EVENT_REASON_SENTENCES[event.reason]} </span>
						<RefusalFixLink workspaceSlug={workspaceSlug} reason={event.reason} />
					</span>
				)}
			</>
		),
	};
}
