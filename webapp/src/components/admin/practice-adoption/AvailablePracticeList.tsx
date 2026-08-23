import { ChevronRight, Library } from "lucide-react";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { DetailStackLink } from "@/components/core/detail-drawer/DetailStackLink";
import { Section } from "@/components/core/Section";
import { CATALOG_AVAILABILITY_DEFS } from "@/components/practice-vocabulary/catalog-availability-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { WorkTypeLabel } from "@/components/practice-vocabulary/WorkTypeLabel";
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

export interface AvailablePracticeListProps {
	practices: CatalogPracticeSummary[];
	/**
	 * The areas the workspace already has. An area missing from this set is one the catalog can
	 * put back, which is why its practices stay listed even after they have been added.
	 */
	existingAreaSlugs: ReadonlySet<string>;
}

/**
 * What the instance catalog still has to offer this workspace, grouped by the area each entry would
 * land in. A practice already added into an area the workspace still has is not offered again; one
 * whose group was deleted stays, because the group is what the catalog can put back.
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
							? "This workspace already has every practice the catalog currently includes."
							: "The instance catalog includes no practices for this workspace yet."}
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
				// A group exists because something was filed under it, so the first entry is what names
				// it — an empty one would be a group nothing put anything in.
				const [first] = entries;
				if (!first) return null;
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
								{first.areaName ?? "Unassigned"}
							</span>
						}
						actions={
							areaSlug && (available > 0 || (areaMissing && restorable > 0)) ? (
								<DetailStackLink
									entry={{ kind: "catalog-area", id: areaSlug }}
									className={buttonVariants({ size: "sm", variant: "outline" })}
								>
									{areaMissing && available === 0
										? `Restore group · ${countLabel(restorable)}`
										: existingAreaSlugs.has(areaSlug)
											? `Review ${countLabel(available)}`
											: `Review group · ${countLabel(available)}`}
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
	// Both open as a drawer level, so the catalog never has to be left to look at something in it.
	const link =
		practice.availability === "ADOPTED" ? (
			<DetailStackLink entry={{ kind: "practice", id: practice.slug }} />
		) : (
			<DetailStackLink entry={{ kind: "catalog-practice", id: practice.slug }} />
		);

	return (
		// No `aria-label`: it would replace the work type and group below it for a screen reader. The
		// visible text is the name; the registry's verb phrase stands in for the chevron.
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
					<WorkTypeLabel artifactKind={practice.artifactKind} />
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
