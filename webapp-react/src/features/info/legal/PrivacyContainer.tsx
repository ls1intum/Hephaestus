import environment from "@/environment";
import { LegalPage } from "./LegalPage";

export function PrivacyContainer() {
  return <LegalPage 
    title="Privacy Policy"
    content={environment.legal.privacyHtml} 
  />;
}