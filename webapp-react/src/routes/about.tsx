import { createFileRoute } from "@tanstack/react-router";
import { AboutPage } from "@/components/info/about/AboutPage";
import { useQuery } from "@tanstack/react-query";
import { getContributorsOptions } from "@/api/@tanstack/react-query.gen";

export const Route = createFileRoute("/about")({
  component: AboutContainer,
});

function AboutContainer() {
  const { data: contributors = [], isPending, isError } = useQuery({
    ...getContributorsOptions({}),
    gcTime: Infinity,
  });
  
  // Filter out the project manager and bots
  const otherContributors = contributors?.filter(
    contributor => contributor.id !== 5898705 && !contributor.login.includes('[bot]')
  ) || [];

  return (
    <AboutPage 
      isPending={isPending}
      isError={isError}
      otherContributors={otherContributors}
    />
  );
}