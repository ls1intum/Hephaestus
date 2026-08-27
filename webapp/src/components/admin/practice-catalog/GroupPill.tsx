import { cva, type VariantProps } from "class-variance-authority";
import { CircleDashed } from "lucide-react";
import { getGroupVisual } from "@/components/admin/practice-catalog/group-visuals";
import { cn } from "@/lib/utils";

const groupPillVariants = cva("flex shrink-0 items-center justify-center", {
	variants: {
		size: {
			/** Inline beside a practice name, where it is the only thing carrying the group. */
			sm: "size-4 rounded-sm [&_svg]:size-2.5",
			/** A list row's leading element. */
			md: "size-8 rounded-md [&_svg]:size-4",
			/** A detail surface's heading. */
			lg: "size-9 rounded-md [&_svg]:size-4",
		},
	},
	defaultVariants: { size: "md" },
});

export interface GroupPillProps extends VariantProps<typeof groupPillVariants> {
	/** Absent for a practice that belongs to no group, which gets the neutral mark rather than a colour. */
	slug?: string;
	name?: string;
	icon?: string;
	color?: string;
	/**
	 * Announce the group's name from the pill itself. Only for the places where no visible text
	 * repeats it — elsewhere the pill is decorative and naming it twice is noise.
	 */
	srLabel?: boolean;
	className?: string;
}

/**
 * Colour and icon come from the shared registry, so a group looks the same in every tree, row and
 * header — the colour is what an administrator learns to scan by, so no surface re-decides it.
 */
export function GroupPill({
	slug,
	name,
	icon,
	color,
	srLabel = false,
	size,
	className,
}: GroupPillProps) {
	const visual = slug
		? getGroupVisual(slug, name ?? slug, icon, color)
		: { Icon: CircleDashed, pill: "bg-muted text-muted-foreground" };
	const { Icon, pill } = visual;
	const label = name ?? "Unassigned";

	return (
		<span
			className={cn(groupPillVariants({ size }), pill, className)}
			aria-hidden={srLabel ? undefined : true}
			title={srLabel ? label : undefined}
		>
			<Icon aria-hidden />
			{srLabel && <span className="sr-only">{label}</span>}
		</span>
	);
}
