import type { Practice, PracticeDefinitionOptions } from "@/api/types.gen";
import { CatalogOriginNote } from "@/components/admin/practices/CatalogOriginBadge";
import { PracticeDefinitionPreview } from "@/components/admin/practices/PracticeDefinitionPreview";
import { StatusBadge } from "@/components/common/StatusBadge";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import { AutonomySourceNote } from "@/components/practice-vocabulary/AutonomySourceNote";
import { Item, ItemContent, ItemDescription, ItemGroup, ItemTitle } from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";
import { autonomySourceOf } from "@/lib/practice-autonomy";

export interface WorkspacePracticeDefinitionProps {
	practice: Practice;
	definitionOptions: PracticeDefinitionOptions;
	/** The group's display name, for the inheritance sentence. Absent when unassigned. */
	groupName?: string;
	/**
	 * Where it came from and how much autonomy it has, above the definition. Off where a header
	 * already says the autonomy, so it is said once.
	 */
	showStanding?: boolean;
}

/**
 * A workspace practice's full definition, read-only: where it came from, its autonomy, and the
 * {@link PracticeDefinitionPreview} the catalog drawer uses. Practice setup's panel and the practice
 * sheet of Practice reviews both show it, so a practice reads the same in both places; changing it
 * is Practice setup's form.
 */
export function WorkspacePracticeDefinition({
	practice,
	definitionOptions,
	groupName,
	showStanding = true,
}: WorkspacePracticeDefinitionProps) {
	const autonomy = AUTONOMY_DEFS[practice.autonomy.effective];
	const autonomySource = autonomySourceOf(practice.autonomy, groupName ?? null);
	return (
		<div className="space-y-6">
			{showStanding && (
				<>
					<ItemGroup className="gap-2">
						<CatalogOriginNote origin={practice.catalogOrigin} kind="practice" />
						<Item variant="muted" size="sm" role="listitem">
							<ItemContent>
								<ItemTitle className="flex flex-wrap items-center gap-2">
									<StatusBadge def={autonomy} />
									<AutonomySourceNote
										source={autonomySource}
										className="text-xs font-normal text-muted-foreground"
									/>
								</ItemTitle>
								<ItemDescription className="line-clamp-none">
									{autonomy.description}
								</ItemDescription>
							</ItemContent>
						</Item>
					</ItemGroup>
					<Separator />
				</>
			)}
			<PracticeDefinitionPreview definition={practice} options={definitionOptions} />
		</div>
	);
}
