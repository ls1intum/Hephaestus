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
		title: "Lifecycle, not diff",
		body: "Diff-level review bots see only the patch. Hephaestus reads descriptions, commits, review threads, related issues, and the contributor's prior history — the signals that constitute *practice*, not just defects.",
	},
	{
		icon: BookOpenCheck,
		title: "Versioned definitions, not vendor prompts",
		body: "Each practice is a first-class artifact with a category, detection criteria, and trigger events. You can read it, fork it, and change it. Detection isn't an opaque prompt buried in a SaaS pipeline.",
	},
	{
		icon: Radio,
		title: "Coupled channels, not a single feed",
		body: "Findings route to where the contributor can act on them: in-context for the moment, on a private dashboard for reflection, in conversation for articulation. Educators get their own surface. No public ranking.",
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
						How Hephaestus differs
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						Practice findings, not scores.
					</h2>
					<p className="text-lg text-muted-foreground">
						Developer-analytics tools surface aggregate metrics for managers. Diff-level review bots
						annotate patches for defects. Hephaestus does neither. It detects the{" "}
						<strong className="text-foreground">practices</strong> that constitute professional
						contribution and feeds them back to the contributor as actionable findings tied to
						evidence.
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
