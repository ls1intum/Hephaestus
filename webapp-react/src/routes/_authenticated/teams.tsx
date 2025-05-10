import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/teams")({
  component: Teams,
});

function Teams() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">Teams</h1>
      <p className="mb-4">
        Manage your teams and team members here.
      </p>
      {/* Teams content would go here */}
    </div>
  );
}