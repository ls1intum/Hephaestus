import { useQuery } from "@tanstack/react-query";
import { AboutPage } from "./AboutPage";
import { getContributorsOptions } from "@/api/@tanstack/react-query.gen";


export function AboutContainer() {
  const { data: contributors = [], isPending, isError } = useQuery({
    ...getContributorsOptions({}),
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
      otherContributors={otherContributors}
    />
  );
}