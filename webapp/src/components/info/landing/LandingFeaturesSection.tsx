import {
	GitPullRequest,
	Hammer,
	MessageSquareText,
	MousePointerClick,
	PanelsTopLeft,
	RotateCcw,
} from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Badge } from "@/components/ui/badge";

const FEEDBACK_STEPS = [
	{
		icon: PanelsTopLeft,
		title: "Your project work",
		description: "Work and context from the tools your team uses",
	},
	{
		icon: Hammer,
		title: "Review the work",
		description: "Hephaestus checks the evidence against your workspace's practices",
	},
	{
		icon: MessageSquareText,
		title: "Practice feedback",
		description: "What worked, what could improve, and what to try next",
	},
	{
		icon: MousePointerClick,
		title: "You decide what helps",
		description: "Use it, question it, or skip it",
	},
] as const;

export function LandingFeaturesSection() {
	const shouldReduceMotion = useReducedMotion();

	return (
		<section
			id="features"
			className="relative w-full overflow-hidden border-y border-border/60 bg-muted/15 py-20 md:py-28"
		>
			<div className="pointer-events-none absolute inset-0">
				<div className="absolute -left-32 top-16 size-80 rounded-full bg-provider-done/5 blur-3xl" />
				<div className="absolute -right-32 bottom-8 size-80 rounded-full bg-mentor/5 blur-3xl" />
			</div>

			<div className="container relative mx-auto max-w-7xl px-4 md:px-6">
				<motion.div
					initial={shouldReduceMotion ? false : { opacity: 0, y: 18 }}
					whileInView={{ opacity: 1, y: 0 }}
					viewport={{ once: true, amount: 0.55 }}
					transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
					className="mx-auto max-w-5xl text-center"
				>
					<Badge className="mb-4" variant="outline">
						How feedback works
					</Badge>
					<h2 className="text-3xl font-bold tracking-[-0.035em] sm:text-4xl md:text-5xl">
						From project work to practice feedback
					</h2>
					<p className="mx-auto mt-5 max-w-2xl text-pretty text-lg leading-relaxed text-muted-foreground">
						Hephaestus checks evidence from project work against your workspace's practices, then
						delivers feedback alongside the work or in conversation.
					</p>
				</motion.div>

				<div className="relative mx-auto mt-16 max-w-6xl">
					<motion.div
						initial={shouldReduceMotion ? false : { scaleX: 0 }}
						whileInView={{ scaleX: 1 }}
						viewport={{ once: true, amount: 0.6 }}
						transition={{ duration: 1, delay: 0.2, ease: [0.22, 1, 0.36, 1] }}
						className="absolute left-[12.5%] right-[12.5%] top-7 hidden h-px origin-left bg-gradient-to-r from-border via-mentor/50 to-border lg:block"
					/>
					<motion.ol
						initial={shouldReduceMotion ? false : "hidden"}
						whileInView="visible"
						viewport={{ once: true, amount: 0.35 }}
						variants={{
							hidden: {},
							visible: { transition: { staggerChildren: 0.12 } },
						}}
						className="relative grid gap-10 sm:grid-cols-2 lg:grid-cols-4 lg:gap-6"
					>
						<div
							aria-hidden="true"
							className="absolute bottom-7 left-7 top-7 w-px bg-gradient-to-b from-border via-mentor/40 to-border lg:hidden"
						/>
						{FEEDBACK_STEPS.map((step, index) => {
							const Icon = step.icon;
							return (
								<motion.li
									key={step.title}
									variants={{
										hidden: { opacity: 0, y: 18 },
										visible: {
											opacity: 1,
											y: 0,
											transition: { duration: 0.55, ease: [0.22, 1, 0.36, 1] },
										},
									}}
									className="group relative flex gap-4 lg:flex-col lg:items-center lg:text-center"
								>
									<motion.div
										whileHover={shouldReduceMotion ? undefined : { y: -4 }}
										whileTap={shouldReduceMotion ? undefined : { scale: 0.94 }}
										className="relative z-10 flex size-14 shrink-0 items-center justify-center rounded-2xl border border-border bg-background text-mentor shadow-[0_14px_35px_-22px_rgb(15_23_42_/_0.55)] transition-colors group-hover:border-mentor/30 group-hover:bg-mentor/5 dark:bg-secondary"
									>
										<Icon className="size-6" strokeWidth={1.7} />
									</motion.div>
									<div className="pt-0.5 lg:pt-2">
										<p className="text-xs font-semibold tracking-[0.12em] text-mentor uppercase">
											{String(index + 1).padStart(2, "0")}
										</p>
										<h3 className="mt-1 text-lg font-semibold">{step.title}</h3>
										<p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
											{step.description}
										</p>
									</div>
								</motion.li>
							);
						})}
					</motion.ol>

					<div className="relative mt-7 hidden h-16 lg:block" aria-hidden="true">
						<svg
							aria-hidden="true"
							className="absolute inset-0 size-full"
							viewBox="0 0 1160 64"
							fill="none"
						>
							<motion.path
								d="M1080 4C1080 58 80 58 80 4"
								stroke="currentColor"
								className="text-mentor/45"
								strokeWidth="1.5"
								strokeDasharray="5 7"
								initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
								whileInView={{ pathLength: 1, opacity: 1 }}
								viewport={{ once: true, amount: 0.8 }}
								transition={{ duration: 1, delay: 0.5, ease: [0.22, 1, 0.36, 1] }}
							/>
							<motion.path
								d="m70 14 10-10 10 10"
								stroke="currentColor"
								className="text-mentor/60"
								strokeWidth="1.5"
								strokeLinecap="round"
								strokeLinejoin="round"
								initial={shouldReduceMotion ? false : { opacity: 0 }}
								whileInView={{ opacity: 1 }}
								viewport={{ once: true }}
								transition={{ duration: 0.3, delay: 1.35 }}
							/>
						</svg>
						<div className="absolute inset-x-0 bottom-0 flex justify-center">
							<span className="inline-flex items-center gap-2 bg-muted/15 px-3 text-xs font-semibold tracking-[0.12em] text-mentor uppercase">
								<RotateCcw className="size-3.5" />
								Next project work
							</span>
						</div>
					</div>

					<div className="mt-8 flex items-center gap-2 pl-1 text-sm font-medium text-mentor lg:hidden">
						<RotateCcw className="size-4" />
						Next project work continues the cycle
					</div>
				</div>

				<motion.div
					initial={shouldReduceMotion ? false : { opacity: 0, y: 20 }}
					whileInView={{ opacity: 1, y: 0 }}
					viewport={{ once: true, amount: 0.35 }}
					transition={{ duration: 0.65, delay: 0.15, ease: [0.22, 1, 0.36, 1] }}
					className="mx-auto mt-16 grid max-w-6xl border-y border-border/70 lg:grid-cols-[0.78fr_1fr_1fr]"
				>
					<div className="flex items-center gap-3 py-6 pr-6 lg:py-8">
						<MessageSquareText className="size-5 text-mentor" />
						<div>
							<p className="text-xs font-semibold tracking-[0.12em] text-muted-foreground uppercase">
								Available today
							</p>
							<p className="mt-1 font-semibold">Where feedback can appear</p>
						</div>
					</div>

					<motion.div
						whileHover={shouldReduceMotion ? undefined : { y: -3 }}
						className="group border-t border-border/70 py-6 transition-colors hover:bg-background/60 lg:border-l lg:border-t-0 lg:px-8 lg:py-8"
					>
						<div className="flex items-start gap-4">
							<div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-provider-accent text-provider-accent-foreground transition-transform group-hover:-rotate-3">
								<GitPullRequest className="size-5" />
							</div>
							<div>
								<h3 className="font-semibold">Alongside project work</h3>
								<p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
									Practice feedback can appear as a comment in GitHub or GitLab.
								</p>
							</div>
						</div>
					</motion.div>

					<motion.div
						whileHover={shouldReduceMotion ? undefined : { y: -3 }}
						className="group border-t border-border/70 py-6 transition-colors hover:bg-background/60 lg:border-l lg:border-t-0 lg:px-8 lg:py-8"
					>
						<div className="flex items-start gap-4">
							<div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-mentor/10 text-mentor transition-transform group-hover:rotate-3">
								<MentorIcon size={28} pad={4} />
							</div>
							<div>
								<h3 className="font-semibold">Talk with Heph</h3>
								<p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
									Discuss feedback and recent work in the web app or, when connected, in Slack.
								</p>
							</div>
						</div>
					</motion.div>
				</motion.div>
			</div>
		</section>
	);
}
