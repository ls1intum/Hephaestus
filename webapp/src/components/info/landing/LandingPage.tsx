import { LandingCtaSection } from "./LandingCtaSection";
import { LandingFaqSection } from "./LandingFaqSection";
import { LandingFeaturesSection } from "./LandingFeaturesSection";
import { LandingHeroSection } from "./LandingHeroSection";
import { LandingProjectOriginsSection } from "./LandingProjectOriginsSection";

interface LandingPageProps {
	onSignIn: (idpHint: string) => void;
	onGoToDashboard?: () => void;
	isSignedIn?: boolean;
}

export function LandingPage({ onSignIn, onGoToDashboard, isSignedIn = false }: LandingPageProps) {
	return (
		<div className="flex flex-col">
			<LandingHeroSection
				onSignIn={onSignIn}
				onGoToDashboard={onGoToDashboard}
				isSignedIn={isSignedIn}
			/>
			<LandingFeaturesSection />
			<LandingProjectOriginsSection />
			<LandingFaqSection />
			<LandingCtaSection
				onSignIn={onSignIn}
				onGoToDashboard={onGoToDashboard}
				isSignedIn={isSignedIn}
			/>
		</div>
	);
}
