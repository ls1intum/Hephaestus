import type { DeliveryPolicyTrace, DeliveryPolicyTraceCheck } from "@/api/types.gen";

type Check = DeliveryPolicyTraceCheck["check"];
type Status = DeliveryPolicyTraceCheck["status"];
type Surface = DeliveryPolicyTrace["surface"];
type Stage = DeliveryPolicyTrace["stage"];
type Reason = NonNullable<DeliveryPolicyTrace["decisiveReason"]>;

export const deliveryCheckLabels = {
	INSTANCE_SILENT_MODE: "Instance Silent Mode",
	WORKSPACE_ENABLED: "Workspace enabled",
	ROLLOUT_REVISION: "Current rollout revision",
	WORKSPACE_DELIVERY: "Workspace delivery",
	CURRENT_COVERAGE: "Current review coverage",
	PRACTICE_AUTHORITY: "Practice authority",
	HUMAN_APPROVAL: "Human approval",
	RECIPIENT_CONSENT: "Recipient preference",
	ARTIFACT_ELIGIBILITY: "Work eligibility",
} satisfies Record<Check, string>;

export const deliveryStatusLabels = {
	PASSED: "Passed",
	DENIED: "Denied",
	NOT_APPLICABLE: "Not applicable",
	NOT_REACHED: "Not reached",
} satisfies Record<Status, string>;

export const deliverySurfaceLabels = {
	ARTIFACT: "In-context feedback",
	IN_APP: "In-app feedback",
	CONVERSATION: "Conversation feedback",
} satisfies Record<Surface, string>;

export const deliveryStageLabels = {
	COMPOSITION: "Composition",
	AUTOMATIC: "Automatic authorization",
	APPROVED: "Approved authorization",
	EGRESS: "Final delivery",
} satisfies Record<Stage, string>;

export const deliveryReasonLabels = {
	VOLUME_CAPPED: "Review volume limit reached",
	COMPOSER_DEDUPED: "Duplicate feedback removed",
	REACTED_DISPUTED: "Feedback was disputed",
	REACTED_NOT_APPLICABLE: "Feedback was marked not applicable",
	CONVERSATION_EXPIRED: "Conversation expired",
	ARTIFACT_GONE: "Work no longer exists",
	ARTIFACT_CLOSED: "Work is closed",
	ARTIFACT_MERGED: "Work is merged",
	ARTIFACT_DRAFT: "Work is still a draft",
	RECIPIENT_OPTED_OUT: "Recipient opted out",
	EMPTY_AFTER_SANITIZE: "No deliverable feedback remained",
	INSTANCE_SILENCED: "Instance Silent Mode is active",
	WORKSPACE_DISABLED: "Practice reviews are disabled",
	WORKSPACE_DELIVERY_PAUSED: "Workspace delivery is paused",
	STALE_ROLLOUT_REVISION: "Review used an older rollout configuration",
	OUTSIDE_CURRENT_COVERAGE: "Work is outside current review coverage",
	ADMINISTRATIVE_INTERNAL_ONLY: "Administrative review is internal only",
	APPROVAL_STALE: "Approval is stale",
	APPROVAL_NO_LONGER_ELIGIBLE: "Approved feedback is no longer eligible",
	PRACTICE_REQUIRES_APPROVAL: "Practice requires human approval",
	BACKFILL_QUIET: "Backfill is configured not to deliver feedback",
} satisfies Record<Reason, string>;
