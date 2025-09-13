import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";

interface LandingCTASectionProps {
	onSignIn: () => void;
	isSignedIn: boolean;
}

export function LandingCTASection({
	onSignIn,
	isSignedIn,
}: LandingCTASectionProps) {
	return (
		<section className="w-full py-8 md:py-16 bg-foreground">
			<div className="container px-4 md:px-6">
				<div className="flex flex-col items-center space-y-6 text-center max-w-3xl mx-auto">
					<h2 className="text-3xl md:text-4xl font-bold text-primary-foreground">
						Ready to Get Started?
					</h2>
					<p className="text-lg text-secondary">
						Join our community and build more collaborative and effective
						software development practices.
					</p>
					<div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto invert">
						<Button size="lg" onClick={onSignIn}>
							{isSignedIn ? "Go to Dashboard" : "Get Started Now"}
							<ArrowRight className="h-4 w-4" />
						</Button>
						<Button size="lg" asChild>
							<a
								href="https://ls1intum.github.io/Hephaestus/"
								target="_blank"
								rel="noopener noreferrer"
							>
								<span>Read Documentation</span>
							</a>
						</Button>
					</div>
					{!isSignedIn && (
						<p className="text-sm text-secondary pt-2">
							Open-source and free to use.
						</p>
					)}
				</div>
			</div>
		</section>
	);
}
