import { forwardRef, type SVGProps } from "react";

// lucide-react v1 dropped brand glyphs (GitHub, GitLab, ...) for licensing reasons.
// These thin wrappers mimic the Lucide icon API (size/className/currentColor) so
// callsites that previously imported `GithubIcon`/`GitlabIcon` from lucide-react
// can drop-in replace by switching the import path.

export type BrandIcon = React.ForwardRefExoticComponent<
	SVGProps<SVGSVGElement> & { size?: number | string } & React.RefAttributes<SVGSVGElement>
>;

const make = (displayName: string, viewBox: string, path: string): BrandIcon => {
	const Icon = forwardRef<SVGSVGElement, SVGProps<SVGSVGElement> & { size?: number | string }>(
		({ size, width, height, ...props }, ref) => {
			const label = props["aria-label"] ?? displayName;
			return (
				<svg
					ref={ref}
					xmlns="http://www.w3.org/2000/svg"
					viewBox={viewBox}
					width={width ?? size ?? "1em"}
					height={height ?? size ?? "1em"}
					fill="currentColor"
					role="img"
					{...props}
				>
					<title>{label}</title>
					<path d={path} />
				</svg>
			);
		},
	);
	Icon.displayName = displayName;
	return Icon;
};

export const GithubIcon: BrandIcon = make(
	"GithubIcon",
	"0 0 16 16",
	"M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z",
);

export const GitlabIcon: BrandIcon = make(
	"GitlabIcon",
	"0 0 24 24",
	"m23.6 9.593-.033-.087L20.3.197a.847.847 0 0 0-1.626.084l-2.198 6.726H7.522L5.324.281A.847.847 0 0 0 3.698.197L.434 9.506.4 9.593a5.882 5.882 0 0 0 1.95 6.798l.011.008.029.022 4.82 3.61 2.388 1.808 1.453 1.099a1.002 1.002 0 0 0 1.214 0l1.453-1.099 2.388-1.808 4.85-3.629.011-.009A5.881 5.881 0 0 0 23.6 9.593Z",
);

export const SlackIcon: BrandIcon = make(
	"SlackIcon",
	"0 0 24 24",
	"M5.042 15.165a2.528 2.528 0 0 1-2.52 2.523A2.528 2.528 0 0 1 0 15.165a2.527 2.527 0 0 1 2.522-2.52h2.52v2.52zm1.271 0a2.527 2.527 0 0 1 2.522-2.52 2.527 2.527 0 0 1 2.52 2.52v6.313A2.528 2.528 0 0 1 8.835 24a2.528 2.528 0 0 1-2.522-2.522v-6.313zM8.835 5.042a2.528 2.528 0 0 1-2.522-2.52A2.528 2.528 0 0 1 8.835 0a2.528 2.528 0 0 1 2.52 2.522v2.52h-2.52zm0 1.271a2.528 2.528 0 0 1 2.52 2.522 2.528 2.528 0 0 1-2.52 2.52H2.522A2.528 2.528 0 0 1 0 8.835a2.528 2.528 0 0 1 2.522-2.522h6.313zm10.123 2.522a2.528 2.528 0 0 1 2.52-2.522A2.528 2.528 0 0 1 24 8.835a2.528 2.528 0 0 1-2.522 2.52h-2.52v-2.52zm-1.271 0a2.528 2.528 0 0 1-2.522 2.52 2.528 2.528 0 0 1-2.52-2.52V2.522A2.528 2.528 0 0 1 15.165 0a2.528 2.528 0 0 1 2.522 2.522v6.313zm-2.522 10.123a2.528 2.528 0 0 1 2.522 2.52A2.528 2.528 0 0 1 15.165 24a2.528 2.528 0 0 1-2.52-2.522v-2.52h2.52zm0-1.271a2.528 2.528 0 0 1-2.52-2.522 2.528 2.528 0 0 1 2.52-2.52h6.313A2.528 2.528 0 0 1 24 15.165a2.528 2.528 0 0 1-2.522 2.522h-6.313z",
);

// `Github` is the old (pre-v1) lucide alias. Re-export so legacy imports keep working.
export { GithubIcon as Github };
