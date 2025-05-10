import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/user/$username/activity")({
  component: UserActivity,
});

function UserActivity() {
  const { username } = Route.useParams();
  
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">Activity: {username}</h1>
      <p className="mb-4">
        View user activity and contributions.
      </p>
      {/* User activity content would go here */}
    </div>
  );
}