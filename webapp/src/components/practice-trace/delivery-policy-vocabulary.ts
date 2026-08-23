import { CircleCheckIcon, CircleDashedIcon, CircleSlashIcon, OctagonXIcon } from "lucide-react";
import type {
	DeliveryPolicyFactsSnapshot,
	DeliveryPolicyTrace,
	DeliveryPolicyTraceCheck,
} from "@/api/types.gen";
import type { StatusDefs } from "@/components/practice-vocabulary/status-def";
import { WITHHOLDING_REASON_DEFS } from "@/components/practice-vocabulary/withholding-defs";

type Check = DeliveryPolicyTraceCheck["check"];
type Status = DeliveryPolicyTraceCheck["status"];
type Surface = DeliveryPolicyTrace["surface"];
type Stage = DeliveryPolicyTrace["stage"];
type Reason = NonNullable<DeliveryPolicyTrace["decisiveReason"]>;
type RepositoryMode = NonNullable<DeliveryPolicyFactsSnapshot["repositoryMode"]>;
type PersonMode = NonNullable<DeliveryPolicyFactsSnapshot["personMode"]>;
type Subject = NonNullable<DeliveryPolicyFactsSnapshot["subject"]>;

export const DELIVERY_CHECK_LABELS = {
	INSTANCE_SILENT_MODE: "Instance Silent Mode",
	WORKSPACE_ENABLED: "Workspace enabled",
	ROLLOUT_REVISION: "Current rollout revision",
	WORKSPACE_DELIVERY: "Workspace delivery",
	CURRENT_COVERAGE: "Current review coverage",
	PRACTICE_AUTHORITY: "Practice authority",
	RECIPIENT_CONSENT: "Recipient preference",
	ARTIFACT_ELIGIBILITY: "Work eligibility",
} satisfies Record<Check, string>;

export const DELIVERY_CHECK_STATUS_DEFS: StatusDefs<Status> = {
	PASSED: {
		label: "Passed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "The check had no objection, so the run carried on to the next one.",
	},
	DENIED: {
		label: "Denied",
		icon: OctagonXIcon,
		badgeVariant: "destructive",
		description: "The check stopped the delivery. The reason above it says which rule it applied.",
	},
	NOT_APPLICABLE: {
		label: "Not applicable",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "The check ran and had nothing to decide for this surface and stage.",
	},
	NOT_REACHED: {
		label: "Not reached",
		icon: CircleDashedIcon,
		badgeVariant: "secondary",
		description: "An earlier check denied the delivery, so this one never ran at all.",
	},
};

export const DELIVERY_SURFACE_LABELS = {
	ARTIFACT: "In-context feedback",
	IN_APP: "In-app feedback",
	CONVERSATION: "Conversation feedback",
} satisfies Record<Surface, string>;

export const DELIVERY_STAGE_LABELS = {
	COMPOSITION: "Composition",
	AUTOMATIC: "Automatic authorization",
	APPROVED: "Approved authorization",
	EGRESS: "Final delivery",
} satisfies Record<Stage, string>;

export const DELIVERY_REPOSITORY_MODE_LABELS = {
	ALL_MONITORED: "all monitored",
	SELECTED: "selected",
} satisfies Record<RepositoryMode, string>;

export const DELIVERY_PERSON_MODE_LABELS = {
	ALL_ELIGIBLE: "all eligible",
	SELECTED: "selected",
} satisfies Record<PersonMode, string>;

export const DELIVERY_SUBJECT_LABELS = {
	RESOLVED_LINKED_HUMAN: "author linked to an account",
	MISSING: "no author identified",
	NON_HUMAN: "author is a bot",
	UNLINKED: "author not linked",
} satisfies Record<Subject, string>;

/**
 * One vocabulary with the withheld-feedback surfaces: a decisive reason here and a feedback unit's
 * suppression reason are the same enum, and two sentences for one value is a drift.
 *
 * The annotation is the exhaustiveness check. `Reason` is generated separately from
 * `WithholdingReason`, so a reason the policy can return that the registry has no words for fails
 * `typecheck:webapp` on this line rather than rendering as a blank on the page.
 */
export const DELIVERY_REASON_SENTENCES: Record<Reason, string> = WITHHOLDING_REASON_DEFS;
