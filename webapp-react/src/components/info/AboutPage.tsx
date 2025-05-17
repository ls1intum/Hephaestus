import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { AlertCircle } from "lucide-react";

interface Contributor {
  id: number;
  login: string;
  avatarUrl: string;
  htmlUrl: string;
}

interface AboutPageProps {
  isPending: boolean;
  isError: boolean;
  error?: Error;
  otherContributors: Contributor[];
}

export function AboutPage({ 
  isPending, 
  isError,
  otherContributors,
}: AboutPageProps) {
  // Project manager information is hardcoded since it never changes
  const projectManager = {
    id: 5898705,
    login: "felixtjdietrich",
    avatarUrl: "https://avatars.githubusercontent.com/u/5898705",
    htmlUrl: "https://github.com/felixtjdietrich"
  };

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">About</h1>
      <p className="mb-4">
        Hephaestus is built to help software teams work better together. Our tools — like code review leaderboards and AI-guided reflection sessions — support agile practices and
        continuous improvement.
      </p>
      <h2 className="text-2xl font-semibold">Team</h2>
      
      {/* Project Manager - Always displayed */}
      <div className="flex items-center gap-3 mb-4">
        <a
          href={projectManager.htmlUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="hover:scale-105 transition-all hover:shadow-secondary-foreground/15 hover:shadow-lg rounded-full"
        >
          <Avatar className="h-32 w-32">
            <AvatarImage src={projectManager.avatarUrl} alt={`${projectManager.login}'s avatar`} />
            <AvatarFallback>FD</AvatarFallback>
          </Avatar>
        </a>
        <div>
          <div className="text-2xl font-semibold">Felix T.J. Dietrich</div>
          <div className="text-lg text-muted-foreground">Project Manager</div>
          <a href="https://aet.cit.tum.de/people/dietrich/" target="_blank" rel="noopener noreferrer" className="text-primary underline-offset-4 hover:underline">
            Website
          </a>
        </div>
      </div>
      
      <h3 className="text-lg font-bold">Contributors</h3>
      
      {/* Loading state for contributors only */}
      {isPending && (
        <div className="flex flex-wrap gap-2 mt-2">
          {[...Array(6)].map((_, index) => (
            <Skeleton key={index} className="h-20 w-20 rounded-full" />
          ))}
        </div>
      )}
      
      {/* Error state for contributors */}
      {isError && (
        <div className="flex flex-col items-center py-8 space-y-4 text-center">
          <div className="bg-destructive/10 p-4 rounded-full">
            <AlertCircle className="h-10 w-10 text-destructive" />
          </div>
          <div className="space-y-2">
            <h3 className="text-xl font-medium">Failed to load contributors</h3>
            <p className="text-muted-foreground max-w-md">
              We couldn't retrieve the contributor information at this time. Please try refreshing the page.
            </p>
          </div>
        </div>
      )}
      
      {/* Display contributors when loaded successfully */}
      {!isPending && !isError && (
        <div className="flex flex-wrap gap-2">
          {otherContributors.length > 0 ? (
            otherContributors.map(contributor => (
              <a
                key={contributor.id}
                href={contributor.htmlUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="hover:scale-105 transition-all hover:shadow-secondary-foreground/15 hover:shadow-lg rounded-full"
              >
                <Avatar className="h-20 w-20">
                  <AvatarImage src={contributor.avatarUrl} alt={`${contributor.login}'s avatar`} />
                  <AvatarFallback>{contributor.login.slice(0, 2).toUpperCase()}</AvatarFallback>
                </Avatar>
              </a>
            ))
          ) : (
            <p className="text-muted-foreground">No additional contributors found.</p>
          )}
        </div>
      )}
    </div>
  );
}