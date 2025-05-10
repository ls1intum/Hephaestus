import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

interface Contributor {
  id: number;
  login: string;
  avatarUrl: string;
  htmlUrl: string;
}

interface AboutPageProps {
  isPending: boolean;
  isError: boolean;
  projectManager?: Contributor;
  otherContributors: Contributor[];
}

export function AboutPage({ isPending, isError, projectManager, otherContributors }: AboutPageProps) {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">About</h1>
      <p className="mb-4">
        Hephaestus is built to help software teams work better together. Our tools — like code review leaderboards and AI-guided reflection sessions — support agile practices and
        continuous improvement.
      </p>
      <h2 className="text-2xl font-semibold">Team</h2>
      
      {isPending && <span className="text-muted-foreground">Loading...</span>}
      {isError && <span className="text-destructive">An error has occurred</span>}
      
      {projectManager && (
        <div className="flex items-center gap-3 mb-4">
          <a
            href={projectManager.htmlUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="hover:scale-105 transition-all hover:shadow-secondary-foreground/15 hover:shadow-lg rounded-full"
          >
            <Avatar className="h-32 w-32">
              <AvatarImage src={projectManager.avatarUrl} alt={`${projectManager.login}'s avatar`} />
              <AvatarFallback>{projectManager.login.slice(0, 2).toUpperCase()}</AvatarFallback>
            </Avatar>
          </a>
          <div>
            <div className="text-2xl font-semibold">Felix T.J. Dietrich</div>
            <div className="text-lg text-muted-foreground">Project Manager</div>
            <a href="https://ase.cit.tum.de/people/dietrich/" target="_blank" rel="noopener noreferrer" className="text-primary underline-offset-4 hover:underline">
              Website
            </a>
          </div>
        </div>
      )}
      
      {otherContributors.length > 0 && (
        <>
          <h3 className="text-lg font-bold">Contributors</h3>
          <div className="flex flex-wrap gap-2">
            {otherContributors.map(contributor => (
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
            ))}
          </div>
        </>
      )}
    </div>
  );
}