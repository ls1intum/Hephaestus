import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";

interface SuggestedActionsProps {
	onAction: (actionMessage: string) => void;
}

export function SuggestedActions({ onAction }: SuggestedActionsProps) {
	const suggestedActions = [
		{
			title: "Review progress & plan next steps",
			label: "Reflect on recent work and goals ahead",
			action:
				"Can we review my recent progress and map out what to tackle next?",
		},
		{
			title: "Break work into smaller tasks",
			label: "Keep things simple and deliverable",
			action:
				"I'm not sure how to slice my work—can you help me break it into smaller pieces?",
		},
		{
			title: "Check task aligns with our goal",
			label: "Ensure I'm building the right thing",
			action:
				"Does this task fit our overall goal, or should I adjust my approach?",
		},
		{
			title: "Troubleshoot a blocker",
			label: "Find a strategy to move forward",
			action:
				"I'm running into a blocker and need help figuring out a way forward.",
		},
	];

	return (
		<div
			data-testid="suggested-actions"
			className="grid sm:grid-cols-2 gap-2 w-full"
		>
			{suggestedActions.map((suggestedAction, index) => (
				<motion.div
					initial={{ opacity: 0, y: 20 }}
					animate={{ opacity: 1, y: 0 }}
					exit={{ opacity: 0, y: 20 }}
					transition={{ delay: 0.05 * index }}
					key={`suggested-action-${suggestedAction.title}-${index}`}
					className={index > 1 ? "hidden sm:block" : "block"}
				>
					<Button
						variant="ghost"
						onClick={() => {
							onAction(suggestedAction.action);
						}}
						className="text-left bg-background border rounded-xl px-4 py-3.5 text-sm flex-1 gap-1 sm:flex-col w-full h-auto justify-start items-start"
					>
						<span className="font-medium">{suggestedAction.title}</span>
						<span className="text-muted-foreground">
							{suggestedAction.label}
						</span>
					</Button>
				</motion.div>
			))}
		</div>
	);
}
