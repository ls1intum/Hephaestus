import { Link } from "@tanstack/react-router";
import { Pencil } from "lucide-react";
import { useId } from "react";
import type { Practice, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { CatalogOriginBadge } from "@/components/admin/practices/CatalogOriginBadge";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { buttonVariants } from "@/components/ui/button";
import { DrawerBody, DrawerDescription, DrawerFooter, DrawerTitle } from "@/components/ui/drawer";
import { Item, ItemContent, ItemDescription, ItemGroup, ItemTitle } from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import { inheritedAutonomySourceSentence } from "@/lib/practice-autonomy";
import { cn } from "@/lib/utils";

export type WorkspacePracticeState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| {
			status: "ready";
			practice: Practice;
			definitionOptions: PracticeDefinitionOptions;
			/** The area's display name, for the inheritance sentence. Absent when unassigned. */
			areaName?: string;
	  };

export interface WorkspacePracticePanelProps {
	workspaceSlug: string;
	state: WorkspacePracticeState;
	nested?: boolean;
}

/**
 * A workspace practice, read-only.
 *
 * This exists so that opening a practice is not the same act as editing one. Before it, every route
 * into a practice landed on a 762-line form with a live unsaved-changes guard, which made "what does
 * this practice say?" a question you could only answer by entering a surface you then had to escape
 * from.
 *
 * It renders the **same** {@link PracticeDefinitionPreview} the catalog adoption drawer uses —
 * `Practice` is structurally a `CuratedPracticeDefinition` — so a practice reads identically whether
 * you met it in the library or in your own tree. Editing is one explicit action away, and it is a
 * route, because a form that must ask before discarding your work is not a dismissible surface.
 */
export function WorkspacePracticePanel({
	workspaceSlug,
	state,
	nested,
}: WorkspacePracticePanelProps) {
	// Two levels of the stack can show a practice at once, so nothing here may hold a fixed DOM id.
	const idPrefix = useId();
	if (state.status !== "ready") {
		return (
			<>
				<DetailDrawerHeader nested={nested}>
					<DrawerTitle>Practice</DrawerTitle>
				</DetailDrawerHeader>
				<DrawerBody>
					{state.status === "loading" ? (
						<div className="flex min-h-32 items-center justify-center" role="status">
							<Spinner />
							<span className="sr-only">Loading practice</span>
						</div>
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
	const follows = inheritedAutonomySourceSentence(practice.autonomy, areaName ?? null);

	return (
		<>
			<DetailDrawerHeader nested={nested}>
				<AreaPill size="lg" slug={practice.areaSlug} name={areaName} />
				<div className="min-w-0 flex-1 space-y-0.5">
					<DrawerTitle className="break-words">{practice.name}</DrawerTitle>
					<DrawerDescription>{artifactKindLabel(practice.artifactKind)}</DrawerDescription>
				</div>
				<CatalogOriginBadge origin={practice.catalogOrigin} kind="practice" />
			</DetailDrawerHeader>

			<DrawerBody className="space-y-6">
				<ItemGroup className="gap-2">
					<Item variant="muted" size="sm" role="listitem">
						<ItemContent>
							<ItemTitle>
								<StatusBadge def={autonomy} />
							</ItemTitle>
							<ItemDescription className="line-clamp-none">
								{follows ? `${follows}. ${autonomy.description}` : autonomy.description}
							</ItemDescription>
						</ItemContent>
					</Item>
				</ItemGroup>

				<Separator />

				<PracticeDefinitionPreview
					definition={practice}
					options={definitionOptions}
					idPrefix={idPrefix}
				/>
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
