import {
	ArrowDown,
	ArrowRight,
	Check,
	ClipboardCheck,
	Code2,
	FileText,
	GitPullRequest,
	Lightbulb,
	MessageSquareText,
	RotateCcw,
	TrendingUp,
} from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Badge } from "@/components/ui/badge";

const enter = {
	duration: 0.6,
	ease: [0.22, 1, 0.36, 1],
} as const;

interface StageNumberProps {
	number: number;
}

function StageNumber({ number }: StageNumberProps) {
	const shouldReduceMotion = useReducedMotion();
	const pulseDelay = (number - 1) * 0.55;

	return (
		<motion.span
			className="relative flex size-8 shrink-0 items-center justify-center rounded-full bg-mentor text-xs font-bold text-white"
			animate={shouldReduceMotion ? undefined : { scale: [1, 1.08, 1] }}
			transition={{
				duration: 4.8,
				times: [0, 0.08, 0.18],
				delay: pulseDelay,
				repeat: Number.POSITIVE_INFINITY,
				ease: [0.22, 1, 0.36, 1],
			}}
		>
			<motion.span
				aria-hidden="true"
				className="absolute inset-0 rounded-full border border-mentor/70"
				initial={{ opacity: 0, scale: 1 }}
				animate={
					shouldReduceMotion
						? { opacity: 0, scale: 1 }
						: {
								opacity: [0, 0.32, 0],
								scale: [1, 1.06, 1.5],
							}
				}
				transition={{
					duration: 4.8,
					times: [0, 0.08, 0.2],
					delay: pulseDelay,
					repeat: Number.POSITIVE_INFINITY,
					ease: [0.22, 1, 0.36, 1],
				}}
			/>
			{number}
		</motion.span>
	);
}

interface StageProps {
	children: React.ReactNode;
	className: string;
	delay: number;
}

function Stage({ children, className, delay }: StageProps) {
	const shouldReduceMotion = useReducedMotion();

	return (
		<motion.article
			initial={shouldReduceMotion ? false : { opacity: 0, y: 18, scale: 0.98 }}
			whileInView={{ opacity: 1, y: 0, scale: 1 }}
			viewport={{ once: true, amount: 0.45 }}
			transition={{ ...enter, delay }}
			className={className}
		>
			{children}
		</motion.article>
	);
}

function ProjectWorkStage({ mobile = false }: { mobile?: boolean }) {
	return (
		<Stage
			delay={0.05}
			className={
				mobile
					? "relative rounded-3xl border border-border bg-background p-4 shadow-sm sm:p-5"
					: "absolute left-0 top-[64px] z-10 w-[285px] rounded-3xl border border-border bg-background p-6 shadow-[0_20px_50px_-30px_rgb(15_23_42_/_0.45)] dark:shadow-black/50"
			}
		>
			<div className="flex items-center gap-3">
				<StageNumber number={1} />
				<div>
					<p className="text-xs font-semibold tracking-[0.12em] text-mentor uppercase">
						Project work
					</p>
					<h3 className="mt-0.5 text-xl font-semibold">Your work and its context</h3>
				</div>
			</div>
			<p className="mt-4 text-sm leading-relaxed text-muted-foreground">
				Work and context already available in the tools your team uses.
			</p>
			<div className={mobile ? "mt-5 grid grid-cols-4 gap-2" : "mt-5 grid grid-cols-2 gap-2"}>
				{[
					{ icon: Code2, label: "Code" },
					{ icon: ClipboardCheck, label: "Tasks" },
					{ icon: MessageSquareText, label: "Discussion" },
					{ icon: FileText, label: "Docs" },
				].map(({ icon: Icon, label }) => (
					<div
						key={label}
						className={
							mobile
								? "flex min-w-0 flex-col items-center gap-1.5 rounded-xl border border-border/70 bg-muted/30 px-1 py-2.5 text-[10px] text-muted-foreground"
								: "flex items-center gap-2 rounded-xl border border-border/70 bg-muted/30 px-3 py-2.5 text-xs text-muted-foreground"
						}
					>
						<Icon className="size-4 text-mentor" strokeWidth={1.7} />
						{label}
					</div>
				))}
			</div>
		</Stage>
	);
}

function ReviewStage({ mobile = false }: { mobile?: boolean }) {
	return (
		<Stage
			delay={0.15}
			className={
				mobile
					? "rounded-3xl border border-border bg-background p-4 shadow-sm sm:p-5"
					: "absolute left-[325px] top-[70px] z-10 w-[300px] rounded-3xl border border-border bg-background p-6 shadow-[0_20px_50px_-30px_rgb(15_23_42_/_0.45)] dark:shadow-black/50"
			}
		>
			<div className="flex items-center gap-3">
				<StageNumber number={2} />
				<div>
					<p className="text-xs font-semibold tracking-[0.12em] text-mentor uppercase">
						Practice review
					</p>
					<h3 className="mt-0.5 text-xl font-semibold">Hephaestus reviews the work</h3>
				</div>
			</div>
			<p className="mt-4 text-sm leading-relaxed text-muted-foreground">
				Hephaestus checks evidence from the work against the engineering practices your workspace
				chose.
			</p>
			<div className="mt-5 flex items-center gap-3">
				<span className="rounded-xl border border-border bg-muted/30 px-3 py-2 text-sm">
					Evidence
				</span>
				<span className="text-lg text-muted-foreground">+</span>
				<span className="rounded-xl border border-mentor/30 bg-mentor/10 px-3 py-2 text-sm text-mentor">
					Practice
				</span>
			</div>
		</Stage>
	);
}

function FeedbackStage({ mobile = false }: { mobile?: boolean }) {
	return (
		<Stage
			delay={0.25}
			className={
				mobile
					? "rounded-3xl border border-mentor/35 bg-mentor/5 p-4 shadow-[0_24px_60px_-32px_var(--color-mentor)] sm:p-5"
					: "absolute right-0 top-[32px] z-10 w-[500px] rounded-3xl border border-mentor/35 bg-mentor/5 p-6 shadow-[0_28px_70px_-34px_var(--color-mentor)]"
			}
		>
			<div className="flex items-center gap-3">
				<StageNumber number={3} />
				<div>
					<p className="text-xs font-semibold tracking-[0.12em] text-mentor uppercase">
						Practice feedback
					</p>
					<h3 className="mt-0.5 text-2xl font-semibold">Feedback reaches you</h3>
				</div>
			</div>

			<div className={mobile ? "mt-5 grid gap-3 sm:grid-cols-3" : "mt-5 grid gap-3"}>
				{[
					{ icon: Check, label: "What worked" },
					{ icon: TrendingUp, label: "What could improve" },
					{ icon: Lightbulb, label: "What to try next" },
				].map(({ icon: Icon, label }) => (
					<div key={label} className="flex items-center gap-3 text-sm">
						<span className="flex size-7 shrink-0 items-center justify-center rounded-full border border-mentor/25 bg-background text-mentor dark:bg-secondary">
							<Icon className="size-3.5" strokeWidth={1.8} />
						</span>
						{label}
					</div>
				))}
			</div>

			<div className="mt-5 border-t border-mentor/20 pt-4">
				<p className="text-xs font-semibold tracking-[0.12em] text-mentor uppercase">
					Where feedback can appear today
				</p>
				<div className="mt-3 grid gap-2 sm:grid-cols-2">
					<div className="flex items-center gap-2 rounded-full border border-mentor/25 bg-background/80 px-3 py-2 text-xs dark:bg-secondary/80">
						<GitPullRequest className="size-4 text-mentor" />
						Alongside the work
					</div>
					<div className="flex items-center gap-2 rounded-full border border-mentor/25 bg-background/80 px-3 py-2 text-xs dark:bg-secondary/80">
						<MentorIcon size={20} pad={3} />
						In conversation with Heph
					</div>
				</div>
			</div>
		</Stage>
	);
}

function ChoiceStage({ mobile = false }: { mobile?: boolean }) {
	return (
		<Stage
			delay={0.35}
			className={
				mobile
					? "rounded-3xl border border-mentor/35 bg-background p-4 shadow-sm sm:p-5"
					: "absolute bottom-[50px] left-[400px] z-10 w-[650px] rounded-3xl border border-mentor/35 bg-background p-5 shadow-[0_20px_50px_-30px_rgb(15_23_42_/_0.45)] dark:shadow-black/50"
			}
		>
			<div
				className={
					mobile
						? "space-y-4 sm:flex sm:items-center sm:gap-6 sm:space-y-0"
						: "flex items-center gap-6"
				}
			>
				<div className="flex items-center gap-3">
					<StageNumber number={4} />
					<div>
						<p className="text-xs font-semibold tracking-[0.12em] text-mentor uppercase">
							You stay in charge
						</p>
						<h3 className="mt-0.5 text-xl font-semibold">You decide what helps</h3>
					</div>
				</div>
				<div className="flex flex-wrap gap-1.5 sm:gap-2">
					{["Use it", "Question it", "Skip it"].map((choice, index) => (
						<span
							key={choice}
							className={
								index === 2
									? "rounded-full border border-border bg-muted/40 px-3 py-2 text-sm text-muted-foreground"
									: "rounded-full border border-mentor/25 bg-mentor/10 px-3 py-2 text-sm text-mentor"
							}
						>
							{choice}
						</span>
					))}
				</div>
			</div>
		</Stage>
	);
}

function DesktopFeedbackLoop({ forceVisible = false }: { forceVisible?: boolean }) {
	const shouldReduceMotion = useReducedMotion();
	const connectorTransition = { duration: 0.85, ease: [0.22, 1, 0.36, 1] } as const;

	return (
		<div
			className={
				forceVisible
					? "relative mx-auto h-[590px] max-w-[1160px]"
					: "relative mx-auto mt-14 hidden h-[590px] max-w-[1160px] xl:block"
			}
		>
			<svg
				aria-hidden="true"
				className="absolute inset-0 size-full"
				viewBox="0 0 1160 590"
				fill="none"
			>
				<defs>
					<marker
						id="loop-arrow"
						viewBox="0 0 10 10"
						refX="8"
						refY="5"
						markerWidth="7"
						markerHeight="7"
						orient="auto"
					>
						<path
							d="m1 1 7 4-7 4"
							fill="none"
							stroke="var(--color-mentor)"
							strokeWidth="1.7"
							strokeLinecap="round"
							strokeLinejoin="round"
						/>
					</marker>
				</defs>
				{[
					{ d: "M285 188H325", delay: 0.2 },
					{ d: "M625 188H660", delay: 0.35 },
					{ d: "M1130 365C1180 405 1150 460 1050 475", delay: 0.5 },
					{ d: "M400 500C250 582 42 560 34 330", delay: 0.65 },
				].map(({ d, delay }) => (
					<motion.path
						key={d}
						d={d}
						stroke="currentColor"
						className="text-mentor/65"
						strokeWidth="2"
						strokeLinecap="round"
						markerEnd="url(#loop-arrow)"
						initial={shouldReduceMotion ? false : { pathLength: 0, opacity: 0 }}
						whileInView={{ pathLength: 1, opacity: 1 }}
						viewport={{ once: true, amount: 0.7 }}
						transition={{ ...connectorTransition, delay }}
					/>
				))}
			</svg>

			<ProjectWorkStage />
			<ReviewStage />
			<FeedbackStage />
			<ChoiceStage />

			<RepeatLabel className="absolute bottom-0 left-28 bg-muted/15 px-3" />
		</div>
	);
}

function TabletFeedbackLoop() {
	return (
		<div className="mx-auto mt-12 hidden max-w-4xl sm:block xl:hidden">
			<div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-4">
				<ProjectWorkStage mobile />
				<ArrowRight className="size-6 text-mentor/70" strokeWidth={1.8} aria-hidden="true" />
				<ReviewStage mobile />
			</div>
			<MobileArrow />
			<FeedbackStage mobile />
			<MobileArrow />
			<ChoiceStage mobile />
			<div className="mx-auto max-w-lg">
				<CycleReturn />
			</div>
		</div>
	);
}

function MobileFeedbackLoop({ forceVisible = false }: { forceVisible?: boolean }) {
	return (
		<div
			className={
				forceVisible ? "relative mx-auto max-w-md" : "relative mx-auto mt-12 max-w-md sm:hidden"
			}
		>
			<ProjectWorkStage mobile />
			<MobileArrow />
			<ReviewStage mobile />
			<MobileArrow />
			<FeedbackStage mobile />
			<MobileArrow />
			<ChoiceStage mobile />
			<CycleReturn />
		</div>
	);
}

function CycleReturn() {
	const shouldReduceMotion = useReducedMotion();

	return (
		<motion.div
			initial={shouldReduceMotion ? false : { opacity: 0 }}
			whileInView={{ opacity: 1 }}
			viewport={{ once: true, amount: 0.8 }}
			transition={{ duration: 0.45 }}
			className="relative h-16"
		>
			<svg aria-hidden="true" className="absolute inset-0 size-full" viewBox="0 0 400 64">
				<motion.path
					d="M200 0v6c0 23-20 36-48 36H24"
					fill="none"
					stroke="currentColor"
					className="text-mentor/65"
					strokeWidth="2"
					strokeLinecap="round"
					initial={shouldReduceMotion ? false : { pathLength: 0 }}
					whileInView={{ pathLength: 1 }}
					viewport={{ once: true, amount: 0.8 }}
					transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
				/>
				<motion.path
					d="m32 36-8 6 8 6"
					fill="none"
					stroke="currentColor"
					className="text-mentor/65"
					strokeWidth="2"
					strokeLinecap="round"
					strokeLinejoin="round"
					initial={shouldReduceMotion ? false : { opacity: 0 }}
					whileInView={{ opacity: 1 }}
					viewport={{ once: true }}
					transition={{ duration: 0.2, delay: 0.5 }}
				/>
			</svg>
			<RepeatLabel className="absolute bottom-0 left-2 bg-muted/15 pr-3" />
		</motion.div>
	);
}

function RepeatLabel({ className }: { className: string }) {
	const shouldReduceMotion = useReducedMotion();

	return (
		<div
			className={`flex items-center gap-2 text-xs font-semibold tracking-[0.12em] text-mentor uppercase ${className}`}
		>
			<motion.span
				className="inline-flex"
				animate={shouldReduceMotion ? undefined : { rotate: [0, -35, 0] }}
				transition={{
					duration: 4.8,
					times: [0, 0.1, 0.22],
					delay: 2.15,
					repeat: Number.POSITIVE_INFINITY,
					ease: [0.22, 1, 0.36, 1],
				}}
			>
				<RotateCcw className="size-3.5" strokeWidth={1.8} />
			</motion.span>
			Next project work
		</div>
	);
}

function MobileArrow() {
	const shouldReduceMotion = useReducedMotion();

	return (
		<motion.div
			aria-hidden="true"
			initial={shouldReduceMotion ? false : { opacity: 0, scaleY: 0.5 }}
			whileInView={{ opacity: 1, scaleY: 1 }}
			viewport={{ once: true, amount: 0.8 }}
			transition={{ duration: 0.35 }}
			className="flex h-8 origin-top items-center justify-center text-mentor/70"
		>
			<ArrowDown className="size-5" strokeWidth={1.8} />
		</motion.div>
	);
}

type LandingFeedbackLoopLayout = "responsive" | "desktop" | "mobile";

interface LandingFeedbackLoopProps {
	layout?: LandingFeedbackLoopLayout;
}

export function LandingFeedbackLoop({ layout = "responsive" }: LandingFeedbackLoopProps) {
	if (layout === "desktop") {
		return <DesktopFeedbackLoop forceVisible />;
	}

	if (layout === "mobile") {
		return <MobileFeedbackLoop forceVisible />;
	}

	return (
		<>
			<DesktopFeedbackLoop />
			<TabletFeedbackLoop />
			<MobileFeedbackLoop />
		</>
	);
}

export function LandingFeaturesSection() {
	const shouldReduceMotion = useReducedMotion();

	return (
		<section
			id="features"
			className="relative w-full overflow-hidden border-y border-border/60 bg-muted/15 py-16 md:py-20 xl:py-24"
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
					transition={enter}
					className="mx-auto max-w-5xl text-center"
				>
					<Badge className="mb-4" variant="outline">
						How feedback works
					</Badge>
					<h2 className="text-balance text-3xl font-bold tracking-[-0.035em] sm:text-4xl md:text-5xl">
						From project work to practice feedback
					</h2>
					<p className="mx-auto mt-5 max-w-2xl text-pretty text-lg leading-relaxed text-muted-foreground">
						Hephaestus reviews evidence from project work against the practices chosen for the
						workspace, then gives the developer feedback they can use, question, or skip.
					</p>
				</motion.div>

				<LandingFeedbackLoop />
			</div>
		</section>
	);
}
