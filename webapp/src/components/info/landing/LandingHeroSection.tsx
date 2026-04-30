import { ArrowRight, ChevronDown } from "lucide-react";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

interface LandingHeroSectionProps {
	onSignIn: () => void;
	onGoToDashboard?: () => void;
	isSignedIn: boolean;
	onLearnMoreClick: () => void;
}

export function LandingHeroSection({
	onSignIn,
	onGoToDashboard,
	isSignedIn,
	onLearnMoreClick,
}: LandingHeroSectionProps) {
	return (
		<section className="w-full bg-gradient-to-b from-background to-muted/30 pt-8 md:pt-16 lg:pt-24 text-foreground">
			<div className="container mx-auto px-4 md:px-6 mb-12">
				<div className="flex flex-col items-center space-y-8 text-center">
					<div className="space-y-6 max-w-3xl">
						<h1 className="text-5xl font-semibold tracking-tight sm:text-6xl md:text-7xl">
							Feedback on the practices behind the code.
						</h1>
						<p className="mx-auto max-w-[640px] text-xl text-muted-foreground">
							Each project keeps a list of the practices that matter. When a pull request comes in,
							a comment appears alongside the existing review — with the evidence behind it and a
							suggested next move.
						</p>
					</div>
					<div className="flex flex-col items-center gap-3 sm:flex-row sm:gap-4">
						{isSignedIn ? (
							<Button onClick={onGoToDashboard} size="lg" className="gap-2">
								Go to Dashboard <ArrowRight className="h-4 w-4" />
							</Button>
						) : (
							<GitHubSignInButton
								onClick={onSignIn}
								size="lg"
								className="w-full justify-center sm:w-auto"
							/>
						)}
						<Button variant="ghost" size="lg" onClick={onLearnMoreClick} className="gap-2">
							Learn more <ChevronDown className="h-4 w-4" />
						</Button>
					</div>
				</div>
			</div>

			<div className="mx-auto max-w-2xl px-4 md:px-6">
				<SampleComment />
			</div>
		</section>
	);
}

function SampleComment() {
	return (
		<Card className="shadow-xl border-muted -mb-6 overflow-hidden text-left">
			<CardContent className="space-y-3 p-6 text-sm leading-relaxed">
				<p className="text-xs uppercase tracking-wide text-muted-foreground">
					Hephaestus on pull request #142
				</p>
				<p className="font-medium">Practice: a clear pull-request description.</p>
				<p className="text-muted-foreground">
					Your description reads "fixes the bug" and two reviewers asked what bug. A short paragraph
					— what failure you saw, and why this fix gets at the cause — would save them a round trip.
					Worth adding before merge.
				</p>
			</CardContent>
		</Card>
	);
}
