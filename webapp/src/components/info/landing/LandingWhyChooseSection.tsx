import { ArrowRight, Layers, MessageCircle, ScanSearch } from "lucide-react";
import agileHephaestus from "@/assets/agile_hephaestus.png";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface LandingWhyChooseSectionProps {
	onSignIn: () => void;
	onGoToDashboard?: () => void;
	isSignedIn: boolean;
}

export function LandingWhyChooseSection({
	onSignIn,
	onGoToDashboard,
	isSignedIn,
}: LandingWhyChooseSectionProps) {
	return (
		<section className="w-full py-8 md:py-16 bg-gradient-to-b from-background to-muted/30">
			<div className="container px-4 md:px-6">
				<div className="grid gap-10 lg:grid-cols-[1fr_500px] lg:gap-12">
					<img
						src={agileHephaestus}
						alt="Agile Hephaestus"
						width="500"
						height="350"
						className="mx-auto rounded-lg aspect-auto object-cover lg:order-last"
					/>
					<div className="flex flex-col justify-center space-y-5">
						<Badge className="w-fit" variant="outline">
							What's Different
						</Badge>
						<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl">
							Practices, Not Just Metrics
						</h2>
						<p className="text-lg text-muted-foreground">
							Most engineering analytics tools produce summative dashboards for managers. Hephaestus
							delivers formative feedback to the people doing the work — detecting practice patterns
							across pull requests, reviews, and issues, then guiding improvement through the
							channel that fits the moment.
						</p>

						<ul className="grid gap-4 mt-4">
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<ScanSearch className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Detects what dashboards miss</p>
									<p className="text-sm text-muted-foreground">
										Goes beyond cycle time to spot the practices that actually shape code quality —
										across pull requests, reviews, and collaboration patterns.
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Layers className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Four dimensions of health</p>
									<p className="text-sm text-muted-foreground">
										Looks at code quality, workflow, collaboration, and learning together — so
										improving one area doesn't come at the expense of the others.
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<MessageCircle className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Coaching, not surveillance</p>
									<p className="text-sm text-muted-foreground">
										Feedback goes to the person who can act on it — not their manager. Formative,
										not summative.
									</p>
								</div>
							</li>
						</ul>

						<div className="pt-4">
							{isSignedIn ? (
								<Button onClick={onGoToDashboard} className="gap-2">
									Go to Dashboard <ArrowRight className="h-4 w-4" />
								</Button>
							) : (
								<GitHubSignInButton
									onClick={onSignIn}
									className="w-full justify-center sm:w-auto"
								/>
							)}
						</div>
					</div>
				</div>
			</div>
		</section>
	);
}
