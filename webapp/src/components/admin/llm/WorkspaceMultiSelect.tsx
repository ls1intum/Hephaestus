import { ChevronsUpDownIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

export interface WorkspaceMultiSelectOption {
	id: number;
	displayName: string;
	workspaceSlug: string;
}

export interface WorkspaceMultiSelectProps {
	id?: string;
	options: WorkspaceMultiSelectOption[];
	selectedIds: number[];
	onChange: (ids: number[]) => void;
	disabled?: boolean;
	className?: string;
}

/**
 * Checkbox-list picker for "Share with: Selected workspaces" (#1368). A plain popover rather than a
 * combobox — the option count is the instance's workspace count, which stays small enough that a
 * scrollable checklist reads faster than a filterable search box.
 */
export function WorkspaceMultiSelect({
	id,
	options,
	selectedIds,
	onChange,
	disabled = false,
	className,
}: WorkspaceMultiSelectProps) {
	const toggle = (workspaceId: number, checked: boolean) => {
		onChange(
			checked
				? [...selectedIds, workspaceId]
				: selectedIds.filter((existing) => existing !== workspaceId),
		);
	};

	const triggerLabel =
		selectedIds.length === 0
			? "Select workspaces…"
			: selectedIds.length === 1
				? (options.find((o) => o.id === selectedIds[0])?.displayName ?? "1 workspace")
				: `${selectedIds.length} workspaces`;

	return (
		<Popover>
			<PopoverTrigger
				render={
					<Button
						id={id}
						type="button"
						variant="outline"
						disabled={disabled}
						className={cn("w-full justify-between font-normal", className)}
					>
						<span className={cn("truncate", selectedIds.length === 0 && "text-muted-foreground")}>
							{triggerLabel}
						</span>
						<ChevronsUpDownIcon className="size-4 shrink-0 opacity-50" aria-hidden />
					</Button>
				}
			/>
			<PopoverContent align="start" className="max-h-72 overflow-y-auto">
				{options.length === 0 ? (
					<p className="text-muted-foreground py-2 text-center text-sm">No workspaces yet.</p>
				) : (
					<ul className="space-y-1">
						{options.map((option) => {
							const checked = selectedIds.includes(option.id);
							const checkboxId = `${id ?? "workspace-multiselect"}-${option.id}`;
							return (
								<li key={option.id} className="flex items-center gap-2 py-0.5">
									<Checkbox
										id={checkboxId}
										checked={checked}
										disabled={disabled}
										onCheckedChange={(next) => toggle(option.id, next === true)}
									/>
									<label
										htmlFor={checkboxId}
										className="min-w-0 flex-1 cursor-pointer truncate text-sm"
									>
										{option.displayName}
										<span className="text-muted-foreground ml-1.5 text-xs">
											{option.workspaceSlug}
										</span>
									</label>
								</li>
							);
						})}
					</ul>
				)}
			</PopoverContent>
		</Popover>
	);
}
