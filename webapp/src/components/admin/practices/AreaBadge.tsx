import { Badge } from "@/components/ui/badge";
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

/**
 * Just the area's icon, for placing beside an existing heading. Decorative — the adjacent text
 * carries the meaning, so the icon is aria-hidden.
 */
export function AreaIcon({ slug, name, icon, color, className }: AreaIconProps) {
	const { Icon }: AreaVisual = getAreaVisual(slug, name ?? "", icon, color);
	return (
		<Icon className={cn("size-4 shrink-0 text-muted-foreground", className)} aria-hidden="true" />
	);
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
 * Identity chip for a practice area, built on the shadcn {@link Badge}: coloured pill + icon + name.
 * The name is the accessible label and the icon is decorative (aria-hidden), so identity never
 * depends on colour alone. The palette class only recolours the badge; its shape stays consistent
 * with every other badge in the app.
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
		<Badge className={cn("max-w-full border-transparent", pill, className)}>
			<Icon aria-hidden="true" />
			<span className="truncate">{name}</span>
			{showBlocking && blocking && (
				<span className="rounded-sm bg-black/10 px-1 text-[10px] font-semibold uppercase tracking-wide dark:bg-white/15">
					Blocking
				</span>
			)}
		</Badge>
	);
}
