import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, Users } from "lucide-react";
import { useEffect, useState } from "react";
import { getDeveloperPracticeReportOptions } from "@/api/@tanstack/react-query.gen";
import type { PracticeReportSummary } from "@/api/types.gen";
import { PracticeReflectionCard } from "@/components/practices/reflection/PracticeReflectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";

export interface DeveloperDrillDownDialogProps {
	workspaceSlug: string;
	developer: PracticeReportSummary | null;
	onClose: () => void;
}

export function DeveloperDrillDownDialog({
	workspaceSlug,
	developer,
	onClose,
}: DeveloperDrillDownDialogProps) {
	const open = developer !== null;

	// Remember the last non-null developer in state (updated in an effect — never during render,
	// which the React Compiler forbids) so the content stays populated through the close animation.
	// While open, `developer` is used directly, so the first open never renders stale state.
	const [lastDeveloper, setLastDeveloper] = useState<PracticeReportSummary | null>(null);
	useEffect(() => {
		if (developer) setLastDeveloper(developer);
	}, [developer]);
	const displayDeveloper = developer ?? lastDeveloper;
	// Key the query on the DISPLAYED developer, not the raw prop: when the dialog closes the prop
	// goes null, and keying on it would switch the query (and its cached data) away mid-animation,
	// flashing the empty state over the still-visible content.
	const userId = displayDeveloper?.userId;

	const reflectionQuery = useQuery({
		...getDeveloperPracticeReportOptions({ path: { workspaceSlug, userId: userId ?? 0 } }),
		enabled: open && userId != null,
		refetchOnWindowFocus: false,
		refetchOnReconnect: false,
		retry: false,
	});

	const practices = reflectionQuery.data ?? [];
	const displayName = displayDeveloper?.name ?? displayDeveloper?.userLogin ?? "developer";
	const isLoading = open && userId != null && reflectionQuery.isPending;
	const isError = open && reflectionQuery.isError;

	const handleRetry = () => {
		if (reflectionQuery.isError) reflectionQuery.refetch();
	};

	return (
		<Dialog
			open={open}
			onOpenChange={(next) => {
				if (!next) onClose();
			}}
		>
			<DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
				<DialogHeader>
					<DialogTitle>Mentoring view — {displayName}</DialogTitle>
					<DialogDescription>
						A read-only look at {displayName}&apos;s practice feedback to help you mentor.
					</DialogDescription>
				</DialogHeader>

				{isLoading ? (
					<div className="flex flex-col gap-4">
						<Skeleton className="h-40 w-full" />
						<Skeleton className="h-40 w-full" />
					</div>
				) : isError ? (
					<EmptyState
						icon={AlertTriangle}
						title="Couldn't load this developer's feedback"
						description="Something went wrong loading the reflection. This is usually temporary — try again."
						action={
							<Button variant="outline" onClick={handleRetry}>
								Retry
							</Button>
						}
					/>
				) : practices.length === 0 ? (
					<EmptyState
						icon={Users}
						title="No recent practice feedback"
						description={`${displayName} has no practice feedback in the current review cycle.`}
					/>
				) : (
					<div className="flex flex-col gap-4">
						{practices.map((practice) => (
							<PracticeReflectionCard key={practice.slug} practice={practice} />
						))}
					</div>
				)}
			</DialogContent>
		</Dialog>
	);
}
