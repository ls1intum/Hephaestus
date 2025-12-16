import {
	ClockIcon,
	GitBranchIcon,
	GitCommitIcon,
} from "@primer/octicons-react";
import { Link } from "@tanstack/react-router";

import {
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

export interface FooterProps {
	className?: string;
	buildInfo?: {
		branch?: string;
		commit?: string;
		deployedAt?: string;
	};
}

const sanitizeBuildInfoValue = (value?: string) =>
	value && !value.startsWith("WEB_ENV_") ? value : undefined;

/**
 * Minimal footer with navigation links, attribution, and build info for previews.
 * Version is NOT shown here - it's displayed in the Header beside the logo.
 */
export default function Footer({ className, buildInfo }: FooterProps) {
	const sanitizedBuildInfo = {
		branch: sanitizeBuildInfoValue(buildInfo?.branch),
		commit: sanitizeBuildInfoValue(buildInfo?.commit),
		deployedAt: sanitizeBuildInfoValue(buildInfo?.deployedAt),
	};
	const formattedDeployedAt = sanitizedBuildInfo.deployedAt
		? sanitizedBuildInfo.deployedAt.replace("T", " ").substring(0, 16)
		: undefined;

	const showBuildInfo =
		sanitizedBuildInfo.branch ||
		sanitizedBuildInfo.commit ||
		formattedDeployedAt;

	return (
		<footer
			className={cn(
				"border-t border-sidebar-border bg-sidebar py-2 md:px-8",
				className,
			)}
		>
			<div className="flex flex-col items-center justify-between gap-2 px-4 md:flex-row md:px-0">
				{/* Attribution */}
				<p className="text-sm text-muted-foreground text-center md:text-left">
					Built by{" "}
					<a
						href="https://github.com/ls1intum"
						target="_blank"
						rel="noopener noreferrer"
						className="font-medium underline underline-offset-4 hover:text-foreground"
					>
						AET Team
					</a>{" "}
					at{" "}
					<a
						href="https://www.tum.de/en/"
						target="_blank"
						rel="noopener noreferrer"
						className="font-medium underline underline-offset-4 hover:text-foreground"
					>
						TUM
					</a>
					. Source on{" "}
					<a
						href="https://github.com/ls1intum/Hephaestus"
						target="_blank"
						rel="noopener noreferrer"
						className="font-medium underline underline-offset-4 hover:text-foreground"
					>
						GitHub
					</a>
					.
				</p>

				{/* Navigation + Build Info */}
				<div className="flex items-center gap-4">
					<nav className="flex gap-4 sm:gap-6">
						<Link
							to="/about"
							className="text-sm text-muted-foreground hover:text-foreground hover:underline underline-offset-4"
						>
							About
						</Link>
						<a
							href="https://github.com/ls1intum/Hephaestus/releases"
							target="_blank"
							rel="noopener noreferrer"
							className="text-sm text-muted-foreground hover:text-foreground hover:underline underline-offset-4"
						>
							Releases
						</a>
						<Link
							to="/privacy"
							className="text-sm text-muted-foreground hover:text-foreground hover:underline underline-offset-4"
						>
							Privacy
						</Link>
						<Link
							to="/imprint"
							className="text-sm text-muted-foreground hover:text-foreground hover:underline underline-offset-4"
						>
							Imprint
						</Link>
					</nav>

					{/* Build info only for preview/dev - minimal style */}
					{showBuildInfo && (
						<div className="hidden sm:flex items-center gap-2 border-l border-sidebar-border pl-4 text-xs text-muted-foreground/60 font-mono">
							{sanitizedBuildInfo.branch && (
								<Tooltip>
									<TooltipTrigger asChild>
										<a
											href={`https://github.com/ls1intum/Hephaestus/tree/${sanitizedBuildInfo.branch}`}
											target="_blank"
											rel="noopener noreferrer"
											className="flex items-center gap-1 hover:text-foreground transition-colors"
										>
											<GitBranchIcon size={12} />
											<span className="max-w-20 truncate">
												{sanitizedBuildInfo.branch}
											</span>
										</a>
									</TooltipTrigger>
									<TooltipContent>{sanitizedBuildInfo.branch}</TooltipContent>
								</Tooltip>
							)}

							{sanitizedBuildInfo.commit && (
								<Tooltip>
									<TooltipTrigger asChild>
										<a
											href={`https://github.com/ls1intum/Hephaestus/commit/${sanitizedBuildInfo.commit}`}
											target="_blank"
											rel="noopener noreferrer"
											className="flex items-center gap-1 hover:text-foreground transition-colors"
										>
											<GitCommitIcon size={12} />
											<span>{sanitizedBuildInfo.commit.substring(0, 7)}</span>
										</a>
									</TooltipTrigger>
									<TooltipContent>{sanitizedBuildInfo.commit}</TooltipContent>
								</Tooltip>
							)}

							{formattedDeployedAt && (
								<Tooltip>
									<TooltipTrigger className="flex items-center gap-1 cursor-help">
										<ClockIcon size={12} />
									</TooltipTrigger>
									<TooltipContent>
										Deployed: {formattedDeployedAt}
									</TooltipContent>
								</Tooltip>
							)}
						</div>
					)}
				</div>
			</div>
		</footer>
	);
}
