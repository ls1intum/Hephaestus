import { ListFilterIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

export interface ReviewMoreFiltersProps {
	activeCount: number;
	children: ReactNode;
}

export function ReviewMoreFilters({ activeCount, children }: ReviewMoreFiltersProps) {
	return (
		<Popover>
			<PopoverTrigger
				render={
					<Button variant="outline" size="sm" className="h-8 border-dashed font-normal">
						<ListFilterIcon aria-hidden />
						More filters
						{activeCount > 0 && <Badge variant="secondary">{activeCount}</Badge>}
					</Button>
				}
			/>
			<PopoverContent align="start" className="w-72 space-y-4">
				{children}
			</PopoverContent>
		</Popover>
	);
}
