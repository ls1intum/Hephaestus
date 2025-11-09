import { ArrowRight, Code, Hammer, Users } from "lucide-react";
import agileHephaestus from "@/assets/agile_hephaestus.png";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface LandingWhyChooseSectionProps {
	onSignIn: () => void;
	isSignedIn: boolean;
}

export function LandingWhyChooseSection({
	onSignIn,
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
							Our Approach
						</Badge>
						<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl">
							Why Choose Hephaestus?
						</h2>
						<p className="text-lg text-muted-foreground">
							Named after the Greek god of craftsmen, Hephaestus combines
							creativity with technical expertise to build better team habits
							through process-aware, AI-guided mentoring.
						</p>

						<ul className="grid gap-4 mt-4">
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Hammer className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Empower engineers</p>
									<p className="text-sm text-muted-foreground">
										Tools that accelerate learning through real-world feedback
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Users className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Foster collaboration</p>
									<p className="text-sm text-muted-foreground">
										Build team habits that strengthen your engineering culture
									</p>
								</div>
							</li>
							<li className="flex items-start gap-3">
								<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
									<Code className="h-4 w-4 text-primary" />
								</div>
								<div>
									<p className="font-medium">Improve code quality</p>
									<p className="text-sm text-muted-foreground">
										Motivate better code reviews through friendly competition
									</p>
								</div>
							</li>
						</ul>

						<div className="pt-4">
							{isSignedIn ? (
								<Button onClick={onSignIn} className="gap-2">
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
