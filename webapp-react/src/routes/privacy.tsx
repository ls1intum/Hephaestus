import { LegalPage } from "@/components/info/LegalPage";
import environment from "@/environment";
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/privacy")({
	component: PrivacyContainer,
});

export function PrivacyContainer() {
	return (
		<LegalPage title="Privacy Policy" content={environment.legal.privacyHtml} />
	);
}
