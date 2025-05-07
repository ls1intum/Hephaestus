import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/about")({
  component: About,
});

function About() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">About</h1>
      <p className="mb-4">
        Hephaestus is built to help software teams work better together. Our tools — like code review leaderboards and AI-guided reflection sessions — support agile practices and
        continuous improvement.
      </p>
      <h2 className="text-2xl font-semibold">Team</h2>
      <div className="flex items-center gap-3 mb-4">
        <div className="size-32 rounded-full overflow-hidden">
          <img src="/images/felix_dietrich.jpg" alt="Felix T.J. Dietrich" />
        </div>
        <div>
          <div className="text-2xl font-semibold">Felix T.J. Dietrich</div>
          <div className="text-lg text-muted-foreground">Project Manager</div>
          <a href="https://ase.cit.tum.de/people/dietrich/" target="_blank" rel="noopener noreferrer" className="text-primary underline-offset-4 hover:underline">Website</a>
        </div>
      </div>
      <h3 className="text-lg font-bold">Contributors</h3>
      <div className="flex flex-wrap gap-2">
        {/* Placeholder for contributors - would be dynamically loaded in a real implementation */}
      </div>
    </div>
  );
}
