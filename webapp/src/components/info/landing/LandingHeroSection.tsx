import {
	Check,
	ChevronDown,
	Code2,
	FileText,
	GitPullRequest,
	Hammer,
	Lightbulb,
} from "lucide-react";
import { motion, stagger, useReducedMotion } from "motion/react";
import { LandingSignInCta } from "@/components/auth/LandingSignInCta";
import { GithubIcon, GitlabIcon } from "@/components/icons/brand";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Button } from "@/components/ui/button";

interface LandingHeroSectionProps {
	onSignIn: (idpHint: string) => void;
	onGoToDashboard?: () => void;
	isSignedIn: boolean;
	onLearnMoreClick: () => void;
}

const entranceTransition = {
	duration: 0.65,
	ease: [0.22, 1, 0.36, 1],
} as const;

interface LandingFeedbackPreviewProps {
	staticMode?: boolean;
}

export function LandingFeedbackPreview({ staticMode = false }: LandingFeedbackPreviewProps) {
	const prefersReducedMotion = useReducedMotion();
	const shouldReduceMotion = staticMode || prefersReducedMotion;

	return (
		<motion.div
			aria-hidden="true"
			initial={shouldReduceMotion ? false : { opacity: 0, y: 24, scale: 0.97 }}
			animate={{ opacity: 1, y: 0, scale: 1 }}
			transition={{ ...entranceTransition, delay: 0.15 }}
			className="relative mx-auto h-[530px] w-full max-w-[680px] min-[370px]:h-[500px]"
		>
			<div className="absolute right-[4%] top-[6%] size-56 rounded-full bg-mentor/10 blur-3xl" />
			<div className="absolute bottom-[5%] left-[5%] size-64 rounded-full bg-provider-done/10 blur-3xl" />

			<div
				className="absolute inset-[7%] opacity-50"
				style={{
					backgroundImage:
						"radial-gradient(circle, color-mix(in oklch, var(--border) 78%, transparent) 1px, transparent 1px)",
					backgroundSize: "24px 24px",
					maskImage: "radial-gradient(ellipse at center, black 10%, transparent 72%)",
				}}
			/>

			<svg
				aria-hidden="true"
				className="absolute inset-0 size-full"
				viewBox="0 0 680 500"
				fill="none"
			>
				<defs>
					<marker
						id="hero-flow-arrow"
						viewBox="0 0 10 10"
						refX="8"
						refY="5"
						markerWidth="6"
						markerHeight="6"
						orient="auto-start-reverse"
					>
						<path
							d="m1 1 7 4-7 4"
							fill="none"
							stroke="var(--color-mentor)"
							strokeOpacity="0.45"
							strokeWidth="1.5"
							strokeLinecap="round"
							strokeLinejoin="round"
						/>
					</marker>
				</defs>
				<motion.circle
					cx="340"
					cy="250"
					r="106"
					stroke="currentColor"
					className="text-mentor/20"
					strokeWidth="1.5"
					strokeDasharray="5 10"
					animate={shouldReduceMotion ? undefined : { rotate: 360 }}
					transition={{ duration: 42, repeat: Number.POSITIVE_INFINITY, ease: "linear" }}
					style={{ transformOrigin: "340px 250px" }}
				/>
				{[
					"M188 105C246 130 281 165 310 207",
					"M370 207C398 164 433 132 492 106",
					"M306 294C269 327 238 357 202 395",
					"M374 294C411 328 444 357 480 393",
				].map((path, index) => (
					<motion.path
						key={path}
						d={path}
						stroke="currentColor"
						className="text-mentor/35"
						strokeWidth="2"
						strokeDasharray="4 7"
						markerEnd="url(#hero-flow-arrow)"
						initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
						animate={{ pathLength: 1, opacity: 1 }}
						transition={{ duration: 0.8, delay: 0.5 + index * 0.1 }}
					/>
				))}
			</svg>

			<motion.div
				initial={shouldReduceMotion ? false : { opacity: 0, x: -22, rotate: -6 }}
				animate={{ opacity: 1, x: 0, rotate: 0 }}
				transition={{ ...entranceTransition, delay: 0.3 }}
				whileHover={shouldReduceMotion ? undefined : { y: -4, scale: 1.01 }}
				className="absolute left-0 top-8 w-[48%] rounded-2xl border border-border/70 bg-background p-3 shadow-[0_18px_45px_-24px_rgb(15_23_42_/_0.45)] sm:left-5 sm:top-12 sm:w-[220px] sm:p-4 dark:shadow-black/50"
			>
				<div className="mb-2 flex items-center gap-2">
					<div className="flex size-8 items-center justify-center rounded-xl bg-provider-accent text-provider-accent-foreground">
						<GitPullRequest className="size-4" />
					</div>
					<div>
						<p className="text-[11px] font-semibold tracking-[0.12em] text-muted-foreground uppercase sm:text-xs">
							Your project work
						</p>
						<p className="text-[13px] font-medium sm:text-sm">A focused change</p>
					</div>
				</div>
				<div className="flex items-center gap-2 text-[11px] text-muted-foreground">
					<span className="flex items-center gap-1">
						<Code2 className="size-3" /> Code
					</span>
					<span aria-hidden="true">·</span>
					<span className="flex items-center gap-1">
						<FileText className="size-3" /> Context
					</span>
				</div>
			</motion.div>

			<motion.div
				initial={shouldReduceMotion ? false : { opacity: 0, x: 22, rotate: 6 }}
				animate={{ opacity: 1, x: 0, rotate: 0 }}
				transition={{ ...entranceTransition, delay: 0.4 }}
				whileHover={shouldReduceMotion ? undefined : { y: -4, scale: 1.01 }}
				className="absolute right-0 top-[60px] w-[51%] rounded-2xl border border-provider-success/40 bg-background p-3 shadow-[0_18px_45px_-24px_rgb(15_23_42_/_0.45)] sm:right-2 sm:top-12 sm:w-[250px] sm:p-4 dark:shadow-black/50"
			>
				<div className="mb-1.5 flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.12em] text-provider-success-foreground uppercase sm:text-xs">
					<Check className="size-3.5" />
					What worked
				</div>
				<p className="text-[13px] leading-relaxed sm:text-sm">
					You kept the change focused, so it is easier for someone else to review.
				</p>
			</motion.div>

			<motion.div
				initial={shouldReduceMotion ? false : { opacity: 0, scale: 0.72 }}
				animate={{ opacity: 1, scale: 1 }}
				transition={
					shouldReduceMotion
						? { duration: 0 }
						: {
								opacity: { duration: 0.35, delay: 0.5 },
								scale: { type: "spring", stiffness: 190, damping: 16, delay: 0.5 },
							}
				}
				whileHover={shouldReduceMotion ? undefined : { scale: 1.06, rotate: -4 }}
				className="absolute left-1/2 top-[210px] z-20 -translate-x-1/2 min-[370px]:top-[195px] sm:top-[186px]"
			>
				<div className="flex size-20 items-center justify-center rounded-full border border-mentor/25 bg-background text-mentor shadow-[0_18px_48px_-18px_var(--color-mentor)] sm:size-28 dark:bg-secondary">
					<Hammer className="size-9 sm:size-12" strokeWidth={1.7} />
				</div>
			</motion.div>

			<motion.div
				initial={shouldReduceMotion ? false : { opacity: 0, x: -22, rotate: -5 }}
				animate={{ opacity: 1, x: 0, rotate: 0 }}
				transition={{ ...entranceTransition, delay: 0.6 }}
				whileHover={shouldReduceMotion ? undefined : { y: -4, scale: 1.01 }}
				className="absolute bottom-[32px] left-0 w-[48%] rounded-2xl border border-warning/35 bg-background p-3 shadow-[0_18px_45px_-24px_rgb(15_23_42_/_0.45)] sm:bottom-9 sm:left-5 sm:w-[250px] sm:p-4 dark:shadow-black/50"
			>
				<div className="mb-1.5 flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.12em] text-warning uppercase sm:text-xs">
					<Lightbulb className="size-3.5" />
					What to try next
				</div>
				<p className="text-[13px] leading-relaxed sm:text-sm">
					Add one sentence explaining why the change matters.
				</p>
			</motion.div>

			<motion.div
				initial={shouldReduceMotion ? false : { opacity: 0, x: 22, rotate: 5 }}
				animate={{ opacity: 1, x: 0, rotate: 0 }}
				transition={{ ...entranceTransition, delay: 0.7 }}
				whileHover={shouldReduceMotion ? undefined : { y: -4, scale: 1.01 }}
				className="absolute bottom-0 right-0 w-[48%] rounded-2xl border border-mentor/25 bg-background p-3 shadow-[0_18px_45px_-24px_rgb(15_23_42_/_0.45)] sm:bottom-6 sm:right-3 sm:w-[240px] sm:p-4 dark:shadow-black/50"
			>
				<div className="mb-2 flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.12em] text-mentor uppercase sm:text-xs">
					<MentorIcon size={20} pad={3} />
					Talk with Heph
				</div>
				<div className="space-y-1.5 text-[11px] sm:text-xs">
					<motion.div
						initial={shouldReduceMotion ? false : { opacity: 0, x: 8 }}
						animate={{ opacity: 1, x: 0 }}
						transition={{ duration: 0.4, delay: 0.9 }}
						className="ml-4 rounded-xl rounded-br-sm bg-muted px-2.5 py-1.5"
					>
						<span className="mb-0.5 block text-[9px] font-semibold tracking-wide text-muted-foreground uppercase">
							You
						</span>
						What should that sentence say?
					</motion.div>
					<motion.div
						initial={shouldReduceMotion ? false : { opacity: 0, x: -8 }}
						animate={{ opacity: 1, x: 0 }}
						transition={{ duration: 0.4, delay: 1.05 }}
						className="mr-2 rounded-xl rounded-bl-sm bg-mentor/10 px-2.5 py-1.5"
					>
						<span className="mb-0.5 block text-[9px] font-semibold tracking-wide text-mentor uppercase">
							Heph
						</span>
						Name the impact: “This keeps sessions active while people work.”
					</motion.div>
				</div>
			</motion.div>
		</motion.div>
	);
}

export function LandingHeroSection({
	onSignIn,
	onGoToDashboard,
	isSignedIn,
	onLearnMoreClick,
}: LandingHeroSectionProps) {
	const shouldReduceMotion = useReducedMotion();

	return (
		<section className="relative w-full overflow-hidden bg-background pb-12 pt-8 text-foreground sm:pb-20 sm:pt-16 lg:pb-24 lg:pt-20">
			<div className="pointer-events-none absolute inset-0 z-0">
				<div className="absolute left-1/2 top-0 h-[520px] w-[900px] -translate-x-1/2 rounded-full bg-[radial-gradient(ellipse_at_center,var(--color-mentor)_0%,transparent_68%)] opacity-[0.055]" />
				<div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-border to-transparent" />
			</div>

			<div className="relative z-10 mx-auto grid w-full max-w-7xl items-center gap-8 px-4 md:px-6 sm:gap-14 lg:grid-cols-[0.82fr_1.18fr] lg:gap-8 xl:gap-14">
				<motion.div
					initial={shouldReduceMotion ? false : "hidden"}
					animate="visible"
					variants={{
						hidden: {},
						visible: {
							transition: {
								delayChildren: stagger(0.09),
							},
						},
					}}
					className="mx-auto flex max-w-xl flex-col items-center text-center lg:mx-0 lg:items-start lg:text-left"
				>
					<motion.div
						variants={{
							hidden: { opacity: 0, y: 14 },
							visible: { opacity: 1, y: 0, transition: entranceTransition },
						}}
						className="mb-4 inline-flex items-center gap-2 rounded-full border border-mentor/20 bg-mentor/5 px-3 py-1.5 text-sm font-medium text-mentor sm:mb-5"
					>
						<Hammer className="size-4" />
						Practice feedback for software teams
					</motion.div>

					<motion.h1
						variants={{
							hidden: { opacity: 0, y: 16 },
							visible: { opacity: 1, y: 0, transition: entranceTransition },
						}}
						className="text-balance text-4xl font-bold tracking-[-0.04em] sm:text-5xl md:text-6xl lg:text-[3.6rem] xl:text-6xl"
					>
						Learn from the work you're{" "}
						<span className="relative inline-block whitespace-nowrap">
							<span className="relative z-10">already doing</span>
							<motion.svg
								aria-hidden="true"
								className="absolute -bottom-[0.08em] left-[-0.08em] -z-0 h-[0.34em] w-[calc(100%+0.16em)] overflow-visible text-mentor/30"
								viewBox="0 0 180 20"
								preserveAspectRatio="none"
							>
								<motion.path
									d="M4 13C42 7 83 10 116 7C140 5 160 7 176 4"
									fill="none"
									stroke="currentColor"
									strokeWidth="10"
									strokeLinecap="round"
									initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
									animate={{ pathLength: 1, opacity: 1 }}
									transition={{ duration: 0.7, delay: 0.75, ease: [0.22, 1, 0.36, 1] }}
								/>
							</motion.svg>
						</span>
					</motion.h1>

					<motion.p
						variants={{
							hidden: { opacity: 0, y: 16 },
							visible: { opacity: 1, y: 0, transition: entranceTransition },
						}}
						className="mt-4 max-w-[620px] text-pretty text-lg leading-relaxed text-muted-foreground sm:mt-6 sm:text-xl"
					>
						Hephaestus uses relevant work and context from your team's existing tools to give you
						practice feedback: what worked, what could improve, and what to try next.
					</motion.p>

					<motion.div
						variants={{
							hidden: { opacity: 0, y: 16 },
							visible: { opacity: 1, y: 0, transition: entranceTransition },
						}}
						className="mt-6 flex w-full flex-col items-center gap-3 sm:mt-8 sm:w-auto sm:flex-row lg:items-start"
					>
						<LandingSignInCta
							isSignedIn={isSignedIn}
							onSignIn={onSignIn}
							onGoToDashboard={onGoToDashboard}
							size="lg"
							className="h-11 w-full px-5 shadow-lg shadow-primary/10 sm:w-auto"
						/>
						<Button
							variant="outline"
							size="lg"
							onClick={onLearnMoreClick}
							className="h-11 w-full gap-2 px-5 sm:w-auto"
						>
							See how it works <ChevronDown className="size-4" />
						</Button>
					</motion.div>

					<motion.div
						variants={{
							hidden: { opacity: 0 },
							visible: { opacity: 1, transition: { duration: 0.5, delay: 0.25 } },
						}}
						className="mt-4 flex flex-wrap items-center justify-center gap-2 text-sm text-muted-foreground sm:mt-5 lg:justify-start"
					>
						<span>Works with projects on</span>
						<span className="inline-flex items-center gap-1.5 rounded-full border border-foreground/15 bg-foreground px-2.5 py-1 font-medium text-background shadow-sm">
							<GithubIcon className="size-3.5" aria-hidden="true" />
							GitHub
						</span>
						<span className="inline-flex items-center gap-1.5 rounded-full border border-[#fc6d26]/30 bg-[#fc6d26]/10 px-2.5 py-1 font-medium text-foreground shadow-sm">
							<GitlabIcon className="size-3.5 text-[#e24329]" aria-hidden="true" />
							GitLab
						</span>
					</motion.div>
				</motion.div>

				<LandingFeedbackPreview />
			</div>
		</section>
	);
}
