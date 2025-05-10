import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/user/$username/")({
  component: UserProfile,
});

function UserProfile() {
  const { username } = Route.useParams();
  
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">User Profile: {username}</h1>
      <p className="mb-4">
        View user profile information and activity.
      </p>
      {/* User profile content would go here */}
    </div>
  );
}