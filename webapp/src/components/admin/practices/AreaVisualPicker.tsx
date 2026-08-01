import { Check, Search } from "lucide-react";
import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";
import {
	areaSeed,
	COLOR_KEYS,
	getAreaVisual,
	ICON_COMPONENTS,
	ICON_NAMES,
	iconLabel,
	iconSearchText,
	PILL,
} from "./area-visuals";

export interface AreaVisualPickerProps {
	/** Set when a form label points at the picker; lands on the trigger, which is the control. */
	id?: string;
	/** Set alongside `id` so the field's help text reaches the control. */
	describedBy?: string;
	slug: string;
	name: string;
	icon?: string | null;
	color?: string | null;
	onChange: (patch: { icon?: string; color?: string }) => void;
	disabled?: boolean;
}

export function AreaVisualPicker({
	id,
	describedBy,
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
	const { Icon: EffectiveIcon, pill } = getAreaVisual(slug, name, icon, color);
	const [query, setQuery] = useState("");
	const colorLabelId = useId();
	const iconLabelId = useId();

	const q = query.trim().toLowerCase();
	const filteredIcons = q ? ICON_NAMES.filter((n) => iconSearchText(n).includes(q)) : ICON_NAMES;

	return (
		<Popover>
			<PopoverTrigger
				render={
					<Button
						id={id}
						variant="ghost"
						size="icon-sm"
						disabled={disabled}
						aria-describedby={describedBy}
						// With a form label pointing at it the visible label is the name; adding our own
						// would make the spoken name disagree with the one people can see and say.
						aria-label={id ? undefined : `Edit the icon and color for ${name}`}
					>
						<span className={cn("flex size-6 items-center justify-center rounded-md", pill)}>
							<EffectiveIcon className="size-4" aria-hidden="true" />
						</span>
					</Button>
				}
			/>
			<PopoverContent className="w-72 space-y-3" aria-label="Icon and color">
				<div className="space-y-1.5">
					<p id={colorLabelId} className="text-xs text-muted-foreground">
						Color
					</p>
					<ToggleGroup
						value={[activeColor]}
						onValueChange={(value) => value[0] && onChange({ color: value[0] })}
						spacing={1}
						role="toolbar"
						aria-labelledby={colorLabelId}
						className="grid w-full grid-cols-7 gap-1.5"
					>
						{COLOR_KEYS.map((key) => (
							<ToggleGroupItem
								key={key}
								value={key}
								aria-label={key}
								className={cn(
									"size-7 min-w-0 rounded-full border border-black/10 p-0 transition-transform hover:scale-110 dark:border-white/15",
									PILL[key],
									activeColor === key && "ring-2 ring-ring ring-offset-1",
								)}
							>
								{activeColor === key && <Check className="size-3.5" aria-hidden="true" />}
							</ToggleGroupItem>
						))}
					</ToggleGroup>
				</div>

				<div className="space-y-1.5">
					<p id={iconLabelId} className="text-xs text-muted-foreground">
						Icon
					</p>
					<div className="relative">
						<Search className="pointer-events-none absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
						<Input
							value={query}
							onChange={(e) => setQuery(e.target.value)}
							placeholder="Search icons…"
							aria-label="Search icons"
							autoComplete="off"
							className="h-8 pl-7 text-sm"
						/>
					</div>
					{filteredIcons.length === 0 ? (
						<p className="py-6 text-center text-xs text-muted-foreground">
							No icons match “{query}”.
						</p>
					) : (
						<ToggleGroup
							value={[activeIcon]}
							onValueChange={(value) => value[0] && onChange({ icon: value[0] })}
							spacing={1}
							role="toolbar"
							aria-labelledby={iconLabelId}
							className="grid max-h-44 w-full grid-cols-7 gap-1 overflow-y-auto pr-1"
						>
							{filteredIcons.map((iconName) => {
								const Icon = ICON_COMPONENTS[iconName];
								return (
									<ToggleGroupItem
										key={iconName}
										value={iconName}
										aria-label={iconLabel(iconName)}
										className="size-8 min-w-0 p-0 text-muted-foreground aria-pressed:bg-primary aria-pressed:text-primary-foreground aria-pressed:hover:bg-primary aria-pressed:hover:text-primary-foreground"
									>
										<Icon className="size-4" aria-hidden="true" />
									</ToggleGroupItem>
								);
							})}
						</ToggleGroup>
					)}
				</div>
			</PopoverContent>
		</Popover>
	);
}
