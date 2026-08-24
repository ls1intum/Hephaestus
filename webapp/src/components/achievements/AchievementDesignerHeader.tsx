import { useReactFlow } from "@xyflow/react";
import { Map as MapIcon, Maximize2, RefreshCw, ZoomIn, ZoomOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

export interface AchievementDesignerHeaderProps {
	isError?: boolean;
	isLoading?: boolean;
	onReload: () => void;
	isReloading: boolean;
}

export function AchievementDesignerHeader({
	isError = false,
	isLoading = false,
	onReload,
	isReloading,
}: AchievementDesignerHeaderProps) {
	const reactFlow = useReactFlow();

	const handleZoomIn = () => {
		void reactFlow.zoomIn();
	};

	const handleZoomOut = () => {
		void reactFlow.zoomOut();
	};

	const handleFitView = () => {
		void reactFlow.fitView({ padding: 0.15 });
	};

	return (
		<header className="flex shrink-0 flex-col gap-3 border-b border-border bg-card/80 px-4 py-3 backdrop-blur-sm sm:flex-row sm:items-center sm:justify-between sm:px-6">
			<div className="flex min-w-0 items-center gap-3">
				<div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-foreground shadow-[0_0_15px_rgba(var(--shadow-rgb),0.2)]">
					<MapIcon className="size-5 text-background" />
				</div>
				<div className="min-w-0">
					<h1 className="truncate text-lg font-bold text-foreground">Achievement designer</h1>
					<p className="hidden text-xs text-muted-foreground sm:block">
						Designer mode — configure achievement layouts
					</p>
				</div>
			</div>

			<div className="flex w-full flex-wrap items-center gap-2 sm:w-auto sm:flex-nowrap sm:gap-4">
				{isLoading ? (
					<div
						className="flex basis-full items-center gap-2 rounded-full border border-border bg-secondary/30 px-3 py-1 text-xs font-medium text-muted-foreground sm:basis-auto"
						role="status"
					>
						<Spinner className="size-3.5" aria-hidden="true" />
						Loading achievements...
					</div>
				) : isError ? (
					<div
						className="flex basis-full items-center gap-1.5 rounded-full border border-destructive/20 bg-destructive/10 px-3 py-1 text-xs font-medium text-destructive sm:basis-auto"
						role="alert"
					>
						<div className="size-2 animate-pulse rounded-full bg-destructive" />
						Failed to load achievement data
					</div>
				) : null}
				<div className="flex items-center gap-1 rounded-lg bg-secondary/50 p-1">
					<Button
						variant="ghost"
						size="icon"
						className="size-8"
						onClick={handleZoomIn}
						aria-label="Zoom in"
					>
						<ZoomIn className="size-4" />
					</Button>
					<Button
						variant="ghost"
						size="icon"
						className="size-8"
						onClick={handleZoomOut}
						aria-label="Zoom out"
					>
						<ZoomOut className="size-4" />
					</Button>
					<Button
						variant="ghost"
						size="icon"
						className="size-8"
						onClick={handleFitView}
						aria-label="Fit view"
					>
						<Maximize2 className="size-4" />
					</Button>
				</div>

				<Button
					variant="outline"
					size="sm"
					onClick={onReload}
					disabled={isReloading}
					className="h-9 gap-2 border-primary/20 bg-background/50 text-xs backdrop-blur-sm hover:border-primary/50"
					aria-label="Reload achievement definitions"
				>
					<RefreshCw className={cn("size-3.5", isReloading && "animate-spin")} />
					<span className="hidden sm:inline">Reload definitions</span>
				</Button>
			</div>
		</header>
	);
}
