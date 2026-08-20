import { Link } from "@tanstack/react-router";
import { Check, ChevronRight, CircleAlert, CircleDashed, Library } from "lucide-react";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { Badge } from "@/components/ui/badge";
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
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";

export interface AvailablePracticeListProps {
	workspaceSlug: string;
	practices: CatalogPracticeSummary[];
}

export function AvailablePracticeList({ workspaceSlug, practices }: AvailablePracticeListProps) {
	if (practices.length === 0) {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Library />
					</EmptyMedia>
					<EmptyTitle>No practices available</EmptyTitle>
					<EmptyDescription>
						The instance catalog does not currently offer any practices to this workspace.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<ItemGroup>
			{practices.map((practice) => (
				<div key={practice.slug} role="listitem">
					<PracticeItem workspaceSlug={workspaceSlug} practice={practice} />
				</div>
			))}
		</ItemGroup>
	);
}

function PracticeItem({
	workspaceSlug,
	practice,
}: {
	workspaceSlug: string;
	practice: CatalogPracticeSummary;
}) {
	const areaName = practice.areaName ?? "Unassigned";
	const areaVisual = practice.areaSlug
		? getAreaVisual(practice.areaSlug, areaName)
		: { Icon: CircleDashed, pill: "bg-muted text-muted-foreground" };
	const { Icon, pill } = areaVisual;
	const link =
		practice.availability === "ADOPTED" ? (
			<Link
				to="/w/$workspaceSlug/admin/practices/$practiceSlug"
				params={{ workspaceSlug, practiceSlug: practice.slug }}
			/>
		) : (
			<Link
				to="/w/$workspaceSlug/admin/practices/available/$catalogSlug"
				params={{ workspaceSlug, catalogSlug: practice.slug }}
			/>
		);

	return (
		<Item variant="outline" render={link} aria-label={`${practice.name}, ${actionLabel(practice)}`}>
			<ItemMedia className={cn("size-9 rounded-md", pill)} aria-hidden="true">
				<Icon className="size-4" />
			</ItemMedia>
			<ItemContent className="min-w-0">
				<ItemTitle className="line-clamp-none break-words">{practice.name}</ItemTitle>
				<ItemDescription className="flex flex-wrap items-center gap-x-2 gap-y-1 line-clamp-none">
					<span>{artifactKindLabel(practice.artifactKind)}</span>
					<span aria-hidden="true">·</span>
					<span>{areaName}</span>
				</ItemDescription>
			</ItemContent>
			<ItemActions>
				<AvailabilityBadge availability={practice.availability} />
				<ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
			</ItemActions>
		</Item>
	);
}

function actionLabel(practice: CatalogPracticeSummary): string {
	if (practice.availability === "ADOPTED") return "open workspace practice, added";
	if (practice.availability === "SLUG_CONFLICT") return "view details, name unavailable";
	return "review for adoption";
}

function AvailabilityBadge({
	availability,
}: {
	availability: CatalogPracticeSummary["availability"];
}) {
	if (availability === "ADOPTED") {
		return (
			<Badge variant="secondary">
				<Check /> Added
			</Badge>
		);
	}
	if (availability === "SLUG_CONFLICT") {
		return (
			<Badge variant="outline">
				<CircleAlert /> Name unavailable
			</Badge>
		);
	}
	return null;
}
