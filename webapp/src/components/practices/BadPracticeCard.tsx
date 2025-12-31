import { useState } from "react";
import type { PullRequestBadPractice } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	DialogTrigger,
} from "@/components/ui/dialog";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuGroup,
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { canBeResolved, stateConfig } from "./utils";

interface BadPracticeCardProps {
	id: number;
	title: string;
	description: string;
	state: PullRequestBadPractice["state"];
	currUserIsDashboardUser?: boolean;
	onResolveBadPracticeAsFixed?: (id: number) => void;
	onResolveBadPracticeAsWontFix?: (id: number) => void;
	onResolveBadPracticeAsWrong?: (id: number) => void;
	onProvideFeedback?: (id: number, feedback: { type: string; explanation: string }) => void;
}

export function BadPracticeCard({
	id,
	title,
	description,
	state,
	currUserIsDashboardUser = false,
	onResolveBadPracticeAsFixed,
	onResolveBadPracticeAsWontFix,
	onResolveBadPracticeAsWrong,
	onProvideFeedback,
}: BadPracticeCardProps) {
	const [dialogOpen, setDialogOpen] = useState(false);
	const stateInfo = stateConfig[state];
	const Icon = stateInfo.icon;
	const [feedbackType, setFeedbackType] = useState<string | undefined>(undefined);
	const [feedbackExplanation, setFeedbackExplanation] = useState<string>("");
	const feedbackTypes = ["Not a bad practice", "Irrelevant", "Incorrect", "Imprecise", "Other"];

	const handleResolveAsFixed = () => {
		onResolveBadPracticeAsFixed?.(id);
	};

	const handleResolveAsWontFix = () => {
		onResolveBadPracticeAsWontFix?.(id);
	};

	const handleResolveAsWrong = () => {
		onResolveBadPracticeAsWrong?.(id);
	};

	const handleProvideFeedback = () => {
		if (onProvideFeedback && feedbackType) {
			onProvideFeedback(id, {
				type: feedbackType || "Other",
				explanation: feedbackExplanation,
			});
		}
		setDialogOpen(false);
		setFeedbackType(undefined);
		setFeedbackExplanation("");
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
			{currUserIsDashboardUser && canBeResolved(state) && (
				<div className="justify-self-end">
					<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
						<DropdownMenu>
							<DropdownMenuTrigger asChild>
								<Button variant="outline">Resolve</Button>
							</DropdownMenuTrigger>
							<DropdownMenuContent className="w-56">
								<DropdownMenuGroup>
									<DropdownMenuItem onClick={handleResolveAsFixed}>
										Resolve as fixed
									</DropdownMenuItem>
								</DropdownMenuGroup>
								<DropdownMenuGroup>
									<DropdownMenuItem onClick={handleResolveAsWontFix}>
										Resolve as won't fix
									</DropdownMenuItem>
								</DropdownMenuGroup>
								<DropdownMenuGroup>
									<DialogTrigger className="w-full">
										<DropdownMenuItem
											onClick={() => {
												handleResolveAsWrong();
											}}
										>
											Resolve as wrong
										</DropdownMenuItem>
									</DialogTrigger>
								</DropdownMenuGroup>
								<DropdownMenuSeparator />
								<DropdownMenuGroup>
									<DialogTrigger className="w-full">
										<DropdownMenuItem>Provide feedback</DropdownMenuItem>
									</DialogTrigger>
								</DropdownMenuGroup>
							</DropdownMenuContent>
						</DropdownMenu>

						<DialogContent>
							<DialogHeader>
								<DialogTitle>Provide feedback</DialogTitle>
								<DialogDescription>
									Mark this bad practice with feedback that helps us improve the bad practice
									detection.
								</DialogDescription>
							</DialogHeader>
							<div className="py-4 grid gap-4">
								<div className="items-center grid grid-cols-4 gap-4">
									<Label htmlFor="feedback-type" className="text-right">
										Feedback
									</Label>
									<div className="col-span-3">
										<Select onValueChange={(value) => setFeedbackType(value)} value={feedbackType}>
											<SelectTrigger id="feedback-type" className="w-full">
												<SelectValue placeholder="Select the type of feedback" />
											</SelectTrigger>
											<SelectContent>
												{feedbackTypes.map((type) => (
													<SelectItem key={type} value={type}>
														{type}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
									</div>
								</div>
								<div className="items-start grid grid-cols-4 gap-4 h-40">
									<Label htmlFor="explanation" className="text-right">
										Explanation
									</Label>
									<Textarea
										id="explanation"
										className="col-span-3 h-full"
										value={feedbackExplanation}
										onChange={(e) => setFeedbackExplanation(e.target.value)}
									/>
								</div>
							</div>
							<DialogFooter>
								<Button type="submit" onClick={handleProvideFeedback}>
									Submit feedback
								</Button>
							</DialogFooter>
						</DialogContent>
					</Dialog>
				</div>
			)}
		</div>
	);
}
