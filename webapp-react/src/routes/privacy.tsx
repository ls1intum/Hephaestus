import { createFileRoute } from "@tanstack/react-router";
import environment from "@/lib/environment";

export const Route = createFileRoute("/privacy")({
  component: Privacy,
});

function Privacy() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">Privacy Policy</h1>
      <div 
        className="prose dark:prose-invert max-w-none" 
        dangerouslySetInnerHTML={{ __html: environment.legal.privacyHtml }}
      />
    </div>
  );
}
