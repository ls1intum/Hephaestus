import { ChevronRight, Library } from "lucide-react";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { DetailStackLink } from "@/components/core/detail-drawer/DetailStackLink";
import { Section } from "@/components/core/Section";
import { CATALOG_AVAILABILITY_DEFS } from "@/components/practice-vocabulary/catalog-availability-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
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

export interface AvailablePracticeListProps {
	practices: CatalogPracticeSummary[];
	/**
	 * The areas the workspace already has. An area missing from this set is one the library can
	 * offer back, which is why its practices stay listed even after they have been added.
	 */
	existingAreaSlugs: ReadonlySet<string>;
}

/**
 * What the instance catalog still has to offer this workspace, grouped by the area each entry would
 * land in. A practice already added into an area the workspace still has is not offered again; one
 * whose area was deleted stays, because the area is what the library can put back.
 */
export function AvailablePracticeList({
	practices,
	existingAreaSlugs,
}: AvailablePracticeListProps) {
	const offered = practices.filter(
		(practice) =>
			practice.availability !== "ADOPTED" ||
			(practice.areaSlug !== undefined && !existingAreaSlugs.has(practice.areaSlug)),
	);

	if (offered.length === 0) {
		const allAdded = practices.length > 0;
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Library />
					</EmptyMedia>
					<EmptyTitle>{allAdded ? "Everything is already added" : "Nothing to add"}</EmptyTitle>
					<EmptyDescription>
						{allAdded
							? "This workspace already has every practice the library currently offers."
							: "The instance catalog does not currently offer any practices to this workspace."}
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	const groups = new Map<string, CatalogPracticeSummary[]>();
	for (const practice of offered) {
		const key = practice.areaSlug ?? "__unassigned__";
		groups.set(key, [...(groups.get(key) ?? []), practice]);
	}

	return (
		<div className="space-y-5">
			{Array.from(groups, ([key, entries]) => {
				const first = entries[0];
				const areaSlug = first.areaSlug;
				const available = entries.filter(({ availability }) => availability === "AVAILABLE").length;
				const restorable = entries.filter(({ availability }) => availability === "ADOPTED").length;
				const areaMissing = areaSlug !== undefined && !existingAreaSlugs.has(areaSlug);

				return (
					<Section
						key={key}
						size="sm"
						level={3}
						title={
							<span className="flex items-center gap-2">
								<AreaPill size="sm" slug={areaSlug} name={first.areaName} />
								{first.areaName ?? "No area"}
							</span>
						}
						actions={
							areaSlug && (available > 0 || (areaMissing && restorable > 0)) ? (
								<DetailStackLink
									entry={{ kind: "catalog-area", id: areaSlug }}
									className={buttonVariants({ size: "sm", variant: "outline" })}
								>
									{areaMissing && available === 0
										? `Restore area · ${countLabel(restorable)}`
										: existingAreaSlugs.has(areaSlug)
											? `Review ${countLabel(available)}`
											: `Review area · ${countLabel(available)}`}
								</DetailStackLink>
							) : (
								<span className="text-xs text-muted-foreground">{countLabel(entries.length)}</span>
							)
						}
					>
						<ItemGroup>
							{entries.map((practice) => (
								<div key={practice.slug} role="listitem">
									<PracticeRow practice={practice} />
								</div>
							))}
						</ItemGroup>
					</Section>
				);
			})}
		</div>
	);
}

function countLabel(count: number): string {
	return `${count} ${count === 1 ? "practice" : "practices"}`;
}

function PracticeRow({ practice }: { practice: CatalogPracticeSummary }) {
	const def = CATALOG_AVAILABILITY_DEFS[practice.availability];
	// An added practice opens the workspace copy, everything else the catalog preview — but both
	// open as a drawer level, so the library never has to be left to look at something in it.
	const link =
		practice.availability === "ADOPTED" ? (
			<DetailStackLink entry={{ kind: "practice", id: practice.slug }} />
		) : (
			<DetailStackLink entry={{ kind: "catalog-practice", id: practice.slug }} />
		);

	return (
		// No `aria-label` on the row: one would replace everything below for a screen reader, taking
		// the work type and the area with it. The visible text is the accessible name, and the
		// registry's second grammatical form is appended for the part a sighted reader gets from
		// the chevron.
		<Item variant="outline" render={link}>
			<ItemMedia variant="icon" className="bg-transparent">
				<AreaPill size="md" slug={practice.areaSlug} name={practice.areaName} />
			</ItemMedia>
			<ItemContent className="min-w-0">
				<ItemTitle className="line-clamp-none break-words">
					{practice.name}
					<span className="sr-only">, {def.action}</span>
				</ItemTitle>
				<ItemDescription className="line-clamp-none">
					{artifactKindLabel(practice.artifactKind)}
				</ItemDescription>
				{/* Without this the rows differ only by name: 20 of the 37 bundled practices review a
				    pull request, so the work type separates almost none of them. */}
				{practice.whyItMatters && (
					<ItemDescription className="line-clamp-2 text-pretty">
						{practice.whyItMatters}
					</ItemDescription>
				)}
			</ItemContent>
			<ItemActions>
				{def.badged && <StatusBadge def={def} />}
				<ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
			</ItemActions>
		</Item>
	);
}
