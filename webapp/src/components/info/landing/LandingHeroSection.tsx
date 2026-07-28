import {
	ArrowRight,
	Check,
	ChevronDown,
	Code2,
	FileText,
	GitPullRequest,
	Lightbulb,
	MessageCircle,
	Sparkles,
} from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { LandingSignInCTA } from "@/components/auth/LandingSignInCTA";
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

function FeedbackPreview() {
	const shouldReduceMotion = useReducedMotion();
	const float = (distance: number, duration: number, delay = 0) =>
		shouldReduceMotion
			? undefined
			: {
					y: [0, -distance, 0],
					transition: {
						duration,
						delay,
						repeat: Number.POSITIVE_INFINITY,
						ease: "easeInOut" as const,
					},
				};

	return (
		<motion.div
			aria-hidden="true"
			initial={shouldReduceMotion ? false : { opacity: 0, y: 24, scale: 0.97 }}
			animate={{ opacity: 1, y: 0, scale: 1 }}
			transition={{ ...entranceTransition, delay: 0.15 }}
			className="relative mx-auto w-full max-w-[680px]"
		>
			<div className="absolute -inset-8 -z-10 bg-[radial-gradient(circle,var(--color-mentor)_0%,transparent_66%)] opacity-[0.08] blur-3xl" />
			<div className="relative overflow-hidden rounded-[2rem] border border-border/70 bg-card/80 p-3 shadow-[0_32px_90px_-36px_rgb(15_23_42_/_0.38)] backdrop-blur-xl sm:p-5 dark:bg-secondary/20 dark:shadow-[0_32px_100px_-42px_rgb(0_0_0_/_0.9)]">
				<motion.div
					animate={
						shouldReduceMotion
							? undefined
							: {
									x: [0, 18, 0],
									y: [0, -10, 0],
									scale: [1, 1.08, 1],
								}
					}
					transition={{
						duration: 12,
						repeat: Number.POSITIVE_INFINITY,
						ease: "easeInOut",
					}}
					className="absolute -right-16 -top-16 size-56 rounded-full bg-mentor/10 blur-3xl"
				/>
				<motion.div
					animate={
						shouldReduceMotion
							? undefined
							: {
									x: [0, -14, 0],
									y: [0, 12, 0],
									scale: [1, 1.12, 1],
								}
					}
					transition={{
						duration: 14,
						repeat: Number.POSITIVE_INFINITY,
						ease: "easeInOut",
					}}
					className="absolute -bottom-20 -left-16 size-64 rounded-full bg-provider-done/10 blur-3xl"
				/>

				<div className="relative z-10 flex items-center justify-between gap-3 border-b border-border/60 px-2 pb-3 sm:px-1">
					<div className="flex items-center gap-2 text-xs font-semibold tracking-[0.14em] text-muted-foreground uppercase">
						<Sparkles className="size-3.5 text-mentor" />
						Practice feedback
					</div>
					<div className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
						<Check className="size-3 text-provider-success-foreground" />
						<span className="sm:hidden">From project work</span>
						<span className="hidden sm:inline">Based on your project work</span>
					</div>
				</div>

				<div className="relative z-10 mt-3 flex flex-wrap justify-center gap-1.5 text-[10px] font-medium text-muted-foreground sm:gap-2 sm:text-xs">
					<span className="rounded-full border border-border/70 bg-background/80 px-2.5 py-1.5 shadow-sm dark:bg-secondary/60">
						Alongside the work
					</span>
					<span className="rounded-full border border-border/70 bg-background/80 px-2.5 py-1.5 shadow-sm dark:bg-secondary/60">
						In your private view
					</span>
					<span className="rounded-full border border-border/70 bg-background/80 px-2.5 py-1.5 shadow-sm dark:bg-secondary/60">
						With Heph
					</span>
				</div>

				<div className="relative h-[510px] sm:h-[430px]">
					<div
						className="absolute inset-0 opacity-45"
						style={{
							backgroundImage:
								"radial-gradient(circle, color-mix(in oklch, var(--border) 75%, transparent) 1px, transparent 1px)",
							backgroundSize: "22px 22px",
							maskImage: "radial-gradient(ellipse at center, black 15%, transparent 74%)",
						}}
					/>

					<svg
						className="absolute inset-0 hidden size-full sm:block"
						viewBox="0 0 640 430"
						fill="none"
						aria-hidden="true"
					>
						<motion.path
							d="M196 103C242 120 264 145 292 184"
							stroke="currentColor"
							className="text-border"
							strokeWidth="1.5"
							strokeDasharray="4 6"
							initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
							animate={{ pathLength: 1, opacity: 1 }}
							transition={{ duration: 0.8, delay: 0.55 }}
						/>
						<motion.path
							d="M445 105C401 120 374 148 346 185"
							stroke="currentColor"
							className="text-border"
							strokeWidth="1.5"
							strokeDasharray="4 6"
							initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
							animate={{ pathLength: 1, opacity: 1 }}
							transition={{ duration: 0.8, delay: 0.65 }}
						/>
						<motion.path
							d="M294 248C262 272 235 294 207 324"
							stroke="currentColor"
							className="text-border"
							strokeWidth="1.5"
							strokeDasharray="4 6"
							initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
							animate={{ pathLength: 1, opacity: 1 }}
							transition={{ duration: 0.8, delay: 0.75 }}
						/>
						<motion.path
							d="M346 248C377 275 407 297 438 324"
							stroke="currentColor"
							className="text-border"
							strokeWidth="1.5"
							strokeDasharray="4 6"
							initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
							animate={{ pathLength: 1, opacity: 1 }}
							transition={{ duration: 0.8, delay: 0.85 }}
						/>
					</svg>

					<motion.div
						initial={shouldReduceMotion ? false : { opacity: 0, x: -18, rotate: -5 }}
						animate={{ opacity: 1, x: 0, rotate: -2 }}
						transition={{ ...entranceTransition, delay: 0.35 }}
						whileHover={shouldReduceMotion ? undefined : { y: -4, rotate: 0 }}
						className="absolute left-1 top-7 w-[47%] rounded-2xl border border-border/70 bg-background/90 p-3 shadow-lg shadow-black/5 backdrop-blur-md sm:left-4 sm:top-9 sm:w-[210px] sm:p-4 dark:bg-secondary/60 dark:shadow-black/30"
					>
						<div className="mb-2 flex items-center gap-2">
							<div className="flex size-7 items-center justify-center rounded-lg bg-provider-accent text-provider-accent-foreground">
								<GitPullRequest className="size-3.5" />
							</div>
							<div>
								<p className="text-[10px] font-semibold tracking-[0.12em] text-muted-foreground uppercase">
									Your project work
								</p>
								<p className="text-xs font-medium sm:text-sm">A focused change</p>
							</div>
						</div>
						<div className="flex flex-wrap gap-1 text-[10px] text-muted-foreground">
							<span className="flex items-center gap-1 rounded-md bg-muted/80 px-1.5 py-1">
								<Code2 className="size-3" /> Code
							</span>
							<span className="flex items-center gap-1 rounded-md bg-muted/80 px-1.5 py-1">
								<FileText className="size-3" /> Context
							</span>
						</div>
					</motion.div>

					<motion.div
						initial={shouldReduceMotion ? false : { opacity: 0, x: 18, rotate: 5 }}
						animate={{ opacity: 1, x: 0, rotate: 2 }}
						transition={{ ...entranceTransition, delay: 0.45 }}
						whileHover={shouldReduceMotion ? undefined : { y: -4, rotate: 0 }}
						className="absolute right-1 top-[96px] w-[51%] rounded-2xl border border-provider-success/40 bg-background/95 p-3 shadow-lg shadow-black/5 backdrop-blur-md sm:right-3 sm:top-10 sm:w-[245px] sm:p-4 dark:bg-secondary/60 dark:shadow-black/30"
					>
						<div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold tracking-[0.12em] text-provider-success-foreground uppercase sm:text-xs">
							<Check className="size-3.5" />
							Worth keeping
						</div>
						<p className="text-xs leading-relaxed sm:text-sm">
							You kept the change focused, so it is easier for someone else to review.
						</p>
					</motion.div>

					<motion.div
						animate={float(6, 5.5)}
						className="absolute left-1/2 top-[205px] z-20 -translate-x-1/2 sm:top-[164px]"
					>
						<motion.div
							initial={shouldReduceMotion ? false : { opacity: 0, scale: 0.7 }}
							animate={{ opacity: 1, scale: 1 }}
							transition={{ type: "spring", stiffness: 190, damping: 16, delay: 0.55 }}
							whileHover={shouldReduceMotion ? undefined : { scale: 1.05, rotate: -2 }}
							className="relative flex size-28 flex-col items-center justify-center rounded-[2rem] border border-mentor/25 bg-background/95 shadow-[0_18px_50px_-18px_var(--color-mentor)] backdrop-blur-xl sm:size-32 dark:bg-secondary"
						>
							<motion.div
								animate={
									shouldReduceMotion
										? undefined
										: {
												scale: [0.9, 1.25, 1.25],
												opacity: [0.25, 0, 0],
											}
								}
								transition={{
									duration: 3,
									repeat: Number.POSITIVE_INFINITY,
									ease: "easeOut",
								}}
								className="absolute inset-2 rounded-[1.6rem] border border-mentor/40"
							/>
							<div className="flex size-14 items-center justify-center rounded-2xl bg-mentor/10 text-mentor sm:size-16">
								<MentorIcon size={48} pad={4} className="sm:h-14 sm:w-14" />
							</div>
							<p className="mt-1 text-sm font-semibold">Hephaestus</p>
							<p className="text-[9px] text-muted-foreground sm:text-[10px]">
								Project work + practices
							</p>
						</motion.div>
					</motion.div>

					<motion.div
						initial={shouldReduceMotion ? false : { opacity: 0, x: -18, rotate: -4 }}
						animate={{ opacity: 1, x: 0, rotate: 1.5 }}
						transition={{ ...entranceTransition, delay: 0.65 }}
						whileHover={shouldReduceMotion ? undefined : { y: -4, rotate: 0 }}
						className="absolute left-1 top-[335px] w-[55%] rounded-2xl border border-warning/35 bg-background/95 p-3 shadow-lg shadow-black/5 backdrop-blur-md sm:left-5 sm:top-auto sm:bottom-5 sm:w-[250px] sm:p-4 dark:bg-secondary/60 dark:shadow-black/30"
					>
						<div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold tracking-[0.12em] text-warning uppercase sm:text-xs">
							<Lightbulb className="size-3.5" />
							Try next
						</div>
						<p className="text-xs leading-relaxed sm:text-sm">
							Add one sentence explaining why the change matters.
						</p>
					</motion.div>

					<motion.div
						initial={shouldReduceMotion ? false : { opacity: 0, x: 18, rotate: 4 }}
						animate={{ opacity: 1, x: 0, rotate: -1.5 }}
						transition={{ ...entranceTransition, delay: 0.75 }}
						whileHover={shouldReduceMotion ? undefined : { y: -4, rotate: 0 }}
						className="absolute right-1 top-[405px] w-[51%] rounded-2xl border border-mentor/25 bg-background/95 p-3 shadow-lg shadow-black/5 backdrop-blur-md sm:right-4 sm:top-auto sm:bottom-3 sm:w-[235px] sm:p-4 dark:bg-secondary/60 dark:shadow-black/30"
					>
						<div className="mb-2 flex items-center gap-1.5 text-[10px] font-semibold tracking-[0.12em] text-mentor uppercase sm:text-xs">
							<MessageCircle className="size-3.5" />
							Talk with Heph
						</div>
						<div className="space-y-1.5 text-[10px] sm:text-xs">
							<p className="mr-4 rounded-xl rounded-bl-sm bg-mentor/10 px-2.5 py-1.5">
								Want help drafting it?
							</p>
							<p className="ml-7 rounded-xl rounded-br-sm bg-muted px-2.5 py-1.5">
								Yes—keep it short.
							</p>
						</div>
					</motion.div>
				</div>

				<div className="relative z-10 flex items-center justify-center gap-2 border-t border-border/60 pt-3 text-xs font-medium text-muted-foreground">
					You decide what to take forward
					<ArrowRight className="size-3.5" />
				</div>
			</div>
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
		<section className="relative w-full overflow-hidden bg-background pb-16 pt-10 text-foreground sm:pb-20 sm:pt-16 lg:pb-24 lg:pt-20">
			<div className="pointer-events-none absolute inset-0 -z-0">
				<div className="absolute left-1/2 top-0 h-[520px] w-[900px] -translate-x-1/2 rounded-full bg-[radial-gradient(ellipse_at_center,var(--color-mentor)_0%,transparent_68%)] opacity-[0.055]" />
				<div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-border to-transparent" />
			</div>

			<div className="container relative z-10 mx-auto grid max-w-7xl items-center gap-14 px-4 md:px-6 lg:grid-cols-[0.82fr_1.18fr] lg:gap-8 xl:gap-14">
				<motion.div
					initial={shouldReduceMotion ? false : "hidden"}
					animate="visible"
					variants={{
						hidden: {},
						visible: {
							transition: {
								staggerChildren: 0.09,
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
						className="mb-5 inline-flex items-center gap-2 rounded-full border border-mentor/20 bg-mentor/5 px-3 py-1.5 text-sm font-medium text-mentor"
					>
						<MentorIcon size={20} animated={!shouldReduceMotion} />
						Practice feedback for software teams
					</motion.div>

					<motion.h1
						variants={{
							hidden: { opacity: 0, y: 16 },
							visible: { opacity: 1, y: 0, transition: entranceTransition },
						}}
						className="text-balance text-4xl font-bold tracking-[-0.04em] sm:text-5xl md:text-6xl lg:text-[3.6rem] xl:text-6xl"
					>
						Learn from the work you're already doing
					</motion.h1>

					<motion.p
						variants={{
							hidden: { opacity: 0, y: 16 },
							visible: { opacity: 1, y: 0, transition: entranceTransition },
						}}
						className="mt-6 max-w-[620px] text-pretty text-lg leading-relaxed text-muted-foreground sm:text-xl"
					>
						Hephaestus turns the work your project already records into practical feedback on your
						engineering practices—what worked, what could improve, and what to try next.
					</motion.p>

					<motion.div
						variants={{
							hidden: { opacity: 0, y: 16 },
							visible: { opacity: 1, y: 0, transition: entranceTransition },
						}}
						className="mt-8 flex w-full flex-col items-center gap-3 sm:w-auto sm:flex-row lg:items-start"
					>
						<LandingSignInCTA
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

					<motion.p
						variants={{
							hidden: { opacity: 0 },
							visible: { opacity: 1, transition: { duration: 0.5, delay: 0.25 } },
						}}
						className="mt-5 text-sm text-muted-foreground"
					>
						Works with GitHub and GitLab projects
					</motion.p>
				</motion.div>

				<FeedbackPreview />
			</div>
		</section>
	);
}
