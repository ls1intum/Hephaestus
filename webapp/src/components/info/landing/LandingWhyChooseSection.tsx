import { Code, Hammer, Users } from "lucide-react";
import agileHephaestus from "@/assets/agile_hephaestus.png";
import { LandingSignInCTA } from "@/components/auth/LandingSignInCTA";
import { Badge } from "@/components/ui/badge";

interface LandingWhyChooseSectionProps {
	onSignIn: (idpHint: string) => void;
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
						alt="Illustration of Heph, the Hephaestus mentor, working alongside a software team"
						width="500"
						height="350"
						className="mx-auto rounded-lg aspect-auto object-cover lg:order-last"
					/>
					<div className="flex flex-col justify-center space-y-5">
						<Badge className="w-fit" variant="outline">
							Our approach
						</Badge>
						<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl">
							Why choose Hephaestus?
						</h2>
						<p className="text-lg text-muted-foreground">
							Good mentors are rare and their time is scarce. Hephaestus carries the routine
							feedback no one has time to give everyone, so that every developer gets some. It
							supports mentors rather than replacing them.
						</p>

						<ul className="grid gap-4 mt-4">
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Hammer className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Grounded in real work</p>
									<p className="text-sm text-muted-foreground">
										Feedback comes from your actual pull requests, issues, and reviews
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Users className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">You stay in charge</p>
									<p className="text-sm text-muted-foreground">
										Feedback is advisory. You decide what to take up and what to skip
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Code className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Fits your team's setup</p>
									<p className="text-sm text-muted-foreground">
										Workspaces connect GitHub, GitLab, and Slack, with optional features like
										achievements and a weekly leaderboard
									</p>
								</div>
							</li>
						</ul>

						<div className="pt-4">
							<LandingSignInCTA
								isSignedIn={isSignedIn}
								onSignIn={onSignIn}
								onGoToDashboard={onGoToDashboard}
								size="default"
								className="w-full sm:w-auto"
							/>
						</div>
					</div>
				</div>
			</div>
		</section>
	);
}
