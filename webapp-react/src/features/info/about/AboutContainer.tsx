import { useQuery } from "@tanstack/react-query";
import { AboutPage } from "./AboutPage";

// Interface for our contributors data
interface Contributor {
  id: number;
  login: string;
  avatarUrl: string;
  htmlUrl: string;
}

export function AboutContainer() {
  // Query to fetch contributors (similar to what's done in the workspace component)
  const { data: contributors, isPending, isError } = useQuery({
    queryKey: ['contributors'],
    queryFn: async () => {
      const response = await fetch('/api/v1/meta/contributors');
      if (!response.ok) {
        throw new Error('Failed to fetch contributors');
      }
      return response.json() as Promise<Contributor[]>;
    },
    gcTime: Infinity,
  });

  // Find the project manager (Felix T.J. Dietrich)
  const projectManager = contributors?.find(contributor => contributor.id === 5898705);
  
  // Filter out the project manager and bots
  const otherContributors = contributors?.filter(
    contributor => contributor.id !== 5898705 && !contributor.login.includes('[bot]')
  );

  return (
    <AboutPage 
      isPending={isPending}
      isError={isError}
      projectManager={projectManager}
      otherContributors={otherContributors || []}
    />
  );
}