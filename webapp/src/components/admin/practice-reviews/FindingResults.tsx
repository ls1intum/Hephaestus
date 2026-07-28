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
import { Skeleton } from "@/components/ui/skeleton";
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
import { FindingAssessmentBadge, FindingFeedbackSummary } from "./ReviewBadges";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLabel } from "./ReviewPracticeLabel";

export type FindingResultsState =
	| { status: "loading" }
	| { status: "empty"; filtered: boolean }
	| { status: "ready"; findings: ReviewFinding[] };

export interface FindingResultsProps {
	workspaceSlug: string;
	state: FindingResultsState;
}

export function FindingResults({ workspaceSlug, state }: FindingResultsProps) {
	if (state.status === "loading") return <FindingsListSkeleton />;
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
										className="line-clamp-2 font-medium hover:underline"
									>
										{finding.title}
									</Link>
									<div className="mt-2">
										<FindingAssessmentBadge finding={finding} />
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
								<ItemTitle className="w-full min-w-0 line-clamp-2 break-words">
									{finding.title}
								</ItemTitle>
								<ItemDescription>
									{finding.area?.name ? `${finding.area.name} · ` : ""}
									{finding.practiceName}
								</ItemDescription>
								<div className="mt-1 flex flex-wrap items-center gap-2">
									<FindingAssessmentBadge finding={finding} />
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

function FindingsListSkeleton() {
	return (
		<div className="space-y-2 rounded-lg border p-4" role="status">
			<span className="sr-only">Loading findings</span>
			{Array.from({ length: 5 }, (_, index) => (
				<div key={index} className="flex items-center gap-4 py-3">
					<Skeleton className="h-4 flex-1" />
					<Skeleton className="h-5 w-24" />
					<Skeleton className="hidden h-4 w-48 md:block" />
				</div>
			))}
		</div>
	);
}
