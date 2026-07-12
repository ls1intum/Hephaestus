import { AlertTriangle, Sprout } from "lucide-react";
import type { PracticeReportCard } from "@/api/types.gen";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { CopyPracticeSummaryButton } from "./CopyPracticeSummaryButton";
import { PracticeReflectionCard } from "./PracticeReflectionCard";

export interface PracticeReflectionSectionProps {
	practices?: PracticeReportCard[];
	isLoading: boolean;
	isError?: boolean;
	onRetry?: () => void;
}

function ReflectionCardSkeleton() {
	return (
		<Card>
			<CardHeader className="gap-2">
				<div className="flex items-center justify-between">
					<Skeleton className="h-5 w-40" />
					<Skeleton className="h-5 w-20 rounded-full" />
				</div>
				<Skeleton className="h-4 w-3/4" />
			</CardHeader>
			<CardContent className="flex flex-col gap-3">
				<Skeleton className="h-14 w-full" />
				<Skeleton className="h-14 w-full" />
			</CardContent>
		</Card>
	);
}

/**
 * The authenticated developer's own practice reflection, rendered as a section of the unified
 * developer home. Non-competitive by design: recent feedback on the developer's own work, for their
 * growth, never a score or a rank. Fed exclusively by the server-gated `GET /practices/reports/me`,
 * so it can only ever show the caller's own cards.
 */
export function PracticeReflectionSection({
	practices,
	isLoading,
	isError = false,
	onRetry,
}: PracticeReflectionSectionProps) {
	const items = practices ?? [];

	return (
		<div className="flex flex-col gap-6">
			<div className="flex flex-col gap-1">
				<div className="flex flex-wrap items-start justify-between gap-3">
					<div className="flex flex-col gap-1">
						<h2 className="text-xl font-semibold tracking-tight">My Practices</h2>
						<p className="text-sm text-muted-foreground">
							Recent feedback on your work, for your growth.
						</p>
					</div>
					{!isLoading && !isError && items.length > 0 && (
						<CopyPracticeSummaryButton practices={items} />
					)}
				</div>
				<p className="text-xs text-muted-foreground">
					Workspace admins can see your practice status to support mentoring, and every detailed
					view is recorded.
				</p>
			</div>

			{isLoading ? (
				<div className="flex flex-col gap-4">
					<ReflectionCardSkeleton />
					<ReflectionCardSkeleton />
				</div>
			) : isError ? (
				<EmptyState
					icon={AlertTriangle}
					title="Couldn't load your practice feedback"
					description="Something went wrong loading your reflection. This is usually temporary, so try again."
					action={
						onRetry ? (
							<Button variant="outline" onClick={onRetry}>
								Retry
							</Button>
						) : undefined
					}
				/>
			) : items.length === 0 ? (
				<EmptyState
					icon={Sprout}
					title="No recent practice feedback yet"
					description="As your pull requests and issues are reviewed, feedback for your growth will show up here."
				/>
			) : (
				<div className="flex flex-col gap-4">
					{items.map((practice) => (
						<PracticeReflectionCard key={practice.slug} practice={practice} />
					))}
				</div>
			)}
		</div>
	);
}
