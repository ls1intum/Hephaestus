import { Button } from "@/components/ui/button";
import { Link } from "@tanstack/react-router";

export default function Footer() {
	return (
		<footer className="py-2 md:px-8 bg-sidebar border-sidebar-border border-t">
			<div className="container p-0">
				<div className="flex flex-wrap justify-between gap-0 md:gap-4">
					{/* Links Section */}
					<div className="flex flex-wrap items-center justify-center sm:justify-start w-full md:w-auto">
						<nav className="flex flex-wrap gap-x-6 gap-y-2 justify-center sm:justify-start">
							<Button variant="link" size="none" asChild>
								<Link to="/about">About</Link>
							</Button>

							<Button variant="link" size="none" asChild>
								<a
									href="https://github.com/ls1intum/Hephaestus/releases"
									target="_blank"
									rel="noopener noreferrer"
								>
									Releases
								</a>
							</Button>

							<Button variant="link" size="none" asChild>
								<Link to="/privacy">Privacy</Link>
							</Button>

							<Button variant="link" size="none" asChild>
								<Link to="/imprint">Imprint</Link>
							</Button>
						</nav>
					</div>

					{/* Copyright/Credits Section */}
					<div className="text-sm text-muted-foreground text-center w-full sm:w-auto mt-2 md:mt-0">
						<p className="text-balance">
							Built by{" "}
							<Button variant="link" size="none" asChild>
								<a
									href="https://github.com/ls1intum"
									target="_blank"
									rel="noopener noreferrer"
								>
									AET Team
								</a>
							</Button>{" "}
							at{" "}
							<Button variant="link" size="none" asChild>
								<a
									href="https://www.tum.de/en/"
									target="_blank"
									rel="noopener noreferrer"
								>
									TUM
								</a>
							</Button>
							. The source code is available on{" "}
							<Button variant="link" size="none" asChild>
								<a
									href="https://github.com/ls1intum/hephaestus"
									target="_blank"
									rel="noopener noreferrer"
								>
									GitHub
								</a>
							</Button>
							.
						</p>
					</div>
				</div>
			</div>
		</footer>
	);
}
