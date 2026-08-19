import { Link } from "@tanstack/react-router";
import { Check, CircleAlert } from "lucide-react";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { PracticeAutomatedReviewValidationSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import {
	Card,
	CardAction,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { artifactKindLabel } from "@/lib/artifact-kinds";

export interface AvailablePracticeListProps {
	workspaceSlug: string;
	practices: CatalogPracticeSummary[];
}

export function AvailablePracticeList({ workspaceSlug, practices }: AvailablePracticeListProps) {
	if (practices.length === 0) {
		return (
			<p className="rounded-lg border border-dashed p-8 text-center text-muted-foreground">
				No practices are currently offered.
			</p>
		);
	}
	return (
		<ul className="grid gap-4 lg:grid-cols-2">
			{practices.map((practice) => (
				<li key={practice.slug}>
					<Card className="h-full">
						<CardHeader>
							<CardTitle>
								<h2>{practice.name}</h2>
							</CardTitle>
							<CardDescription>{artifactKindLabel(practice.artifactKind)}</CardDescription>
							<CardAction>
								<AvailabilityBadge availability={practice.availability} />
							</CardAction>
						</CardHeader>
						<CardContent className="flex flex-wrap items-end justify-between gap-3">
							<div className="space-y-2">
								<p className="text-sm text-muted-foreground">
									Area: {practice.areaName ?? "Unassigned"}
								</p>
								<PracticeAutomatedReviewValidationSummary
									validation={practice.automatedReviewValidation}
								/>
							</div>
							<Link
								to="/w/$workspaceSlug/admin/practices/available/$catalogSlug"
								params={{ workspaceSlug, catalogSlug: practice.slug }}
								className={buttonVariants({ variant: "outline", size: "sm" })}
							>
								Review practice<span className="sr-only">: {practice.name}</span>
							</Link>
						</CardContent>
					</Card>
				</li>
			))}
		</ul>
	);
}

function AvailabilityBadge({
	availability,
}: {
	availability: CatalogPracticeSummary["availability"];
}) {
	if (availability === "ADOPTED") {
		return (
			<Badge variant="secondary">
				<Check /> Adopted
			</Badge>
		);
	}
	if (availability === "SLUG_CONFLICT") {
		return (
			<Badge variant="destructive">
				<CircleAlert /> Slug conflict
			</Badge>
		);
	}
	return <Badge>Available</Badge>;
}
