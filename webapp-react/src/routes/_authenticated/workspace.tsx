import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/workspace")({
  component: Workspace,
});

function Workspace() {
  return (
    <div className="container py-6">
      <div className="flex flex-col gap-6">
        <div className="flex items-center gap-2">
          <h1 className="text-3xl font-bold">Workspace</h1>
        </div>
        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <p>Welcome to your authenticated workspace!</p>
          <p className="mt-2 text-muted-foreground">This page is only visible to authenticated users.</p>
        </div>
      </div>
    </div>
  );
}