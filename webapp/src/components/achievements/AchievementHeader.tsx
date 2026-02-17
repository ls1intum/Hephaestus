import { useReactFlow } from "@xyflow/react";
import { List, Map as MapIcon, Maximize2, Sparkles, ZoomIn, ZoomOut } from "lucide-react";
import type { ViewMode } from "@/components/achievements/types";
import { Button } from "@/components/ui/button";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

interface HeaderProps {
	viewMode?: ViewMode;
	onViewModeChange?: (mode: ViewMode) => void;
	showZoomControls?: boolean;
}

export function AchievementHeader({
	viewMode = "tree",
	onViewModeChange,
	showZoomControls = true,
}: HeaderProps) {
	const reactFlow = useReactFlow();

	const handleZoomIn = () => {
		reactFlow.zoomIn();
	};

	const handleZoomOut = () => {
		reactFlow.zoomOut();
	};

	const handleFitView = () => {
		reactFlow.fitView({ padding: 0.15 });
	};

	return (
		<header className="h-16 bg-card/80 backdrop-blur-sm border-b border-border px-6 flex items-center justify-between">
			<div className="flex items-center gap-3">
				<div className="w-10 h-10 rounded-lg bg-foreground flex items-center justify-center shadow-[0_0_15px_rgba(var(--shadow-rgb),0.2)]">
					<Sparkles className="w-5 h-5 text-background" />
				</div>
				<div>
					<h1 className="text-lg font-bold text-foreground">Contributor Journey</h1>
					<p className="text-xs text-muted-foreground">Track your own contributions</p>
				</div>
			</div>

			<div className="flex items-center gap-4">
				{/* View Mode Toggle */}
				{onViewModeChange && (
					<ToggleGroup
						value={[viewMode]}
						onValueChange={(value) => {
							// Base UI returns an array; take the last selected value for single-select behavior
							const newValue = value[value.length - 1] as ViewMode | undefined;
							if (newValue) onViewModeChange(newValue);
						}}
						aria-label="View mode"
						className="bg-secondary/50 rounded-lg p-1"
					>
						<ToggleGroupItem
							value="tree"
							aria-label="achievements tree view"
							className="h-8 px-3 data-[state=on]:bg-background"
						>
							<MapIcon className="w-4 h-4 mr-1" />
							<span className="text-sm">Tree</span>
						</ToggleGroupItem>
						<ToggleGroupItem
							value="list"
							aria-label="achievements list view"
							className="h-8 px-3 data-[state=on]:bg-background"
						>
							<List className="w-4 h-4 mr-1" />
							<span className="text-sm">List</span>
						</ToggleGroupItem>
					</ToggleGroup>
				)}

				{/* Zoom Controls - only show for tree view */}
				{showZoomControls && viewMode === "tree" && (
					<div className="flex items-center gap-1 bg-secondary/50 rounded-lg p-1">
						<Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleZoomIn}>
							<ZoomIn className="w-4 h-4" />
						</Button>
						<Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleZoomOut}>
							<ZoomOut className="w-4 h-4" />
						</Button>
						<Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleFitView}>
							<Maximize2 className="w-4 h-4" />
						</Button>
					</div>
				)}
			</div>
		</header>
	);
}
