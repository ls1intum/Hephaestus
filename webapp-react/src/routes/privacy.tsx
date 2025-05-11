import { createFileRoute } from "@tanstack/react-router";
import environment from "@/environment";
import { LegalPage } from "@/features/info/legal/LegalPage";

export const Route = createFileRoute("/privacy")({
  component: PrivacyContainer,
});


export function PrivacyContainer() {
  return <LegalPage 
    title="Privacy Policy"
    content={environment.legal.privacyHtml} 
  />;
}