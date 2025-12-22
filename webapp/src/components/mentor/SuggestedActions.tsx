import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";

interface SuggestedActionsProps {
	onAction: (actionMessage: string) => void;
}

/**
 * Quick action buttons for common mentor interactions.
 *
 * @deprecated This component is no longer used in the app.
 * The mentor now auto-starts a conversation when opened.
 * Kept for Storybook documentation purposes.
 */
export function SuggestedActions({ onAction }: SuggestedActionsProps) {
	const suggestedActions = [
		{
			title: "Catch up",
			label: "What did I work on?",
			action: "What did I work on this week?",
		},
		{
			title: "Reflect",
			label: "Something was challenging",
			action: "I want to reflect on something that was challenging.",
		},
		{
			title: "Plan",
			label: "Figure out what's next",
			action: "Help me think through what I should focus on next.",
		},
		{
			title: "Prep my weekly",
			label: "Status update for supervisor",
			action: "Let's prepare my weekly status update.",
		},
	];

	return (
		<div data-testid="suggested-actions" className="grid sm:grid-cols-2 gap-2 w-full">
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
						<span className="text-muted-foreground">{suggestedAction.label}</span>
					</Button>
				</motion.div>
			))}
		</div>
	);
}
