import { Grid3x3, Magnet, Move, Save } from "lucide-react";
import { Button } from "@/components/ui/button.tsx";
import { Label } from "@/components/ui/label.tsx";
import { Separator } from "@/components/ui/separator.tsx";
import { Switch } from "@/components/ui/switch.tsx";

const SNAP_GRID_OPTIONS = [10, 25, 50] as const;
export type SnapGridSize = (typeof SNAP_GRID_OPTIONS)[number];

export interface DesignerToolbarProps {
	/** Whether nodes can be dragged */
	isDraggable: boolean;
	onDraggableChange: (value: boolean) => void;
	/** Whether grid snapping is active */
	isSnapping: boolean;
	onSnappingChange: (value: boolean) => void;
	/** The current snap grid size */
	snapSize: SnapGridSize;
	onSnapSizeChange: (size: SnapGridSize) => void;
	/** Save callback */
	onSave: () => void;
}

export function DesignerToolbar({
	isDraggable,
	onDraggableChange,
	isSnapping,
	onSnappingChange,
	snapSize,
	onSnapSizeChange,
	onSave,
}: DesignerToolbarProps) {
	return (
		<div className="absolute top-4 right-4 z-50 flex items-center gap-3 bg-background/95 backdrop-blur-sm p-2.5 rounded-xl shadow-lg border border-border">
			{/* Drag mode toggle */}
			<div className="flex items-center gap-2">
				<Move className="w-4 h-4 text-muted-foreground" />
				<Switch
					id="designer-drag-mode"
					checked={isDraggable}
					onCheckedChange={onDraggableChange}
				/>
				<Label htmlFor="designer-drag-mode" className="cursor-pointer text-sm">
					Drag
				</Label>
			</div>

			<Separator orientation="vertical" className="h-6" />

			{/* Snap toggle */}
			<div className="flex items-center gap-2">
				<Magnet className="w-4 h-4 text-muted-foreground" />
				<Switch
					id="designer-snap-mode"
					checked={isSnapping}
					onCheckedChange={onSnappingChange}
				/>
				<Label htmlFor="designer-snap-mode" className="cursor-pointer text-sm">
					Snap
				</Label>
			</div>

			{/* Snap grid size selector – only visible when snapping is on */}
			{isSnapping && (
				<div className="flex items-center gap-1 ml-1">
					{SNAP_GRID_OPTIONS.map((size) => (
						<Button
							key={size}
							variant={snapSize === size ? "default" : "outline"}
							size="xs"
							onClick={() => onSnapSizeChange(size)}
						>
							{size}px
						</Button>
					))}
				</div>
			)}

			<Separator orientation="vertical" className="h-6" />

			{/* Grid indicator */}
			<div className="flex items-center gap-1.5 text-muted-foreground">
				<Grid3x3 className="w-4 h-4" />
				<span className="text-xs font-medium tabular-nums">
					{isSnapping ? `${snapSize}px` : "free"}
				</span>
			</div>

			<Separator orientation="vertical" className="h-6" />

			{/* Save button */}
			<Button variant="default" size="sm" onClick={onSave} className="gap-1.5">
				<Save className="w-3.5 h-3.5" data-icon="inline-start" />
				Save Layout
			</Button>
		</div>
	);
}
