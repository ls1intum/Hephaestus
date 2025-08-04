import { AnimatePresence, motion } from "framer-motion";
import { ChevronDown } from "lucide-react";
import { useState } from "react";
import { LoaderIcon } from "./Icons";
import { Markdown } from "./Markdown";

interface MessageReasoningProps {
	isLoading: boolean;
	reasoning: string;
}

export function MessageReasoning({
	isLoading,
	reasoning,
}: MessageReasoningProps) {
	const [isExpanded, setIsExpanded] = useState(true);

	const variants = {
		collapsed: {
			height: 0,
			opacity: 0,
			marginTop: 0,
			marginBottom: 0,
		},
		expanded: {
			height: "auto",
			opacity: 1,
			marginTop: "1rem",
			marginBottom: "0.5rem",
		},
	};

	return (
		<div className="flex flex-col">
			{isLoading ? (
				<div className="flex flex-row gap-2 items-center">
					<div className="font-medium">Reasoning</div>
					<div className="animate-spin">
						<LoaderIcon />
					</div>
				</div>
			) : (
					<button
						data-testid="message-reasoning-toggle"
						type="button"
						className="cursor-pointer flex flex-row gap-1 items-center"
						onClick={() => {
              setIsExpanded(!isExpanded);
						}}
					>
            <div className="font-medium">Reasoned for a few seconds</div>
						<ChevronDown size={20} />
					</button>
			)}

			<AnimatePresence initial={false}>
				{isExpanded && (
					<motion.div
						data-testid="message-reasoning"
						key="content"
						initial="collapsed"
						animate="expanded"
						exit="collapsed"
						variants={variants}
						transition={{ duration: 0.2, ease: "easeInOut" }}
						style={{ overflow: "hidden" }}
						className="pl-4 text-zinc-600 dark:text-zinc-400 border-l flex flex-col gap-4"
					>
						<Markdown>{reasoning}</Markdown>
					</motion.div>
				)}
			</AnimatePresence>
		</div>
	);
}
