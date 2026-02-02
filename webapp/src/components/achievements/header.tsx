import { useReactFlow } from "@xyflow/react";
import { Maximize2, Sparkles, ZoomIn, ZoomOut } from "lucide-react";
import { Button } from "@/components/ui/button";

export function Header() {
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
					<p className="text-xs text-muted-foreground">Track your team contributions</p>
				</div>
			</div>

			<div className="flex items-center gap-4">
				{/* View Controls */}
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
			</div>
		</header>
	);
}
