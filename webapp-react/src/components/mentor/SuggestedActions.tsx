import { Button } from "@/components/ui/button";
import { motion } from "framer-motion";

interface SuggestedActionsProps {
	onAction: (actionMessage: string) => void;
}

export function SuggestedActions({ onAction }: SuggestedActionsProps) {
	const suggestedActions = [
		{
			title: "What are the advantages",
			label: "of using Next.js?",
			action: "What are the advantages of using Next.js?",
		},
		{
			title: "Write code to",
			label: `demonstrate djikstra's algorithm`,
			action: `Write code to demonstrate djikstra's algorithm`,
		},
		{
			title: "Help me write an essay",
			label: "about silicon valley",
			action: "Help me write an essay about silicon valley",
		},
		{
			title: "What is the weather",
			label: "in San Francisco?",
			action: "What is the weather in San Francisco?",
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
						className="text-left border rounded-xl px-4 py-3.5 text-sm flex-1 gap-1 sm:flex-col w-full h-auto justify-start items-start"
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
