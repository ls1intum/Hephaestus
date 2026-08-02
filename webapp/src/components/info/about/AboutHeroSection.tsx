import { Hammer } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";

export function AboutHeroSection() {
	const shouldReduceMotion = useReducedMotion();

	return (
		<section className="space-y-6 py-4 text-center">
			<motion.div
				initial={shouldReduceMotion ? false : { opacity: 0, scale: 0.8, rotate: -8 }}
				animate={{ opacity: 1, scale: 1, rotate: 0 }}
				transition={{ type: "spring", stiffness: 170, damping: 17 }}
				whileHover={shouldReduceMotion ? undefined : { scale: 1.06, rotate: -5 }}
				className="relative mx-auto flex size-28 items-center justify-center"
				aria-hidden="true"
			>
				<motion.div
					animate={shouldReduceMotion ? undefined : { rotate: 360 }}
					transition={{ duration: 28, repeat: Number.POSITIVE_INFINITY, ease: "linear" }}
					className="absolute inset-0 rounded-full border border-dashed border-mentor/25"
				/>
				<motion.div
					animate={
						shouldReduceMotion
							? undefined
							: { scale: [0.92, 1.08, 0.92], opacity: [0.18, 0.08, 0.18] }
					}
					transition={{ duration: 4, repeat: Number.POSITIVE_INFINITY, ease: "easeInOut" }}
					className="absolute inset-3 rounded-full bg-mentor"
				/>
				<div className="relative flex size-20 items-center justify-center rounded-full border border-mentor/25 bg-background text-mentor shadow-[0_18px_48px_-20px_var(--color-mentor)] dark:bg-secondary">
					<Hammer className="size-9" strokeWidth={1.7} />
				</div>
			</motion.div>

			<motion.h1
				initial={shouldReduceMotion ? false : { opacity: 0, y: 12 }}
				animate={{ opacity: 1, y: 0 }}
				transition={{ duration: 0.55, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
				className="text-4xl font-bold tracking-[-0.035em] sm:text-5xl"
			>
				About Hephaestus
			</motion.h1>
			<motion.p
				initial={shouldReduceMotion ? false : { opacity: 0, y: 12 }}
				animate={{ opacity: 1, y: 0 }}
				transition={{ duration: 0.55, delay: 0.16, ease: [0.22, 1, 0.36, 1] }}
				className="mx-auto max-w-2xl text-pretty text-xl leading-relaxed text-muted-foreground"
			>
				Hephaestus helps more developers get feedback on the engineering practices in their project
				work. Heph is the conversational AI mentor for talking through that feedback.
			</motion.p>
		</section>
	);
}
