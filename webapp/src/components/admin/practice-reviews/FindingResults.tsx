import { Link } from "@tanstack/react-router";
import { ChevronRightIcon, ScanSearchIcon } from "lucide-react";
import type { ReviewFinding } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { ReviewArtifact, ReviewArtifactLink } from "./ReviewArtifact";
import { ClaimStatusBadge, FindingAssessmentBadge, FindingFeedbackSummary } from "./ReviewBadges";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLabel } from "./ReviewPracticeLabel";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";

export type FindingResultsState =
	| { status: "loading" }
	| { status: "empty"; filtered: boolean }
	| { status: "ready"; findings: ReviewFinding[] };

export interface FindingResultsProps {
	workspaceSlug: string;
	state: FindingResultsState;
}

export function FindingResults({ workspaceSlug, state }: FindingResultsProps) {
	if (state.status === "loading") return <ReviewResultsSkeleton label="Loading findings" />;
	if (state.status === "empty") {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<ScanSearchIcon />
					</EmptyMedia>
					<EmptyTitle>
						{state.filtered ? "No findings match these filters" : "No findings yet"}
					</EmptyTitle>
					<EmptyDescription>
						{state.filtered
							? "Try removing a filter to broaden the results."
							: "Findings appear after a practice review completes."}
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}
	const { findings } = state;

	return (
		<>
			<div className="hidden xl:block">
				<Table containerClassName="rounded-lg border">
					<TableCaption className="sr-only">Practice review findings, newest first</TableCaption>
					<TableHeader>
						<TableRow>
							<TableHead scope="col">Finding</TableHead>
							<TableHead scope="col">Practice</TableHead>
							<TableHead scope="col">Developer and reviewed work</TableHead>
							<TableHead scope="col" className="w-32">
								Observed
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{findings.map((finding) => (
							<TableRow key={finding.id}>
								<TableCell className="max-w-md whitespace-normal align-top">
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/findings/$findingId"
										params={{ workspaceSlug, findingId: finding.id }}
										search={(previous) => previous}
										className="font-medium hover:underline"
									>
										{finding.title}
									</Link>
									<div className="mt-2">
										<div className="flex flex-wrap gap-2">
											<FindingAssessmentBadge finding={finding} />
											<ClaimStatusBadge status={finding.claimStatus} />
										</div>
									</div>
									<div className="mt-2">
										<FindingFeedbackSummary disposition={finding.feedbackDisposition} />
									</div>
								</TableCell>
								<TableCell className="max-w-56 whitespace-normal align-top">
									<ReviewPracticeLabel area={finding.area} practiceName={finding.practiceName} />
								</TableCell>
								<TableCell className="max-w-xs space-y-2 whitespace-normal align-top">
									<ReviewPerson person={finding.subject} />
									<ReviewArtifactLink artifact={finding.artifact} variant="label" />
								</TableCell>
								<TableCell className="align-top text-muted-foreground">
									<RelativeTime value={finding.observedAt} />
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			</div>
			<ItemGroup className="xl:hidden">
				{findings.map((finding) => (
					<div key={finding.id} role="listitem">
						<Item
							variant="outline"
							className="items-start"
							render={
								<Link
									to="/w/$workspaceSlug/admin/practices/reviews/findings/$findingId"
									params={{ workspaceSlug, findingId: finding.id }}
									search={(previous) => previous}
								/>
							}
						>
							<ItemContent className="min-w-0">
								<ItemTitle className="w-full min-w-0 line-clamp-none break-words">
									{finding.title}
								</ItemTitle>
								<ItemDescription>
									{finding.area?.name ? `${finding.area.name} · ` : ""}
									{finding.practiceName}
								</ItemDescription>
								<div className="mt-1 flex flex-wrap items-center gap-2">
									<FindingAssessmentBadge finding={finding} />
									<ClaimStatusBadge status={finding.claimStatus} />
									<RelativeTime value={finding.observedAt} />
								</div>
								<FindingFeedbackSummary disposition={finding.feedbackDisposition} />
								<ReviewPerson person={finding.subject} display="full" />
								<ReviewArtifact artifact={finding.artifact} display="full" />
							</ItemContent>
							<ItemActions>
								<ChevronRightIcon className="size-4 text-muted-foreground" aria-hidden />
							</ItemActions>
						</Item>
					</div>
				))}
			</ItemGroup>
		</>
	);
}
