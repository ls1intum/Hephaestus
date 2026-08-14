import { ArchiveIcon, BrushCleaningIcon, UserRoundXIcon, VolumeOffIcon } from "lucide-react";
import type { ReviewFeedback } from "@/api/types.gen";
import type { StatusDefs } from "./status-def";

export type WithholdingReason = NonNullable<ReviewFeedback["suppressionReason"]>;
export type WithholdingFamily = "WORK_MOVED_ON" | "POLICY" | "DEVELOPER_CHOICE" | "HOUSEKEEPING";

/**
 * Fourteen reasons is a filter nobody reads. Four families is a question somebody can answer.
 *
 * Each family answers "who decided", which is the only cut an operator acts on differently: the work
 * moving on is nobody's decision and needs no follow-up; policy is a setting they own and can change;
 * the developer's choice is one they must not override; housekeeping is the pipeline's own doing and
 * only matters if a family suddenly grows.
 *
 * <p>The families are for *filtering*. A row still shows its own precise sentence from
 * `WITHHOLDING_REASON_DEFS` — the grouping simplifies the question, not the answer.
 */
export const WITHHOLDING_FAMILY_DEFS: StatusDefs<WithholdingFamily> = {
	WORK_MOVED_ON: {
		label: "The work moved on",
		icon: ArchiveIcon,
		badgeVariant: "outline",
		description: "The pull request, issue or document had moved past the point of being told.",
	},
	POLICY: {
		label: "Policy kept it quiet",
		icon: VolumeOffIcon,
		badgeVariant: "outline",
		description: "A setting somebody chose — a volume limit, a practice's tier, or silent mode.",
	},
	DEVELOPER_CHOICE: {
		label: "The developer's choice",
		icon: UserRoundXIcon,
		badgeVariant: "outline",
		description: "The developer opted out, or already told us this kind of feedback was wrong.",
	},
	HOUSEKEEPING: {
		label: "Housekeeping",
		icon: BrushCleaningIcon,
		badgeVariant: "outline",
		description: "The pipeline dropped it: a duplicate, an empty body, or a chat that never came.",
	},
};

/**
 * Every reason, and the family it filters under. `Record` over the wire union, so a reason the
 * server adds cannot slip through unfiled — it fails the typecheck here.
 *
 * <p>`ARTIFACT_DRAFT` is filed but dead: the server marks the constant `@Deprecated` and nothing
 * writes it. The words stay because the wire union still carries the value and a row rendering blank
 * is worse than a row rendering an explanation nobody will see.
 */
const REASON_FAMILY: Record<WithholdingReason, WithholdingFamily> = {
	ARTIFACT_GONE: "WORK_MOVED_ON",
	ARTIFACT_CLOSED: "WORK_MOVED_ON",
	ARTIFACT_MERGED: "WORK_MOVED_ON",
	ARTIFACT_DRAFT: "WORK_MOVED_ON",
	VOLUME_CAPPED: "POLICY",
	PRACTICE_TIER_QUIET: "POLICY",
	BACKFILL_QUIET: "POLICY",
	INSTANCE_SILENCED: "POLICY",
	RECIPIENT_OPTED_OUT: "DEVELOPER_CHOICE",
	REACTED_DISPUTED: "DEVELOPER_CHOICE",
	REACTED_NOT_APPLICABLE: "DEVELOPER_CHOICE",
	COMPOSER_DEDUPED: "HOUSEKEEPING",
	EMPTY_AFTER_SANITIZE: "HOUSEKEEPING",
	CONVERSATION_EXPIRED: "HOUSEKEEPING",
};

/**
 * The precise sentence for one reason, shown on the row that has it.
 *
 * Written for somebody who has never read the pipeline: no constant is echoed, no stage is named,
 * and each one says what happened rather than which check rejected it. They are also third person —
 * anyone with access can open these screens and the work is usually somebody else's, so "you were
 * over your limit" would tell the wrong person off.
 */
export const WITHHOLDING_REASON_DEFS: Record<WithholdingReason, string> = {
	ARTIFACT_GONE: "The work no longer exists.",
	ARTIFACT_CLOSED: "The work was closed before the feedback could be posted.",
	ARTIFACT_MERGED: "The work was already merged, so a note on it would arrive too late.",
	ARTIFACT_DRAFT: "The work was still a draft.",
	VOLUME_CAPPED: "Over the limit on how much feedback one person gets from a single review.",
	// Names the effect, not the check. The server records this reason for *any* in-context feedback
	// the admission gate turns away, which includes a workspace whose reach is set to conversation
	// only — so wording it as "the practice's tier blocked it" would be wrong about half the rows.
	PRACTICE_TIER_QUIET: "This practice is set to measure quietly rather than to speak up.",
	BACKFILL_QUIET: "Found while reviewing past work, which is measured but never sent.",
	INSTANCE_SILENCED: "Silent mode was switched on for the whole instance.",
	RECIPIENT_OPTED_OUT: "The developer has opted out of AI feedback.",
	REACTED_DISPUTED: "The developer disputed feedback like this before.",
	REACTED_NOT_APPLICABLE: "The developer marked feedback like this not applicable before.",
	COMPOSER_DEDUPED: "Nearly the same as other feedback from the same review.",
	EMPTY_AFTER_SANITIZE: "Nothing was left to send once the text had been cleaned up.",
	CONVERSATION_EXPIRED: "It waited for a conversation that never happened, then aged out.",
};

export function withholdingFamily(reason: WithholdingReason): WithholdingFamily {
	return REASON_FAMILY[reason];
}

export function withholdingReasonSentence(reason: WithholdingReason): string {
	return WITHHOLDING_REASON_DEFS[reason];
}

/**
 * The reasons each family covers, derived from the one table above rather than written twice — a
 * family filter has to expand to exactly the reasons the row filter would have matched, and two
 * hand-kept lists is how that stops being true.
 */
export const WITHHOLDING_FAMILY_REASONS: Record<WithholdingFamily, WithholdingReason[]> =
	Object.entries(REASON_FAMILY).reduce(
		(families, [reason, family]) => {
			families[family].push(reason as WithholdingReason);
			return families;
		},
		{
			WORK_MOVED_ON: [],
			POLICY: [],
			DEVELOPER_CHOICE: [],
			HOUSEKEEPING: [],
		} as Record<WithholdingFamily, WithholdingReason[]>,
	);

/** Every reason the named families cover, for the query a family filter turns into. */
export function reasonsInFamilies(families: readonly WithholdingFamily[]): WithholdingReason[] {
	return families.flatMap((family) => WITHHOLDING_FAMILY_REASONS[family]);
}
