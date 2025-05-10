import environment from "@/environment";
import { LegalPage } from "./LegalPage";

export function ImprintContainer() {
  return <LegalPage 
    title="Imprint"
    content={environment.legal.imprintHtml} 
  />;
}