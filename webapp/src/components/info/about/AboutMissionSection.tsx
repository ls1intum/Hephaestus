import { ArrowRight, BookOpen, ShieldCheck, Sparkles } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";

interface Commitment {
	icon: React.ComponentType<{ className?: string }>;
	title: string;
	body: string;
}

const COMMITMENTS: Commitment[] = [
	{
		icon: BookOpen,
		title: "Formative, not evaluative",
		body: "Findings target the practice and the action — never the contributor's worth. Task and process feedback over self-level feedback. Aggregate scoring, leagues, and rankings are not feedback in the formative sense; they are evaluation by another name.",
	},
	{
		icon: Sparkles,
		title: "Autonomy-supportive by design",
		body: "Most channels are pull, not push. The in-context channel is narrow and addresses authors only. Contributors choose when to read the dashboard and when to talk to the mentor. The platform invites engagement; it does not coerce it.",
	},
	{
		icon: ShieldCheck,
		title: "No surveillance dynamics",
		body: "Author-side practices push to authors. Reviewer-side practices appear only on private reflection surfaces. Findings are about the work, not the worker. Public ranking is not a default product feature.",
	},
];

export function AboutMissionSection() {
	return (
		<div className="space-y-16">
			{/* The problem */}
			<section className="space-y-4">
				<Badge variant="outline" className="w-fit">
					The problem
				</Badge>
				<h2 className="text-3xl font-bold">Practices are tacit. Mentorship is scarce.</h2>
				<div className="space-y-4 text-muted-foreground leading-relaxed">
					<p>
						Early-stage software engineers learn the trade twice: once as a body of technical
						knowledge, and once as a set of{" "}
						<strong className="text-foreground">professional practices</strong> — justifying
						changes, scoping work, engaging in review, following through. Industry surveys
						consistently report graduates underprepared on the second track, even when the first one
						is solid.
					</p>
					<p>
						Practices show up in the trace of collaboration, not in the curriculum. They are learned
						by doing the work, by getting feedback, and by watching coaches in action. Generative-AI
						assistants compound the problem: they accelerate prepared contributors and create an
						illusion of competence in struggling ones. Good coaches close the gap — but coaches are
						scarce. Bots fill the silence with noise; analytics tools fill it with scores. Neither
						develops practice.
					</p>
				</div>
			</section>

			<Separator />

			{/* The method */}
			<section className="space-y-4">
				<Badge variant="outline" className="w-fit">
					Our approach
				</Badge>
				<h2 className="text-3xl font-bold">A method, not just a platform.</h2>
				<div className="space-y-4 text-muted-foreground leading-relaxed">
					<p>
						Hephaestus contributes a method for{" "}
						<strong className="text-foreground">practice-aware formative feedback</strong> in
						software project work. The method has two parts: a <em>process</em> for detecting
						practices from full pull-request lifecycle activity, and a versioned{" "}
						<em>practice-definition format</em> that specifies each practice — its category,
						criteria, and the events it triggers on.
					</p>
					<p>
						The reference implementation routes findings to four coupled channels: in-context push
						for authors, a private reflection dashboard, a conversational mentor, and a facilitator
						dashboard for educators. Different feedback levels reach different audiences at the
						moments they can act.
					</p>
				</div>
				<div className="pt-2">
					<a
						href="/contributor/conceptual-model"
						className="inline-flex items-center gap-1 text-sm font-medium underline underline-offset-4 hover:text-primary"
					>
						See the conceptual model <ArrowRight className="h-4 w-4" />
					</a>
				</div>
			</section>

			<Separator />

			{/* Theoretical commitments */}
			<section className="space-y-6">
				<div className="space-y-4">
					<Badge variant="outline" className="w-fit">
						What we will not do
					</Badge>
					<h2 className="text-3xl font-bold">Three commitments we hold across the platform.</h2>
					<p className="text-muted-foreground leading-relaxed">
						These constraints shape every product decision — what surfaces exist, what channels push
						versus pull, what shows up on whose screen. They are easy to articulate and hard to
						maintain when convenient compromises present themselves.
					</p>
				</div>

				<div className="grid gap-6 md:grid-cols-3">
					{COMMITMENTS.map((c) => {
						const Icon = c.icon;
						return (
							<div key={c.title} className="space-y-2">
								<div className="flex h-10 w-10 items-center justify-center rounded-md bg-primary/10">
									<Icon className="h-5 w-5 text-primary" />
								</div>
								<h3 className="font-semibold">{c.title}</h3>
								<p className="text-sm text-muted-foreground">{c.body}</p>
							</div>
						);
					})}
				</div>
			</section>

			<Separator />

			{/* Honest limitations */}
			<section className="space-y-4">
				<Alert>
					<AlertTitle>Honest limitations</AlertTitle>
					<AlertDescription>
						<ul className="list-disc list-outside ml-5 space-y-1.5">
							<li>
								Today's recognition layer surfaces contribution activity volume, not practice
								mastery. Practice-aware recognition is on the roadmap.
							</li>
							<li>
								Practice findings are delivered to PR/MR comments today; a contributor-facing
								dashboard for findings is in development.
							</li>
							<li>
								The conversational mentor is a reflection partner, not a coding agent — and the
								surface will continue to evolve as we learn how it best supports self-regulated
								practice growth.
							</li>
							<li>
								GitHub has full integration today; GitLab covers webhook ingestion and practice
								detection. Closing the gap is on the roadmap.
							</li>
						</ul>
					</AlertDescription>
				</Alert>
			</section>

			<Separator />

			{/* Open & academic */}
			<section className="space-y-4">
				<Badge variant="outline" className="w-fit">
					Open and academic
				</Badge>
				<h2 className="text-3xl font-bold">
					Built at TUM. Open-source. Designed to be replicated.
				</h2>
				<p className="text-muted-foreground leading-relaxed">
					Hephaestus is developed at the{" "}
					<a
						className="underline underline-offset-4 hover:text-foreground"
						href="https://aet.cit.tum.de/"
						target="_blank"
						rel="noopener noreferrer"
					>
						Applied Education Technologies
					</a>{" "}
					group at TUM. The platform is the artifact of an ongoing PhD investigation into
					practice-aware feedback for software project work. Code, models, and practice catalogs are
					open — fork them, run them on your own infrastructure, and tell us what you learn.
				</p>
			</section>
		</div>
	);
}
