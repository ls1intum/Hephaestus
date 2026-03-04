import { Grid3x3, Magnet, MessageSquareOff, Move, Save } from "lucide-react";
import { Button } from "@/components/ui/button.tsx";
import { Label } from "@/components/ui/label.tsx";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select.tsx";
import { Separator } from "@/components/ui/separator.tsx";
import { Switch } from "@/components/ui/switch.tsx";
import type { EdgeDisplayMode } from "./utils.ts";

const SNAP_GRID_OPTIONS = [24, 48, 96] as const;
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
	/** Whether tooltips should be shown */
	showTooltips: boolean;
	onShowTooltipsChange: (value: boolean) => void;
	/** Save callback */
	onSave: () => void;
	/** Selected edge display mode */
	edgeDisplayMode: EdgeDisplayMode;
	onEdgeDisplayModeChange: (mode: EdgeDisplayMode) => void;
}

export function DesignerToolbar({
	isDraggable,
	onDraggableChange,
	isSnapping,
	onSnappingChange,
	snapSize,
	onSnapSizeChange,
	showTooltips,
	onShowTooltipsChange,
	onSave,
	edgeDisplayMode,
	onEdgeDisplayModeChange,
}: DesignerToolbarProps) {
	return (
		<div className="absolute top-4 right-4 z-50 flex items-center gap-3 bg-background/95 backdrop-blur-sm p-2.5 rounded-xl shadow-lg border border-border">
			{/* Drag mode toggle */}
			<div className="flex items-center gap-2">
				<Move className="w-4 h-4 text-muted-foreground" />
				<Switch id="designer-drag-mode" checked={isDraggable} onCheckedChange={onDraggableChange} />
				<Label htmlFor="designer-drag-mode" className="cursor-pointer text-sm">
					Drag
				</Label>
			</div>

			<Separator orientation="vertical" className="h-6" />

			{/* Tooltip toggle */}
			<div className="flex items-center gap-2">
				<MessageSquareOff className="w-4 h-4 text-muted-foreground" />
				<Switch
					id="designer-tooltip-mode"
					checked={!showTooltips}
					onCheckedChange={(checked) => onShowTooltipsChange(!checked)}
				/>
				<Label htmlFor="designer-tooltip-mode" className="cursor-pointer text-sm">
					Hide Tooltips
				</Label>
			</div>

			<Separator orientation="vertical" className="h-6" />

			{/* Snap toggle */}
			<div className="flex items-center gap-2">
				<Magnet className="w-4 h-4 text-muted-foreground" />
				<Switch id="designer-snap-mode" checked={isSnapping} onCheckedChange={onSnappingChange} />
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

			{/* Edge Style Selector */}
			<div className="flex items-center gap-2">
				<Label className="text-xs text-muted-foreground mr-1 whitespace-nowrap">Edge Style:</Label>
				<Select value={edgeDisplayMode} onValueChange={(val) => onEdgeDisplayModeChange(val as EdgeDisplayMode)}>
					<SelectTrigger className="w-45 h-8 text-xs">
						<SelectValue placeholder="Select Edge Style" />
					</SelectTrigger>
					<SelectContent>
						<SelectItem value="achievement">Standard Achievement</SelectItem>
						<SelectItem value="synthwave">Synthwave (Mono)</SelectItem>
						<SelectItem value="equalizer-traveling">Equalizer (Traveling)</SelectItem>
						<SelectItem value="equalizer-static">Equalizer (Static)</SelectItem>
						<SelectItem value="equalizer-chain">Equalizer (Chain)</SelectItem>
					</SelectContent>
				</Select>
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
