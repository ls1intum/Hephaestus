import { ChevronDown, CircleAlert, CircleCheck, Lightbulb } from "lucide-react";
import { useState } from "react";
import { formatWhen } from "@/components/practices-design/shared/ArtifactLink";
import { getArea } from "@/components/practices-design/shared/area-identity";
import { ArtifactStateIcon } from "@/components/practices-design/shared/status-language";
import type { ActivityItem, FeedObservation } from "@/components/practices-design/shared/types";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { cn } from "@/lib/utils";

function ObservationChip({ observation }: { observation: FeedObservation }) {
	const area = getArea(observation.areaId);
	const Icon = area.icon;
	const ToneIcon = observation.tone === "POSITIVE" ? CircleCheck : CircleAlert;
	return (
		<span
			className={cn(
				"inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium",
				area.tintClassName,
			)}
		>
			<Icon className={cn("size-3", area.iconClassName)} aria-hidden="true" />
			{observation.practiceName}
			<ToneIcon
				className={cn(
					"size-3",
					observation.tone === "POSITIVE"
						? "text-provider-success-foreground"
						: "text-provider-attention-foreground",
				)}
				aria-label={observation.tone === "POSITIVE" ? "Went well" : "Worth a look"}
			/>
		</span>
	);
}

function ObservationDetail({ observation }: { observation: FeedObservation }) {
	const area = getArea(observation.areaId);
	const Icon = area.icon;
	return (
		<div className="flex flex-col gap-1 border-l-2 border-border py-0.5 pl-3">
			<span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
				<Icon className={cn("size-3.5", area.iconClassName)} aria-hidden="true" />
				{area.name} · {observation.practiceName}
			</span>
			<p className="text-sm text-foreground">{observation.reasoning}</p>
			{observation.guidance && (
				<p className="flex items-start gap-1.5 text-sm text-muted-foreground">
					<Lightbulb className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
					{observation.guidance}
				</p>
			)}
		</div>
	);
}

export interface ActivityFeedItemProps {
	item: ActivityItem;
}

/**
 * One artifact in the feed. Collapsed: state icon, deep-linked title, repo and time, plus
 * one observation chip per practice signal attached to this artifact. Expanding in place
 * reveals the reasoning and the next step for each observation, so evidence never leaves
 * the activity it came from. Artifacts without observations render as plain activity.
 */
export function ActivityFeedItem({ item }: ActivityFeedItemProps) {
	const [open, setOpen] = useState(false);
	const hasObservations = item.observations.length > 0;
	return (
		<Collapsible
			open={open}
			onOpenChange={setOpen}
			className="rounded-lg border bg-card px-3 py-2.5"
		>
			<div className="flex items-start gap-2">
				<ArtifactStateIcon
					kind={item.artifact.kind}
					state={item.artifact.state}
					className="mt-0.5"
				/>
				<div className="flex min-w-0 flex-1 flex-col gap-1.5">
					<div className="flex min-w-0 items-baseline gap-2">
						<a
							href={item.artifact.url}
							target="_blank"
							rel="noreferrer"
							className="min-w-0 truncate text-sm font-medium hover:underline"
						>
							{item.artifact.title}
						</a>
						<span className="shrink-0 text-xs text-muted-foreground">
							{item.artifact.repo.split("/")[1]} #{item.artifact.number} ·{" "}
							{formatWhen(item.happenedAt)}
						</span>
					</div>
					{hasObservations && (
						<div className="flex flex-wrap items-center gap-1.5">
							{item.observations.map((observation) => (
								<ObservationChip
									key={`${item.id}-${observation.practiceName}`}
									observation={observation}
								/>
							))}
						</div>
					)}
				</div>
				{hasObservations && (
					<CollapsibleTrigger
						className="rounded-md p-1 text-muted-foreground hover:bg-accent hover:text-foreground"
						aria-label={open ? "Hide details" : "Show details"}
					>
						<ChevronDown
							className={cn("size-4 transition-transform", open && "rotate-180")}
							aria-hidden="true"
						/>
					</CollapsibleTrigger>
				)}
			</div>
			{hasObservations && (
				<CollapsibleContent className="mt-2 flex flex-col gap-2.5 pl-6">
					{item.observations.map((observation) => (
						<ObservationDetail
							key={`${item.id}-${observation.practiceName}-detail`}
							observation={observation}
						/>
					))}
				</CollapsibleContent>
			)}
		</Collapsible>
	);
}
