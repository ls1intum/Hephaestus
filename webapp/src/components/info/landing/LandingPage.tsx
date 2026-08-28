import { useRef } from "react";

import { LandingCtaSection } from "./LandingCtaSection";
import { LandingFaqSection } from "./LandingFaqSection";
import { LandingFeaturesSection } from "./LandingFeaturesSection";
import { LandingHeroSection } from "./LandingHeroSection";
import { LandingWhyChooseSection } from "./LandingWhyChooseSection";

interface LandingPageProps {
	onSignIn: (idpHint: string) => void;
	onGoToDashboard?: () => void;
	isSignedIn?: boolean;
}

export function LandingPage({ onSignIn, onGoToDashboard, isSignedIn = false }: LandingPageProps) {
	const learnMoreRef = useRef<HTMLDivElement>(null);

	const handleLearnMoreClick = () => {
		learnMoreRef.current?.scrollIntoView({
			behavior: "smooth",
			block: "start",
		});
	};

	return (
		<div className="flex flex-col">
			<LandingHeroSection
				onSignIn={onSignIn}
				onGoToDashboard={onGoToDashboard}
				isSignedIn={isSignedIn}
				onLearnMoreClick={handleLearnMoreClick}
			/>
			<div ref={learnMoreRef}>
				<LandingFeaturesSection />
			</div>
			<LandingWhyChooseSection />
			<LandingFaqSection />
			<LandingCtaSection
				onSignIn={onSignIn}
				onGoToDashboard={onGoToDashboard}
				isSignedIn={isSignedIn}
			/>
		</div>
	);
}
