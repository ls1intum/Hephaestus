import { EyeOff, Inbox } from "lucide-react";
import { useState } from "react";
import { AreaStatusStrip } from "@/components/practices-design/candidate-a/AreaStatusStrip";
import { ActivityFeedItem } from "@/components/practices-design/candidate-a/ActivityFeedItem";
import {
	type ActivityItem,
	type DeveloperPracticeProfile,
	type PracticeAreaId,
	summarizeAreas,
} from "@/components/practices-design/shared/types";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";

export interface DeveloperActivityViewProps {
	profile: DeveloperPracticeProfile;
	feed: readonly ActivityItem[];
	/** True when workspace totals are hidden below the member threshold. */
	teamContextSuppressed?: boolean;
}

/**
 * Candidate A, developer self view. The developer's home is their own activity feed with
 * practice observations attached inline to the PRs and issues they came from. The area strip
 * on top answers "where do I stand" in one line and doubles as a feed filter. There is no
 * separate reflection page to visit: reflection lives where the work happened.
 */
export function DeveloperActivityView({
	profile,
	feed,
	teamContextSuppressed = false,
}: DeveloperActivityViewProps) {
	const [selectedAreaId, setSelectedAreaId] = useState<PracticeAreaId | null>(null);
	const summaries = summarizeAreas(profile.signals);
	const visibleFeed = selectedAreaId
		? feed.filter((item) =>
				item.observations.some((observation) => observation.areaId === selectedAreaId),
			)
		: feed;

	return (
		<div className="flex w-full max-w-2xl flex-col gap-4">
			<div className="flex flex-col gap-2">
				<h2 className="text-lg font-semibold">Your recent work</h2>
				{summaries.length > 0 ? (
					<AreaStatusStrip
						summaries={summaries}
						selectedAreaId={selectedAreaId}
						onSelectArea={setSelectedAreaId}
					/>
				) : (
					<p className="text-sm text-muted-foreground">
						Practice signals appear here once your first pull requests and issues are in.
					</p>
				)}
				{teamContextSuppressed && (
					<p className="flex items-center gap-1.5 text-xs text-muted-foreground">
						<EyeOff className="size-3.5" aria-hidden="true" />
						Team totals stay hidden until more members are active this cycle. Your own view is
						complete.
					</p>
				)}
			</div>
			{visibleFeed.length > 0 ? (
				<ol className="flex flex-col gap-2" aria-label="Recent activity">
					{visibleFeed.map((item) => (
						<li key={item.id}>
							<ActivityFeedItem item={item} />
						</li>
					))}
				</ol>
			) : (
				<Empty className="border border-dashed">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<Inbox aria-hidden="true" />
						</EmptyMedia>
						<EmptyTitle>
							{selectedAreaId ? "Nothing here for this area yet" : "No activity yet"}
						</EmptyTitle>
						<EmptyDescription>
							{selectedAreaId
								? "Pick another area or clear the filter to see everything."
								: "Your pull requests and issues show up here as you work, with practice signals attached to each one."}
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			)}
		</div>
	);
}

/** Loading placeholder matching the self view's layout: strip, then three feed rows. */
export function DeveloperActivityViewSkeleton() {
	return (
		<div className="flex w-full max-w-2xl flex-col gap-4">
			<div className="flex flex-col gap-2">
				<Skeleton className="h-6 w-40" />
				<div className="flex gap-1.5">
					<Skeleton className="h-7 w-28 rounded-full" />
					<Skeleton className="h-7 w-24 rounded-full" />
					<Skeleton className="h-7 w-32 rounded-full" />
				</div>
			</div>
			<div className="flex flex-col gap-2">
				{[0, 1, 2].map((row) => (
					<div key={row} className="flex flex-col gap-2 rounded-lg border bg-card px-3 py-2.5">
						<Skeleton className="h-4 w-3/4" />
						<Skeleton className="h-5 w-1/2 rounded-full" />
					</div>
				))}
			</div>
		</div>
	);
}
