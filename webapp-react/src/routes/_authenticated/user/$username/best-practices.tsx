import { createFileRoute } from "@tanstack/react-router";
import { useQuery, useMutation } from "@tanstack/react-query";
import { 
  getActivityByUserOptions
} from "@/api/@tanstack/react-query.gen";
import { detectBadPracticesByUser } from "@/api/sdk.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { PracticesPage } from "@/components/practices";

export const Route = createFileRoute("/_authenticated/user/$username/best-practices")({
  component: BestPracticesContainer,
});

export function BestPracticesContainer() {
  const { username } = Route.useParams();
  const { isCurrentUser } = useAuth();
  
  // Check if current user is the dashboard user
  const currUserIsDashboardUser = isCurrentUser(username);
  
  // Query for activity data
  const activityQuery = useQuery({
    ...getActivityByUserOptions({
      path: { login: username }
    }),
    enabled: Boolean(username),
  });
  
  // Mutation for detecting bad practices
  const detectMutation = useMutation({
    mutationFn: () => detectBadPracticesByUser({
      path: { login: username }
    }),
    onSuccess: () => {
      // Refetch activity data after successful detection
      activityQuery.refetch();
    },
  });
  
  const handleDetectBadPractices = () => {
    detectMutation.mutate();
  };
  
  return (
    <PracticesPage 
      activityData={activityQuery.data}
      isLoading={activityQuery.isLoading || activityQuery.isFetching}
      isDetectingBadPractices={detectMutation.isPending}
      username={username}
      currUserIsDashboardUser={currUserIsDashboardUser}
      onDetectBadPractices={handleDetectBadPractices}
    />
  );
}