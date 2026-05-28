import { Settings2Icon } from "lucide-react";
import type { RepositoryInfo } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import {
	Popover,
	PopoverContent,
	PopoverDescription,
	PopoverHeader,
	PopoverTitle,
	PopoverTrigger,
} from "@/components/ui/popover";
import type { ActivityMonitorFilters } from "@/lib/activity-monitor";

interface ActivityMonitorConfigurationProps {
	repositories: RepositoryInfo[];
	filters: ActivityMonitorFilters;
	onFiltersChange: (filters: ActivityMonitorFilters) => void;
}

export function ActivityMonitorConfiguration({
	repositories,
	filters,
	onFiltersChange,
}: ActivityMonitorConfigurationProps) {
	const toggleRepository = (repositoryId: number, checked: boolean) => {
		const next = checked
			? [...new Set([...filters.repositoryIds, repositoryId])]
			: filters.repositoryIds.filter((id) => id !== repositoryId);
		onFiltersChange({ ...filters, repositoryIds: next });
	};

	return (
		<Popover>
			<PopoverTrigger
				render={
					<Button type="button" variant="outline" className="w-65">
						Configure activity monitor
						<Settings2Icon data-icon="inline-end" />
					</Button>
				}
			/>
			<PopoverContent align="end" className="w-80">
				<PopoverHeader>
					<PopoverTitle>Activity monitor</PopoverTitle>
					<PopoverDescription>Filter activity by repository.</PopoverDescription>
				</PopoverHeader>
				<div className="grid gap-2">
					<p className="text-sm font-medium">Repositories</p>
					{repositories.length > 0 ? (
						repositories.map((repository) => {
							const id = `activity-monitor-repository-${repository.id}`;
							return (
								<Label
									key={repository.id}
									htmlFor={id}
									className="grid min-h-8 grid-cols-[1rem_1fr] items-center gap-2 text-sm font-normal"
								>
									<Checkbox
										id={id}
										checked={filters.repositoryIds.includes(repository.id)}
										onCheckedChange={(checked) => toggleRepository(repository.id, checked === true)}
									/>
									<span className="truncate">{repository.nameWithOwner}</span>
								</Label>
							);
						})
					) : (
						<p className="text-sm text-muted-foreground">No repositories for this timeframe.</p>
					)}
				</div>
			</PopoverContent>
		</Popover>
	);
}
