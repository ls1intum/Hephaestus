import { LandingSignInCTA } from "@/components/auth/LandingSignInCTA";
import { buttonVariants } from "@/components/ui/button";
import { useTheme } from "@/integrations/theme";
import { cn } from "@/lib/utils";

interface LandingCTASectionProps {
	onSignIn: (idpHint: string) => void;
	onGoToDashboard?: () => void;
	isSignedIn: boolean;
}

export function LandingCTASection({
	onSignIn,
	onGoToDashboard,
	isSignedIn,
}: LandingCTASectionProps) {
	const { theme } = useTheme();
	return (
		<section
			className={cn("w-full py-8 md:py-16 bg-background", {
				dark: theme !== "dark",
			})}
		>
			<div className="mx-auto w-full max-w-7xl px-4 md:px-6">
				<div className="flex flex-col items-center space-y-6 text-center max-w-3xl mx-auto">
					<h2 className="text-3xl md:text-4xl font-bold text-primary">Ready to get started?</h2>
					<p className="text-lg text-muted-foreground">
						{isSignedIn
							? "Open your workspace and continue where you left off."
							: "Sign in to open your workspace and see the features your admin has enabled."}
					</p>
					<div className="flex w-full flex-col gap-4 sm:w-auto sm:flex-row">
						<LandingSignInCTA
							isSignedIn={isSignedIn}
							onSignIn={onSignIn}
							onGoToDashboard={onGoToDashboard}
							size="lg"
							className="w-full sm:w-auto"
						/>
						<a
							href="https://ls1intum.github.io/Hephaestus/"
							target="_blank"
							rel="noopener noreferrer"
							className={buttonVariants({
								size: "lg",
								variant: "outline",
								className: "text-primary",
							})}
						>
							<span>Read the user guide</span>
						</a>
					</div>
					{!isSignedIn && (
						<p className="text-sm text-muted-foreground pt-2">Open source under the MIT License.</p>
					)}
				</div>
			</div>
		</section>
	);
}
