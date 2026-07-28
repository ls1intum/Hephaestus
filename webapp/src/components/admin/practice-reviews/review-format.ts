import type {
	ReviewFeedback,
	ReviewFinding,
	ReviewPlacement,
	ReviewSubject,
} from "@/api/types.gen";

export type Presence = ReviewFinding["presence"];
export type Assessment = NonNullable<ReviewFinding["assessment"]>;
export type Severity = NonNullable<ReviewFinding["severity"]>;
export type FeedbackDeliveryState = ReviewFeedback["deliveryState"];
export type FeedbackSuppressionReason = NonNullable<ReviewFeedback["suppressionReason"]>;
export type FeedbackChannel = ReviewFeedback["channel"];
export type PlacementType = ReviewPlacement["placementType"];

export const PRESENCE_LABELS: Record<Presence, string> = {
	PRESENT: "Observed",
	ABSENT: "Expected but not observed",
	NOT_APPLICABLE: "Not applicable",
};

export const ASSESSMENT_LABELS: Record<Assessment, string> = {
	GOOD: "Strength",
	BAD: "Needs improvement",
};

export const SEVERITY_LABELS: Record<Severity, string> = {
	CRITICAL: "Critical",
	MAJOR: "Major",
	MINOR: "Minor",
	INFO: "Informational",
};

export const CHANNEL_LABELS: Record<FeedbackChannel, string> = {
	IN_CONTEXT: "Alongside the work",
	CONVERSATION: "In conversation with Heph",
	PROFILE: "On the developer profile",
};

export const DELIVERY_STATE_LABELS: Record<FeedbackDeliveryState, string> = {
	PREPARED: "Awaiting conversation",
	DELIVERED: "Delivered",
	SUPERSEDED: "Replaced",
	SUPPRESSED: "Not delivered",
	FAILED: "Delivery failed",
};

export const SUPPRESSION_REASON_LABELS: Record<FeedbackSuppressionReason, string> = {
	VOLUME_CAPPED: "Over the delivery volume limit",
	COMPOSER_DEDUPED: "Near-duplicate of another finding in the same review",
	REACTED_DISPUTED: "The developer disputed this earlier",
	REACTED_NOT_APPLICABLE: "The developer marked this not applicable earlier",
	CONVERSATION_EXPIRED: "Never raised in a conversation with Heph, then aged out",
	ARTIFACT_GONE: "The reviewed work no longer exists",
	ARTIFACT_CLOSED: "The reviewed work was closed",
	ARTIFACT_MERGED: "The reviewed work was already merged",
	ARTIFACT_DRAFT: "The reviewed work was a draft",
	RECIPIENT_OPTED_OUT: "The developer opted out of AI feedback",
	EMPTY_AFTER_SANITIZE: "No deliverable content remained after sanitisation",
};

export type BadgeVariant = "default" | "secondary" | "destructive" | "outline" | "warning";

export function severityBadgeVariant(severity: Severity): BadgeVariant {
	switch (severity) {
		case "CRITICAL":
		case "MAJOR":
			return "destructive";
		case "MINOR":
			return "warning";
		case "INFO":
			return "secondary";
	}
}

export function deliveryStateBadgeVariant(state: FeedbackDeliveryState): BadgeVariant {
	switch (state) {
		case "DELIVERED":
			return "default";
		case "PREPARED":
			return "secondary";
		case "SUPERSEDED":
			return "outline";
		case "SUPPRESSED":
			return "warning";
		case "FAILED":
			return "destructive";
	}
}

export function confidenceLabel(confidence: number | undefined): string {
	if (confidence == null) return "—";
	return `${Math.round(confidence * 100)}%`;
}

export function subjectLabel(subject: ReviewSubject | undefined): string {
	if (!subject) return "Unavailable developer";
	return subject.name || subject.login || `#${subject.id}`;
}

export const PLACEMENT_TYPE_LABELS: Record<PlacementType, string> = {
	SUMMARY: "Summary comment",
	INLINE: "Inline note",
	CONVERSATION_TURN: "Conversation turn",
};
