import { Link } from "@tanstack/react-router";
import { Code2, FileText, Pencil, Trash2 } from "lucide-react";
import type { Practice } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { TRIGGER_EVENT_SHORT_LABELS } from "./constants";

interface PracticeCardProps {
	workspaceSlug: string;
	practice: Practice;
	isToggling: boolean;
	onDelete: (practice: Practice) => void;
	onSetActive: (slug: string, active: boolean) => void;
}

export function PracticeCard({
	workspaceSlug,
	practice,
	isToggling,
	onDelete,
	onSetActive,
}: PracticeCardProps) {
	const getShortLabel = (event: string) =>
		event in TRIGGER_EVENT_SHORT_LABELS
			? TRIGGER_EVENT_SHORT_LABELS[event as keyof typeof TRIGGER_EVENT_SHORT_LABELS]
			: event;

	return (
		<Card className={practice.active ? "" : "opacity-60"}>
			<CardHeader>
				<div className="flex items-start justify-between gap-3">
					<div className="flex-1 min-w-0">
						<div className="flex items-center gap-2 mb-0.5">
							<Badge variant="outline" className="text-xs">
								{practice.artifactType === "ISSUE" ? "Issue" : "Pull request"}
							</Badge>
						</div>
						<div className="flex items-center gap-2">
							<CardTitle className="text-lg">{practice.name}</CardTitle>
							{practice.precomputeScript && (
								<Tooltip>
									<TooltipTrigger
										render={
											<Code2
												className="h-4 w-4 text-muted-foreground shrink-0"
												aria-label="Has precompute script"
											/>
										}
									/>
									<TooltipContent side="top">Has precompute script</TooltipContent>
								</Tooltip>
							)}
						</div>
						<p className="text-xs text-muted-foreground mt-0.5">{practice.slug}</p>
					</div>
					<div className="flex items-center gap-2 shrink-0">
						<Switch
							checked={practice.active}
							onCheckedChange={(checked) => onSetActive(practice.slug, checked)}
							disabled={isToggling}
							aria-label={`Toggle ${practice.name} active state`}
						/>
						<Button
							variant="ghost"
							size="icon-sm"
							render={
								<Link
									to="/w/$workspaceSlug/admin/ai/practice-detection/catalog/$practiceSlug"
									params={{ workspaceSlug, practiceSlug: practice.slug }}
								/>
							}
							aria-label={`Edit ${practice.name}`}
						>
							<Pencil className="h-4 w-4" />
						</Button>
						<Button
							variant="ghost"
							size="icon-sm"
							onClick={() => onDelete(practice)}
							aria-label={`Delete ${practice.name}`}
						>
							<Trash2 className="h-4 w-4" />
						</Button>
					</div>
				</div>
			</CardHeader>

			<CardContent className="space-y-3">
				{practice.triggerEvents.length > 0 && (
					<div className="flex flex-wrap items-center gap-1.5">
						{practice.triggerEvents.map((event) => (
							<Badge key={event} variant="outline" className="text-xs">
								{getShortLabel(event)}
							</Badge>
						))}
					</div>
				)}

				<div className="flex items-center gap-2 text-muted-foreground">
					<FileText className="h-4 w-4 shrink-0" aria-hidden="true" />
					<p className="text-sm line-clamp-1">{practice.criteria}</p>
				</div>
			</CardContent>
		</Card>
	);
}
