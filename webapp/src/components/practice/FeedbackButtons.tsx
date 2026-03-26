import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, MessageSquare, X } from "lucide-react";
import { useState } from "react";
import { getLatestFeedbackOptions, submitFeedbackMutation } from "@/api/@tanstack/react-query.gen";
import type { CreateFindingFeedback, FindingFeedback } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

type FeedbackAction = CreateFindingFeedback["action"];

const ACTIONS: Array<{
	value: FeedbackAction;
	label: string;
	icon: typeof Check;
	activeClass: string;
}> = [
	{
		value: "APPLIED",
		label: "Applied",
		icon: Check,
		activeClass: "bg-provider-success text-provider-success-foreground",
	},
	{
		value: "DISPUTED",
		label: "Disputed",
		icon: MessageSquare,
		activeClass: "bg-provider-attention text-provider-attention-foreground",
	},
	{
		value: "NOT_APPLICABLE",
		label: "N/A",
		icon: X,
		activeClass: "bg-provider-muted text-provider-muted-foreground",
	},
];

export interface FeedbackButtonsProps {
	workspaceSlug: string;
	findingId: string;
}

export function FeedbackButtons({ workspaceSlug, findingId }: FeedbackButtonsProps) {
	const queryClient = useQueryClient();
	const [disputeText, setDisputeText] = useState("");
	const [showDispute, setShowDispute] = useState(false);

	const feedbackQueryOpts = getLatestFeedbackOptions({
		path: { workspaceSlug, findingId },
	});

	const { data: existingFeedback } = useQuery({
		...feedbackQueryOpts,
		staleTime: 60_000,
	});

	const mutation = useMutation({
		...submitFeedbackMutation(),
		onSuccess: (data: FindingFeedback) => {
			queryClient.setQueryData(feedbackQueryOpts.queryKey, data);
			setShowDispute(false);
			setDisputeText("");
		},
	});

	const currentAction = existingFeedback?.action ?? null;

	const handleAction = (action: FeedbackAction) => {
		if (action === currentAction) return;

		if (action === "DISPUTED") {
			setShowDispute(true);
			return;
		}

		mutation.mutate({
			path: { workspaceSlug, findingId },
			body: { action },
		});
	};

	const handleSubmitDispute = () => {
		if (!disputeText.trim()) return;

		mutation.mutate({
			path: { workspaceSlug, findingId },
			body: { action: "DISPUTED", explanation: disputeText.trim() },
		});
	};

	return (
		<div className="flex flex-col gap-2">
			<div className="flex items-center gap-1.5">
				<span className="text-xs text-muted-foreground mr-1">Feedback:</span>
				{ACTIONS.map(({ value, label, icon: Icon, activeClass }) => (
					<Button
						key={value}
						variant="outline"
						size="sm"
						disabled={mutation.isPending}
						className={cn("h-7 px-2 text-xs gap-1", currentAction === value && activeClass)}
						onClick={() => handleAction(value)}
					>
						<Icon className="h-3 w-3" />
						{label}
					</Button>
				))}
			</div>

			{showDispute && (
				<div className="flex flex-col gap-2 pl-0.5">
					<Textarea
						placeholder="Why do you disagree with this finding?"
						value={disputeText}
						onChange={(e) => setDisputeText(e.target.value)}
						className="min-h-12 text-xs"
					/>
					<div className="flex gap-2">
						<Button
							size="sm"
							className="h-7 text-xs"
							disabled={!disputeText.trim() || mutation.isPending}
							onClick={handleSubmitDispute}
						>
							Submit
						</Button>
						<Button
							variant="ghost"
							size="sm"
							className="h-7 text-xs"
							onClick={() => {
								setShowDispute(false);
								setDisputeText("");
							}}
						>
							Cancel
						</Button>
					</div>
				</div>
			)}
		</div>
	);
}
