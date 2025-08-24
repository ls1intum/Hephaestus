import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/info/LegalPage";
import environment from "@/environment";

export const Route = createFileRoute("/privacy")({
	component: PrivacyContainer,
});

export function PrivacyContainer() {
	return (
		<LegalPage title="Privacy Policy" content={environment.legal.privacyHtml} />
	);
}
