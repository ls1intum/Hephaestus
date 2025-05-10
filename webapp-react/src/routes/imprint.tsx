import { createFileRoute } from "@tanstack/react-router";
import environment from "@/environment";

export const Route = createFileRoute("/imprint")({
  component: Imprint,
});

function Imprint() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">Imprint</h1>
      <div 
        className="prose dark:prose-invert max-w-none" 
        dangerouslySetInnerHTML={{ __html: environment.legal.imprintHtml }}
      />
    </div>
  );
}