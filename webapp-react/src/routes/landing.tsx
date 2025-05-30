import { LandingPage } from "@/components/info/landing/LandingPage";
import { useAuth } from "@/integrations/auth/AuthContext";
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/landing")({
	component: LandingContainer,
});

export function LandingContainer() {
	const { login, isAuthenticated } = useAuth();
	return (
		<div className="-m-4">
			<LandingPage onSignIn={() => login()} isSignedIn={isAuthenticated} />
		</div>
	);
}
