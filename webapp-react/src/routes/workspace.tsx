import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/workspace")({
  component: Workspace,
});

function Workspace() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2">
        <h1 className="text-3xl font-bold">Workspace</h1>
        {/* Placeholder for future content */}
      </div>
    </div>
  );
}