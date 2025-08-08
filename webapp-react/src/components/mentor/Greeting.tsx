import { motion } from "framer-motion";
import { MentorIcon } from "./MentorIcon";

export const Greeting = () => {
	return (
		<div
			key="overview"
			className="max-w-3xl mx-auto md:mt-20 px-8 size-full flex flex-col justify-center"
		>
			<div className="flex items-center gap-4 mb-4">
				<motion.div
				  className="text-muted-foreground"
					initial={{ opacity: 0, scale: 0.8 }}
					animate={{ opacity: 1, scale: 1 }}
					exit={{ opacity: 0, scale: 0.8 }}
					transition={{ delay: 0.3 }}
				>
					<MentorIcon className="size-20" />
				</motion.div>
				<div className="flex flex-col">
					<motion.div
						initial={{ opacity: 0, y: 10 }}
						animate={{ opacity: 1, y: 0 }}
						exit={{ opacity: 0, y: 10 }}
						transition={{ delay: 0.5 }}
						className="text-2xl font-semibold"
					>
						Hello there!
					</motion.div>
					<motion.div
						initial={{ opacity: 0, y: 10 }}
						animate={{ opacity: 1, y: 0 }}
						exit={{ opacity: 0, y: 10 }}
						transition={{ delay: 0.6 }}
						className="text-2xl text-zinc-500"
					>
						How can I help you today?
					</motion.div>
				</div>
			</div>
		</div>
	);
};
