import { Link } from "@tanstack/react-router";

export default function Footer() {
  return (
    <footer className="py-6 md:px-8 md:py-0 border-t">
      <div className="container flex flex-col items-center justify-between gap-2 md:gap-4 md:h-24 md:flex-row">
        <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
          <Link to="/about" className="font-medium underline underline-offset-4">About</Link>
        </p>
        <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
          <a href="https://github.com/ls1intum/Hephaestus/releases" target="_blank" rel="noopener noreferrer" className="font-medium underline underline-offset-4">Releases</a>
        </p>
        <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
          <a data-canny-link href="https://hephaestus.canny.io/feature-requests" className="font-medium underline underline-offset-4">Feature requests</a>
        </p>
        <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
          <Link to="/privacy" className="font-medium underline underline-offset-4">Privacy</Link>
        </p>
        <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
          <Link to="/imprint" className="font-medium underline underline-offset-4">Imprint</Link>
        </p>
        <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-right flex-1">
          Built by
          <a href="https://github.com/ls1intum" target="_blank" rel="noopener noreferrer" className="font-medium underline underline-offset-4"> AET Team</a>
          {" "}at
          <a href="https://www.tum.de/en/" target="_blank" rel="noopener noreferrer" className="font-medium underline underline-offset-4"> TUM</a>. The source code is available on
          <a href="https://github.com/ls1intum/hephaestus" target="_blank" rel="noopener noreferrer" className="font-medium underline underline-offset-4"> GitHub</a>.
        </p>
      </div>
    </footer>
  );
}