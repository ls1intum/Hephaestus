import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";
import { AreaIcon } from "./AreaBadge";
import { areaSeed, COLOR_KEYS, ICON_COMPONENTS, ICON_NAMES, PILL } from "./areaVisuals";

interface AreaVisualPickerProps {
	slug: string;
	name: string;
	icon?: string | null;
	color?: string | null;
	/** Persist a partial change — only the field the admin touched is sent (PATCH semantics). */
	onChange: (patch: { icon?: string; color?: string }) => void;
	disabled?: boolean;
}

/**
 * Compact icon + colour editor for a practice area, opened from the area's current icon. Highlights the
 * effective value (admin override, else the seeded default) and persists each pick as a partial update.
 */
export function AreaVisualPicker({
	slug,
	name,
	icon,
	color,
	onChange,
	disabled,
}: AreaVisualPickerProps) {
	const seed = areaSeed(slug, name);
	const activeIcon = icon ?? seed.icon;
	const activeColor = color ?? seed.color;

	return (
		<Popover>
			<PopoverTrigger
				render={
					<Button
						variant="ghost"
						size="icon-sm"
						disabled={disabled}
						aria-label={`Edit icon and colour for ${name}`}
					>
						<AreaIcon slug={slug} name={name} icon={icon} color={color} />
					</Button>
				}
			/>
			<PopoverContent className="w-64 space-y-3">
				<div className="space-y-1.5">
					<Label className="text-xs text-muted-foreground">Colour</Label>
					<div className="flex flex-wrap gap-1.5">
						{COLOR_KEYS.map((key) => (
							<button
								key={key}
								type="button"
								aria-label={`Colour ${key}`}
								aria-pressed={activeColor === key}
								onClick={() => onChange({ color: key })}
								className={cn(
									"size-6 rounded-full border border-black/10 transition-transform hover:scale-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 dark:border-white/15",
									PILL[key],
									activeColor === key && "ring-2 ring-ring ring-offset-1",
								)}
							/>
						))}
					</div>
				</div>
				<div className="space-y-1.5">
					<Label className="text-xs text-muted-foreground">Icon</Label>
					<ToggleGroup
						value={[activeIcon]}
						onValueChange={(value) => {
							const next = value.length > 0 ? value[value.length - 1] : activeIcon;
							if (next) onChange({ icon: next });
						}}
						spacing={1}
						size="sm"
						className="flex-wrap"
					>
						{ICON_NAMES.map((iconName) => {
							const Icon = ICON_COMPONENTS[iconName];
							return (
								<ToggleGroupItem
									key={iconName}
									value={iconName}
									aria-label={iconName}
									className="size-7 p-0"
								>
									<Icon className="size-4" aria-hidden="true" />
								</ToggleGroupItem>
							);
						})}
					</ToggleGroup>
				</div>
			</PopoverContent>
		</Popover>
	);
}
