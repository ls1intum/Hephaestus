import { ArrowRight, BookOpenCheck, Radio, ScanSearch } from "lucide-react";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface LandingWhyChooseSectionProps {
	onSignIn: () => void;
	onGoToDashboard?: () => void;
	isSignedIn: boolean;
}

interface Differentiator {
	icon: React.ComponentType<{ className?: string }>;
	title: string;
	body: string;
}

const DIFFERENTIATORS: Differentiator[] = [
	{
		icon: ScanSearch,
		title: "Reads the whole lifecycle",
		body: "Descriptions, commits, review threads, related issues, and the contributor's prior history all feed detection — the full signal, not just the patch.",
	},
	{
		icon: BookOpenCheck,
		title: "Practices are first-class",
		body: "Each practice is a versioned, inspectable artifact with a category, detection criteria, and trigger events. You read it, you fork it, you change it.",
	},
	{
		icon: Radio,
		title: "Findings reach people through coupled channels",
		body: "In-context for the moment, on a private dashboard for reflection, in conversation for articulation. Educators get their own surface.",
	},
];

export function LandingWhyChooseSection({
	onSignIn,
	onGoToDashboard,
	isSignedIn,
}: LandingWhyChooseSectionProps) {
	return (
		<section className="w-full py-12 md:py-20 bg-gradient-to-b from-background to-muted/30">
			<div className="container px-4 md:px-6">
				<div className="max-w-3xl mb-10">
					<Badge className="mb-4" variant="outline">
						How Hephaestus works
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						Findings tied to evidence and an action.
					</h2>
					<p className="text-lg text-muted-foreground">
						The unit of value is a <strong className="text-foreground">finding</strong>: which
						practice, what verdict, the evidence that supports it, and the action a contributor can
						take. Findings are about the work, not the worker.
					</p>
				</div>

				<div className="grid gap-6 md:grid-cols-3 mb-10">
					{DIFFERENTIATORS.map((d) => {
						const Icon = d.icon;
						return (
							<div key={d.title} className="space-y-2">
								<div className="flex h-10 w-10 items-center justify-center rounded-md bg-primary/10">
									<Icon className="h-5 w-5 text-primary" />
								</div>
								<h3 className="font-semibold">{d.title}</h3>
								<p className="text-sm text-muted-foreground">{d.body}</p>
							</div>
						);
					})}
				</div>

				<div className="flex flex-col sm:flex-row sm:items-center gap-4">
					{isSignedIn ? (
						<Button onClick={onGoToDashboard} className="gap-2">
							Go to Dashboard <ArrowRight className="h-4 w-4" />
						</Button>
					) : (
						<GitHubSignInButton onClick={onSignIn} className="w-full justify-center sm:w-auto" />
					)}
					<p className="text-sm text-muted-foreground">
						Open-source. Self-hostable. Bring your own LLM provider.
					</p>
				</div>
			</div>
		</section>
	);
}
