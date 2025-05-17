import { ProfileHeader } from "./components/ProfileHeader";
import { ProfileContent } from "./components/ProfileContent";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { XCircleIcon } from "lucide-react";
import type { UserProfile } from "@/api/types.gen";

interface ProfileProps {
  profileData?: UserProfile;
  isLoading: boolean;
  error: boolean;
  username: string;
}

export function ProfilePage({ profileData, isLoading, error, username }: ProfileProps) {
  if (error) {
    return (
      <div className="flex items-center justify-center gap-2">
        <Alert variant="destructive" className="max-w-xl">
          <XCircleIcon className="h-4 w-4" />
          <AlertTitle>Something went wrong...</AlertTitle>
          <AlertDescription>User couldn't be loaded. Please try again later.</AlertDescription>
        </Alert>
      </div>
    );
  }

  return (
    <div className="pt-4 flex flex-col gap-8">
      <ProfileHeader
        user={profileData?.userInfo}
        firstContribution={profileData?.firstContribution}
        contributedRepositories={profileData?.contributedRepositories}
        leaguePoints={profileData?.userInfo?.leaguePoints}
        isLoading={isLoading}
      />
      <ProfileContent
        reviewActivity={profileData?.reviewActivity}
        openPullRequests={profileData?.openPullRequests}
        isLoading={isLoading}
        username={username}
      />
    </div>
  );
}

// Export individual components for Storybook
export { ProfileHeader } from "./components/ProfileHeader";
export { ProfileContent } from "./components/ProfileContent";
export { ReviewActivityCard } from "./components/ReviewActivityCard";
export { IssueCard } from "./components/IssueCard";
