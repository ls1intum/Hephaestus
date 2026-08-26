import { CheckCircle2Icon, CircleXIcon } from "lucide-react";
import type { FeedbackApproval } from "@/api/types.gen";
import type { StatusDefs } from "@/components/practice-vocabulary/status-def";

export const APPROVAL_DECISION_DEFS = {
	APPROVED: {
		label: "Approved",
		icon: CheckCircle2Icon,
		badgeVariant: "success",
		description: "An authorized workspace reviewer approved the exact package.",
	},
	REJECTED: {
		label: "Rejected",
		icon: CircleXIcon,
		badgeVariant: "destructive",
		description: "An authorized workspace reviewer rejected the exact package.",
	},
} satisfies StatusDefs<NonNullable<FeedbackApproval["decision"]>>;
