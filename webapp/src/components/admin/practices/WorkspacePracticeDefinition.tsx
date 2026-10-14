import type { ReactNode } from "react";
import { currentDefinition } from "@/components/admin/practices/practice-definition-fields";

import type { Practice, PracticeDefinitionOptions } from "@/api/types.gen";
import { CatalogOriginNote } from "@/components/admin/practices/CatalogOriginBadge";
import { PracticeDefinitionPreview } from "@/components/admin/practices/PracticeDefinitionPreview";
import { FeedbackModeBadge } from "@/components/practice-vocabulary/FeedbackModeBadge";
import { FeedbackModeSourceNote } from "@/components/practice-vocabulary/FeedbackModeSourceNote";
import { Item, ItemContent, ItemDescription, ItemGroup, ItemTitle } from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";
import { FEEDBACK_MODE_DESCRIPTIONS, feedbackModeSourceOf } from "@/lib/practice-feedback-mode";

export interface WorkspacePracticeDefinitionProps {
	practice: Practice;
	definitionOptions: PracticeDefinitionOptions;
	/** The group's display name, for the inheritance sentence. Absent when unassigned. */
	groupName?: string;
	/**
	 * Where it came from and its feedback mode, above the definition. Off where a header already
	 * says the feedback mode, so it is said once.
	 */
	showStanding?: boolean;
	/** The way back to the catalog's version, under where the practice came from. */
	resetAction?: ReactNode;
	/** A notice under the feedback mode, such as the members a practice script does not run for. */
	notice?: ReactNode;
	/** What the practice script needs, beside its code; see `PracticeDefinitionPreview`. */
	practiceScript?: ReactNode;
}

/**
 * A workspace practice's current version, read-only: where it came from, its feedback mode, and the
 * {@link PracticeDefinitionPreview} the catalog drawer uses. Practice setup's panel and the practice
 * sheet of Practice reviews both show it, so a practice reads the same in both places; changing it
 * is Practice setup's form.
 */
export function WorkspacePracticeDefinition({
	practice,
	definitionOptions,
	groupName,
	showStanding = true,
	resetAction,
	notice,
	practiceScript,
}: WorkspacePracticeDefinitionProps) {
	const { feedbackMode, currentVersion } = practice;
	return (
		<div className="space-y-6">
			{showStanding && (
				<>
					<ItemGroup className="gap-2">
						<CatalogOriginNote
							origin={practice.catalogOrigin}
							kind="practice"
							action={resetAction}
						/>
						<Item variant="muted" size="sm" role="listitem">
							<ItemContent>
								<ItemTitle className="flex flex-wrap items-center gap-2">
									<FeedbackModeBadge feedbackMode={feedbackMode.effective} />
									<FeedbackModeSourceNote
										source={feedbackModeSourceOf(feedbackMode, "PRACTICE", groupName ?? null)}
										className="text-xs font-normal text-muted-foreground"
									/>
								</ItemTitle>
								<ItemDescription className="line-clamp-none">
									{FEEDBACK_MODE_DESCRIPTIONS[feedbackMode.effective]}
								</ItemDescription>
							</ItemContent>
						</Item>
					</ItemGroup>
					{notice}
					<Separator />
				</>
			)}
			<PracticeDefinitionPreview
				slug={practice.slug}
				kind={practice.kind}
				definition={currentDefinition(practice)}
				version={currentVersion.number}
				options={definitionOptions}
				practiceScript={practiceScript}
			/>
		</div>
	);
}
