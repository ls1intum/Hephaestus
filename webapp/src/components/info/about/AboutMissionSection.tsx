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
		title: "Formative feedback",
		body: "Findings target a practice and the action a contributor can take. Each carries evidence and a recommended next step, so coaching has somewhere concrete to land.",
	},
	{
		icon: Sparkles,
		title: "Autonomy-supportive delivery",
		body: "Most channels are pull, addressed to the contributor on their schedule. The in-context channel is narrow and addresses the author of the contribution. Contributors choose when to read the dashboard and when to talk to the mentor.",
	},
	{
		icon: ShieldCheck,
		title: "Asymmetric scoping",
		body: "Author-side practices reach authors. Reviewer-side practices appear on private reflection surfaces. Facilitators see aggregate signals to support coaching.",
	},
];

export function AboutMissionSection() {
	return (
		<div className="space-y-16">
			{/* The problem */}
			<section className="space-y-4">
				<Badge variant="outline" className="w-fit">
					Why Hephaestus
				</Badge>
				<h2 className="text-3xl font-bold">Practices grow from feedback. Feedback is scarce.</h2>
				<div className="space-y-4 text-muted-foreground leading-relaxed">
					<p>
						Early-stage software engineers learn the trade twice: once as a body of technical
						knowledge, and once as a set of{" "}
						<strong className="text-foreground">professional practices</strong> — justifying
						changes, scoping work, engaging in review, following through. The second track is
						learned in the trace of collaboration: by doing the work, getting feedback on it, and
						watching coaches in action.
					</p>
					<p>
						Hephaestus extends that coaching presence into the gaps where no human is available. It
						observes the full pull-request lifecycle, evaluates each contribution against the
						practices a project defines, and routes findings — with evidence and a recommended
						action — to the contributor in the channel that fits the moment.
					</p>
				</div>
			</section>

			<Separator />

			{/* The method */}
			<section className="space-y-4">
				<Badge variant="outline" className="w-fit">
					Our approach
				</Badge>
				<h2 className="text-3xl font-bold">A method, plus the platform that runs it.</h2>
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

			{/* Design commitments */}
			<section className="space-y-6">
				<div className="space-y-4">
					<Badge variant="outline" className="w-fit">
						How we design
					</Badge>
					<h2 className="text-3xl font-bold">Three commitments that shape the platform.</h2>
					<p className="text-muted-foreground leading-relaxed">
						These principles drive every product decision — what surfaces exist, what channels push
						versus pull, what shows up on whose screen.
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

			{/* What's next */}
			<section className="space-y-4">
				<Alert>
					<AlertTitle>What's next</AlertTitle>
					<AlertDescription>
						<ul className="list-disc list-outside ml-5 space-y-1.5">
							<li>
								Practice-aware recognition: indicators that reflect mastery and growth across the
								practice catalog.
							</li>
							<li>
								A contributor-facing findings dashboard for the reflection channel — list, filter,
								and respond to findings inside the webapp.
							</li>
							<li>
								Continued evolution of the conversational mentor as a partner for planning and
								reflection grounded in finding history.
							</li>
							<li>GitHub/GitLab parity across diff notes, approvals, and label sync.</li>
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
