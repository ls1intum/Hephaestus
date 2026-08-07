import { useQuery } from "@tanstack/react-query";
import { FileQuestionIcon } from "lucide-react";
import {
	listPracticeReviewFeedbackOptions,
	listPracticeReviewFindingsOptions,
} from "@/api/@tanstack/react-query.gen";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import type { KnownArtifactKind } from "@/lib/artifact-kinds";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewOutputSections } from "./ReviewOutputSections";

export interface ReviewTargetPageProps {
	workspaceSlug: string;
	artifactKind: KnownArtifactKind;
	artifactId: number;
}

export function ReviewTargetPage({
	workspaceSlug,
	artifactKind,
	artifactId,
}: ReviewTargetPageProps) {
	const scope = { artifactKind, artifactId };
	const feedbackQuery = useQuery({
		...listPracticeReviewFeedbackOptions({
			path: { workspaceSlug },
			query: { ...scope, size: 5 },
		}),
	});
	const findingsQuery = useQuery({
		...listPracticeReviewFindingsOptions({
			path: { workspaceSlug },
			query: { ...scope, size: 5 },
		}),
	});
	const feedback = feedbackQuery.data?.content ?? [];
	const findings = findingsQuery.data?.content ?? [];
	const artifact = feedback[0]?.artifact ?? findings[0]?.artifact;
	const noOutput =
		!feedbackQuery.isLoading &&
		!findingsQuery.isLoading &&
		!feedbackQuery.isError &&
		!findingsQuery.isError &&
		feedback.length === 0 &&
		findings.length === 0;

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			<ReviewBreadcrumbs workspaceSlug={workspaceSlug} current="Reviewed work" />
			{noOutput ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<FileQuestionIcon />
						</EmptyMedia>
						<EmptyTitle>No review output found</EmptyTitle>
						<EmptyDescription>No findings or feedback are recorded for this work.</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<>
					<header className="space-y-3">
						<div className="space-y-1">
							<p className="text-sm font-medium text-muted-foreground">Reviewed work</p>
							{!artifact && (feedbackQuery.isLoading || findingsQuery.isLoading) ? (
								<Skeleton className="h-8 w-72 max-w-full" />
							) : (
								<h2 className="break-words text-2xl font-semibold tracking-tight">
									{artifact?.title ?? "Review output"}
								</h2>
							)}
							<p className="text-sm text-muted-foreground">
								Findings and feedback recorded across reviews of this work.
							</p>
						</div>
						{artifact && <ReviewArtifactLink artifact={artifact} variant="label" display="full" />}
					</header>

					<ReviewOutputSections
						workspaceSlug={workspaceSlug}
						scope={scope}
						context="target"
						feedback={
							feedbackQuery.isLoading
								? { status: "loading" }
								: feedbackQuery.isError
									? {
											status: "error",
											error: feedbackQuery.error,
											onRetry: () => void feedbackQuery.refetch(),
										}
									: {
											status: "ready",
											items: feedback,
											total: feedbackQuery.data?.page?.totalElements ?? 0,
										}
						}
						findings={
							findingsQuery.isLoading
								? { status: "loading" }
								: findingsQuery.isError
									? {
											status: "error",
											error: findingsQuery.error,
											onRetry: () => void findingsQuery.refetch(),
										}
									: {
											status: "ready",
											items: findings,
											total: findingsQuery.data?.page?.totalElements ?? 0,
										}
						}
					/>
				</>
			)}
		</article>
	);
}
