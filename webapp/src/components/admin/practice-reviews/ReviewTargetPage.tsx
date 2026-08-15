import { useQuery } from "@tanstack/react-query";
import { FileQuestionIcon } from "lucide-react";
import {
	listPracticeReviewFeedbackOptions,
	listPracticeReviewObservationsOptions,
} from "@/api/@tanstack/react-query.gen";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { artifactKindLabel, type KnownArtifactKind } from "@/lib/artifact-kinds";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewDetailHeader } from "./ReviewDetailHeader";
import { REVIEW_PREVIEW_SIZE, ReviewOutputSections } from "./ReviewOutputSections";

export interface ReviewTargetPageProps {
	workspaceSlug: string;
	artifactKind: KnownArtifactKind;
	artifactId: number;
}

/**
 * No eyebrow above the heading: the link's own mark and words say what kind of work this is, which
 * is a fact about *this* work, while a label for the page restates the breadcrumb one line above it.
 */
export function ReviewTargetPage({
	workspaceSlug,
	artifactKind,
	artifactId,
}: ReviewTargetPageProps) {
	const scope = { artifactKind, artifactId };
	const feedbackQuery = useQuery({
		...listPracticeReviewFeedbackOptions({
			path: { workspaceSlug },
			query: { ...scope, size: REVIEW_PREVIEW_SIZE },
		}),
	});
	const observationsQuery = useQuery({
		...listPracticeReviewObservationsOptions({
			path: { workspaceSlug },
			query: { ...scope, size: REVIEW_PREVIEW_SIZE },
		}),
	});
	const feedback = feedbackQuery.data?.content ?? [];
	const observations = observationsQuery.data?.content ?? [];
	const artifact = feedback[0]?.artifact ?? observations[0]?.artifact;
	const stillLoading = feedbackQuery.isLoading || observationsQuery.isLoading;
	const noOutput =
		!stillLoading &&
		!feedbackQuery.isError &&
		!observationsQuery.isError &&
		feedback.length === 0 &&
		observations.length === 0;

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			<ReviewBreadcrumbs workspaceSlug={workspaceSlug} />
			{noOutput ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<FileQuestionIcon />
						</EmptyMedia>
						<EmptyTitle>Nothing has been reviewed on this work</EmptyTitle>
						<EmptyDescription>
							No observations or feedback are recorded against it. Either no review has run, or the
							practices in this workspace do not apply to{" "}
							{artifactKindLabel(artifactKind).toLowerCase()}s.
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<>
					<ReviewDetailHeader
						title={
							!artifact && stillLoading ? (
								<Skeleton className="h-8 w-72 max-w-full" />
							) : (
								(artifact?.title ?? artifactKindLabel(artifactKind))
							)
						}
						provenance={artifact && <ReviewArtifactLink artifact={artifact} className="text-sm" />}
					/>

					<ReviewOutputSections
						workspaceSlug={workspaceSlug}
						scope={scope}
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
						observations={
							observationsQuery.isLoading
								? { status: "loading" }
								: observationsQuery.isError
									? {
											status: "error",
											error: observationsQuery.error,
											onRetry: () => void observationsQuery.refetch(),
										}
									: {
											status: "ready",
											items: observations,
											total: observationsQuery.data?.page?.totalElements ?? 0,
										}
						}
					/>
				</>
			)}
		</article>
	);
}
