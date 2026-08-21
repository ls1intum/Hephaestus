import { Link } from "@tanstack/react-router";
import { Check, ChevronRight, CircleAlert, CircleDashed, Library } from "lucide-react";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { DetailStackLink } from "@/components/core/detail-drawer/DetailStackLink";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
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
	groupByArea?: boolean;
	hideAdopted?: boolean;
	existingAreaSlugs?: ReadonlySet<string>;
}

export function AvailablePracticeList({
	workspaceSlug,
	practices,
	groupByArea = false,
	hideAdopted = false,
	existingAreaSlugs = new Set(),
}: AvailablePracticeListProps) {
	const visiblePractices = hideAdopted
		? practices.filter(
				(practice) =>
					practice.availability !== "ADOPTED" ||
					(practice.areaSlug !== undefined && !existingAreaSlugs.has(practice.areaSlug)),
			)
		: practices;
	if (visiblePractices.length === 0) {
		const allAdded = hideAdopted && practices.length > 0;
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Library />
					</EmptyMedia>
					<EmptyTitle>
						{allAdded ? "Everything is already added" : "No practices available"}
					</EmptyTitle>
					<EmptyDescription>
						{allAdded
							? "This workspace already has every practice currently offered in the library."
							: "The instance catalog does not currently offer any practices to this workspace."}
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}
	if (groupByArea) {
		const groups = new Map<string, CatalogPracticeSummary[]>();
		for (const practice of visiblePractices) {
			const areaKey = practice.areaSlug ?? "__unassigned__";
			groups.set(areaKey, [...(groups.get(areaKey) ?? []), practice]);
		}
		return (
			<div className="space-y-4">
				{Array.from(groups, ([areaKey, entries]) => {
					const areaName = entries[0]?.areaName ?? "Unassigned";
					const areaSlug = entries[0]?.areaSlug;
					const availableCount = entries.filter(
						(entry) => entry.availability === "AVAILABLE",
					).length;
					const restoreCount = entries.filter((entry) => entry.availability === "ADOPTED").length;
					const areaMissing = areaSlug !== undefined && !existingAreaSlugs.has(areaSlug);
					return (
						<section
							key={areaKey}
							className="space-y-2"
							aria-labelledby={`library-${entries[0].areaSlug ?? "unassigned"}`}
						>
							<div className="flex flex-wrap items-center justify-between gap-3">
								<h3 id={`library-${entries[0].areaSlug ?? "unassigned"}`} className="font-medium">
									{areaName}
								</h3>
								{areaSlug && (availableCount > 0 || (areaMissing && restoreCount > 0)) ? (
									<DetailStackLink
										entry={{ kind: "area", id: areaSlug }}
										className={buttonVariants({ size: "sm", variant: "outline" })}
									>
										{areaMissing && availableCount === 0
											? `Restore area · ${restoreCount} ${restoreCount === 1 ? "practice" : "practices"}`
											: existingAreaSlugs.has(areaSlug)
												? `Review ${availableCount} ${availableCount === 1 ? "practice" : "practices"}`
												: `Review area · ${availableCount} ${availableCount === 1 ? "practice" : "practices"}`}
									</DetailStackLink>
								) : (
									<span className="text-xs text-muted-foreground">
										{entries.length} {entries.length === 1 ? "practice" : "practices"}
									</span>
								)}
							</div>
							<PracticeItems workspaceSlug={workspaceSlug} practices={entries} />
						</section>
					);
				})}
			</div>
		);
	}
	return <PracticeItems workspaceSlug={workspaceSlug} practices={visiblePractices} />;
}

function PracticeItems({ workspaceSlug, practices }: AvailablePracticeListProps) {
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
	// An adopted practice leaves the library for its workspace copy; everything else opens over it.
	const link =
		practice.availability === "ADOPTED" ? (
			<Link
				to="/w/$workspaceSlug/admin/practices/$practiceSlug"
				params={{ workspaceSlug, practiceSlug: practice.slug }}
				search={{}}
			/>
		) : (
			<DetailStackLink entry={{ kind: "practice", id: practice.slug }} />
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
