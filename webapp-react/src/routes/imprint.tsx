import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/info/LegalPage";
import environment from "@/environment";

export const Route = createFileRoute("/imprint")({
  component: ImprintContainer,
});

function ImprintContainer() {
  return <LegalPage 
    title="Imprint"
    content={environment.legal.imprintHtml} 
  />;
}
