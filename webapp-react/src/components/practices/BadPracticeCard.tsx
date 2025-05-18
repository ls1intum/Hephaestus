import type { PullRequestBadPractice } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuGroup,
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
	Tooltip,
	TooltipContent,
	TooltipProvider,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { useState } from "react";
import { stateConfig } from "./utils";

interface BadPracticeCardProps {
	id: number;
	title: string;
	description: string;
	state: PullRequestBadPractice["state"];
	currUserIsDashboardUser?: boolean;
	onResolveBadPracticeAsFixed?: (id: number) => void;
}

export function BadPracticeCard({
	id,
	title,
	description,
	state,
	currUserIsDashboardUser = false,
	onResolveBadPracticeAsFixed,
}: BadPracticeCardProps) {
	const [dialogOpen, setDialogOpen] = useState(false);
	const stateInfo = stateConfig[state];
	const Icon = stateInfo.icon;

	const handleResolveAsFixed = () => {
		onResolveBadPracticeAsFixed?.(id);
	};

	const handleProvideFeedback = () => {
		setDialogOpen(false);
		// In the future, add implementation for feedback
	};

	return (
		<div className="flex flex-row justify-between items-center gap-2">
			<div className="flex flex-row justify-start items-center gap-4">
				<div>
					<TooltipProvider>
						<Tooltip>
							<TooltipTrigger>
								<Icon className={`size-5 ${stateInfo.color}`} />
							</TooltipTrigger>
							<TooltipContent>
								<span>{stateInfo.text}</span>
							</TooltipContent>
						</Tooltip>
					</TooltipProvider>
				</div>
				<div className="flex flex-col">
					<h3 className="text-md font-semibold">{title}</h3>
					<p className="text-sm text-pretty">{description}</p>
				</div>
			</div>
			{currUserIsDashboardUser && (
				<div className="justify-self-end">
					<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
						<DropdownMenu>
							<DropdownMenuTrigger asChild>
								<Button variant="outline">Resolve</Button>
							</DropdownMenuTrigger>
							<DropdownMenuContent className="w-56">
								<DropdownMenuGroup>
									<DropdownMenuItem onClick={handleResolveAsFixed}>
										I've fixed this ✓
									</DropdownMenuItem>
								</DropdownMenuGroup>
								<DropdownMenuGroup>
									<DropdownMenuItem>Won't fix right now</DropdownMenuItem>
								</DropdownMenuGroup>
								<DropdownMenuGroup>
									<DropdownMenuItem onClick={() => setDialogOpen(true)}>
										This isn't accurate
									</DropdownMenuItem>
								</DropdownMenuGroup>
								<DropdownMenuSeparator />
								<DropdownMenuGroup>
									<DropdownMenuItem onClick={() => setDialogOpen(true)}>
										Share your thoughts
									</DropdownMenuItem>
								</DropdownMenuGroup>
							</DropdownMenuContent>
						</DropdownMenu>

						<DialogContent>
							<DialogHeader>
								<DialogTitle>Help us get better</DialogTitle>
								<DialogDescription>
									Your feedback helps us improve our analysis and provide more
									accurate insights for everyone.
								</DialogDescription>
							</DialogHeader>
							<div className="py-4 grid gap-4">
								<div className="items-center grid grid-cols-4 gap-4">
									{/* Feedback form controls would go here */}
								</div>
								<div className="items-start grid grid-cols-4 gap-4 h-40">
									{/* Additional form controls would go here */}
								</div>
							</div>
							<DialogFooter>
								<Button type="submit" onClick={handleProvideFeedback}>
									Send feedback
								</Button>
							</DialogFooter>
							<div className="py-4 grid gap-4">
								<div className="items-center grid grid-cols-4 gap-4">
									{/* Future form elements would go here */}
								</div>
								<div className="items-start grid grid-cols-4 gap-4 h-40">
									{/* Future text area would go here */}
								</div>
							</div>
							<DialogFooter>
								<Button type="submit" onClick={handleProvideFeedback}>
									Send feedback
								</Button>
							</DialogFooter>
						</DialogContent>
					</Dialog>
				</div>
			)}
		</div>
	);
}
