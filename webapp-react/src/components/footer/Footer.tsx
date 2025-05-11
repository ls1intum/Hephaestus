import { Link } from "@tanstack/react-router";
import { Github } from "lucide-react";
import environment from "@/environment";

export default function Footer() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="container mx-auto py-6 mt-auto">
      <div className="border-t pt-6">
        <div className="flex flex-col md:flex-row justify-between items-center gap-4">
          <div className="flex items-center gap-4">
            <Link to="/" className="text-sm font-medium text-muted-foreground hover:text-foreground">
              Home
            </Link>
            <Link to="/about" className="text-sm font-medium text-muted-foreground hover:text-foreground">
              About
            </Link>
            <a 
              href="https://github.com/hephaestus-repo/hephaestus" 
              target="_blank" 
              rel="noopener noreferrer"
              className="text-sm font-medium text-muted-foreground hover:text-foreground inline-flex items-center gap-1"
            >
              <Github className="h-4 w-4" />
              <span>GitHub</span>
            </a>
          </div>
          
          <div className="flex flex-col md:flex-row items-center gap-2">
            <p className="text-sm text-muted-foreground">
              © {currentYear} Hephaestus Project
            </p>
            <span className="hidden md:inline text-muted-foreground">•</span>
            <p className="text-xs text-muted-foreground">
              Version {environment.version}
            </p>
          </div>
        </div>
      </div>
    </footer>
  );
}