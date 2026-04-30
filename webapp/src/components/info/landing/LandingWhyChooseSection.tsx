import { ArrowRight } from "lucide-react";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
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
		<section className="w-full py-20 md:py-32 bg-gradient-to-b from-background to-muted/30">
			<div className="container max-w-3xl px-4 md:px-6 space-y-8">
				<h2 className="text-4xl font-semibold tracking-tight sm:text-5xl">
					Coaching that meets the contributor where they are.
				</h2>
				<div className="text-lg text-muted-foreground leading-relaxed space-y-5">
					<p>
						Most tools point at numbers. Hephaestus points at the work — at this pull request, at
						this thread, at this commit message — and says what would make it better.
					</p>
					<p>
						The advice changes with the person. Someone new to a practice gets an example. Someone
						who's seen the note before gets straight to the fix. Someone who's been growing gets a
						question to think on.
					</p>
				</div>

				<div className="pt-2">
					{isSignedIn ? (
						<Button onClick={onGoToDashboard} className="gap-2">
							Go to Dashboard <ArrowRight className="h-4 w-4" />
						</Button>
					) : (
						<GitHubSignInButton onClick={onSignIn} className="w-full justify-center sm:w-auto" />
					)}
				</div>
			</div>
		</section>
	);
}
