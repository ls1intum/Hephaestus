import { cn } from "@/lib/utils";
import { type AreaVisual, getAreaVisual } from "./areaVisuals";

interface AreaIconProps {
	slug: string;
	name?: string;
	/** Admin-set lucide icon name; falls back to the seeded/keyword default when unset. */
	icon?: string | null;
	/** Admin-set palette colour key; falls back to the seeded/keyword default when unset. */
	color?: string | null;
	className?: string;
}

/** Just the area's coloured icon, for placing beside an existing heading. Decorative — the adjacent
 * text carries the meaning, so the icon is aria-hidden. */
export function AreaIcon({ slug, name, icon, color, className }: AreaIconProps) {
	const { Icon }: AreaVisual = getAreaVisual(slug, name ?? "", icon, color);
	return <Icon className={cn("h-4 w-4 shrink-0", className)} aria-hidden="true" />;
}

interface AreaBadgeProps {
	slug: string;
	name: string;
	icon?: string | null;
	color?: string | null;
	/** Show the small "Blocking" marker on the three correctness/security areas (redundant to colour). */
	showBlocking?: boolean;
	className?: string;
}

/**
 * A chip identifying a practice area: coloured pill + icon + name. The name is the accessible label
 * and the icon is decorative (aria-hidden), so identity never depends on colour alone.
 */
export function AreaBadge({
	slug,
	name,
	icon,
	color,
	showBlocking = false,
	className,
}: AreaBadgeProps) {
	const { Icon, pill, blocking } = getAreaVisual(slug, name, icon, color);
	return (
		<span
			className={cn(
				"inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium",
				pill,
				className,
			)}
		>
			<Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
			<span className="truncate">{name}</span>
			{showBlocking && blocking && (
				<span className="ml-0.5 rounded-sm bg-black/10 px-1 text-[10px] font-semibold uppercase tracking-wide dark:bg-white/15">
					Blocking
				</span>
			)}
		</span>
	);
}
