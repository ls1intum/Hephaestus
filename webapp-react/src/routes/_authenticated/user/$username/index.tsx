import { createFileRoute } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { ProfilePage } from "@/components/profile/ProfilePage";

export const Route = createFileRoute("/_authenticated/user/$username/")({
  component: UserProfile,
});

function UserProfile() {
  const { username } = Route.useParams();
  
  // Query for user profile data
  const profileQuery = useQuery({
    ...getUserProfileOptions({
      path: { login: username }
    }),
    enabled: Boolean(username),
  });
  
  return (
    <ProfilePage 
      profileData={profileQuery.data}
      isLoading={profileQuery.isLoading || profileQuery.isFetching}
      error={profileQuery.isError}
      username={username}
    />
  );
}