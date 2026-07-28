import { Code, Hammer, Users } from "lucide-react";
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
				<div className="mx-auto max-w-3xl">
					<div className="flex flex-col justify-center space-y-5">
						<Badge className="w-fit" variant="outline">
							Our approach
						</Badge>
						<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl">
							Support for mentors, not a replacement
						</h2>
						<p className="text-lg text-muted-foreground">
							Mentors, teachers, and maintainers cannot comment on every contribution. Hephaestus
							helps with routine practice feedback and leaves judgement, context, and relationships
							to people.
						</p>

						<ul className="grid gap-4 mt-4">
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Hammer className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Based on the work</p>
									<p className="text-sm text-muted-foreground">
										Each comment points to evidence in a pull request, merge request, or issue
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Users className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Your decision</p>
									<p className="text-sm text-muted-foreground">
										The feedback is advisory, and developers can disagree with it
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Code className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Configured by your workspace</p>
									<p className="text-sm text-muted-foreground">
										Admins choose the repositories, practices, AI model, and optional features
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
