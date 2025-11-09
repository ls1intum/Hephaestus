import { ArrowRight } from "lucide-react";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
import { Button } from "@/components/ui/button";
import { useTheme } from "@/integrations/theme";
import { cn } from "@/lib/utils";

interface LandingCTASectionProps {
	onSignIn: () => void;
	isSignedIn: boolean;
}

export function LandingCTASection({
	onSignIn,
	isSignedIn,
}: LandingCTASectionProps) {
	const { theme } = useTheme();
	return (
		<section
			className={cn("w-full py-8 md:py-16 bg-background", {
				dark: theme !== "dark",
			})}
		>
			<div className="container px-4 md:px-6">
				<div className="flex flex-col items-center space-y-6 text-center max-w-3xl mx-auto">
					<h2 className="text-3xl md:text-4xl font-bold text-primary">
						Ready to Get Started?
					</h2>
					<p className="text-lg text-muted-foreground">
						Join our community and build more collaborative and effective
						software development practices.
					</p>
					<div className="flex w-full flex-col gap-4 sm:w-auto sm:flex-row">
						{isSignedIn ? (
							<Button size="lg" onClick={onSignIn}>
								Go to Dashboard
								<ArrowRight className="h-4 w-4" />
							</Button>
						) : (
							<GitHubSignInButton
								onClick={onSignIn}
								size="lg"
								className="w-full justify-center sm:w-auto"
							/>
						)}
						<Button
							size="lg"
							variant="outline"
							className="text-primary"
							asChild
						>
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
						<p className="text-sm text-muted-foreground pt-2">
							Open-source and free to use.
						</p>
					)}
				</div>
			</div>
		</section>
	);
}
