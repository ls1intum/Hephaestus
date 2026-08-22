import { Link } from "@tanstack/react-router";
import { Pencil } from "lucide-react";
import type { Practice, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { CatalogOriginBadge } from "@/components/admin/practices/CatalogOriginBadge";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import { AutonomySourceNote } from "@/components/practice-vocabulary/AutonomySourceNote";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { WorkTypeLabel } from "@/components/practice-vocabulary/WorkTypeLabel";
import { buttonVariants } from "@/components/ui/button";
import { DrawerBody, DrawerDescription, DrawerFooter, DrawerTitle } from "@/components/ui/drawer";
import { Item, ItemContent, ItemDescription, ItemGroup, ItemTitle } from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";
import { autonomySourceOf } from "@/lib/practice-autonomy";
import { cn } from "@/lib/utils";

export type WorkspacePracticeState = PanelState<{
	practice: Practice;
	definitionOptions: PracticeDefinitionOptions;
	/** The group's display name, for the inheritance sentence. Absent when unassigned. */
	areaName?: string;
}>;

export interface WorkspacePracticePanelProps {
	workspaceSlug: string;
	state: WorkspacePracticeState;
	nested?: boolean;
}

/**
 * A workspace practice, read-only, so that opening one is not the same act as editing it.
 *
 * Renders the same {@link PracticeDefinitionPreview} the catalog drawer uses — `Practice` is
 * structurally a `CuratedPracticeDefinition` — so a practice reads identically whether it was met in
 * the library or in the workspace's own tree. Editing stays a route: a form that must ask before
 * discarding work is not a dismissible surface.
 */
export function WorkspacePracticePanel({
	workspaceSlug,
	state,
	nested,
}: WorkspacePracticePanelProps) {
	if (state.status !== "ready") {
		return (
			<>
				<DetailDrawerHeader nested={nested}>
					<DrawerTitle>Practice</DrawerTitle>
				</DetailDrawerHeader>
				<DrawerBody>
					{state.status === "loading" ? (
						<PracticeDefinitionSkeleton />
					) : (
						<QueryErrorAlert
							error={state.error}
							title="Couldn't load this practice"
							onRetry={state.onRetry}
						/>
					)}
				</DrawerBody>
			</>
		);
	}

	const { practice, definitionOptions, areaName } = state;
	const autonomy = AUTONOMY_DEFS[practice.autonomy.effective];
	const autonomySource = autonomySourceOf(practice.autonomy, areaName ?? null);

	return (
		<>
			<DetailDrawerHeader nested={nested}>
				<AreaPill size="lg" slug={practice.areaSlug} name={areaName} />
				<div className="min-w-0 flex-1 space-y-0.5">
					<DrawerTitle className="break-words">{practice.name}</DrawerTitle>
					<DrawerDescription>
						<WorkTypeLabel artifactKind={practice.artifactKind} />
					</DrawerDescription>
				</div>
				<CatalogOriginBadge origin={practice.catalogOrigin} kind="practice" />
			</DetailDrawerHeader>

			<DrawerBody className="space-y-6">
				<ItemGroup className="gap-2">
					<Item variant="muted" size="sm" role="listitem">
						<ItemContent>
							<ItemTitle className="flex flex-wrap items-center gap-2">
								<StatusBadge def={autonomy} />
								<AutonomySourceNote
									source={autonomySource}
									className="text-muted-foreground text-xs font-normal"
								/>
							</ItemTitle>
							<ItemDescription className="line-clamp-none">{autonomy.description}</ItemDescription>
						</ItemContent>
					</Item>
				</ItemGroup>

				<Separator />

				<PracticeDefinitionPreview definition={practice} options={definitionOptions} />
			</DrawerBody>

			<DrawerFooter>
				<Link
					to="/w/$workspaceSlug/admin/practices/$practiceSlug"
					params={{ workspaceSlug, practiceSlug: practice.slug }}
					search={{}}
					className={cn(buttonVariants(), "w-full sm:w-auto")}
				>
					<Pencil /> Edit practice
				</Link>
			</DrawerFooter>
		</>
	);
}
