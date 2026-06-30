import { Check, Search } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
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

/** Split a PascalCase lucide name into lowercase words so "git" matches "GitBranch", etc. */
function searchable(iconName: string): string {
	return iconName.replace(/([a-z])([A-Z])/g, "$1 $2").toLowerCase();
}

/**
 * Compact icon + colour editor for a practice area, opened from the area's current icon. Highlights the
 * effective value (admin override, else the seeded default), persists each pick as a partial update, and
 * offers the full accessible colour spectrum plus a searchable icon library.
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
	const [query, setQuery] = useState("");

	const q = query.trim().toLowerCase();
	const filteredIcons = q ? ICON_NAMES.filter((n) => searchable(n).includes(q)) : ICON_NAMES;

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
			<PopoverContent className="w-72 space-y-3">
				<div className="space-y-1.5">
					<Label className="text-xs text-muted-foreground">Colour</Label>
					<div className="grid grid-cols-7 gap-1.5">
						{COLOR_KEYS.map((key) => (
							<button
								key={key}
								type="button"
								aria-label={`Colour ${key}`}
								aria-pressed={activeColor === key}
								onClick={() => onChange({ color: key })}
								className={cn(
									"flex size-7 items-center justify-center rounded-full border border-black/10 transition-transform hover:scale-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 dark:border-white/15",
									PILL[key],
									activeColor === key && "ring-2 ring-ring ring-offset-1",
								)}
							>
								{activeColor === key && <Check className="size-3.5" aria-hidden="true" />}
							</button>
						))}
					</div>
				</div>

				<div className="space-y-1.5">
					<Label className="text-xs text-muted-foreground">Icon</Label>
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
						<div className="grid max-h-44 grid-cols-7 gap-1 overflow-y-auto pr-1">
							{filteredIcons.map((iconName) => {
								const Icon = ICON_COMPONENTS[iconName];
								const selected = activeIcon === iconName;
								return (
									<button
										key={iconName}
										type="button"
										aria-label={iconName}
										aria-pressed={selected}
										onClick={() => onChange({ icon: iconName })}
										className={cn(
											"flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
											selected &&
												"bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground",
										)}
									>
										<Icon className="size-4" aria-hidden="true" />
									</button>
								);
							})}
						</div>
					)}
				</div>
			</PopoverContent>
		</Popover>
	);
}
