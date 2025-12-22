import { useRef } from "react";
import { LandingCTASection } from "./LandingCTASection";
import { LandingFAQSection } from "./LandingFAQSection";
import { LandingFeaturesSection } from "./LandingFeaturesSection";
import { LandingHeroSection } from "./LandingHeroSection";
import { LandingTestimonialSection } from "./LandingTestimonialSection";
import { LandingWhyChooseSection } from "./LandingWhyChooseSection";

interface LandingPageProps {
	onSignIn: () => void;
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
			<LandingWhyChooseSection
				onSignIn={onSignIn}
				onGoToDashboard={onGoToDashboard}
				isSignedIn={isSignedIn}
			/>
			<LandingTestimonialSection />
			<LandingFAQSection />
			<LandingCTASection
				onSignIn={onSignIn}
				onGoToDashboard={onGoToDashboard}
				isSignedIn={isSignedIn}
			/>
		</div>
	);
}
