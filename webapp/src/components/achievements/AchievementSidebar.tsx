import { useReactFlow } from "@xyflow/react";
import {
	CircleDot,
	GitCommit,
	GitPullRequest,
	Layers,
	List,
	Loader2,
	Map as MapIcon,
	Maximize2,
	MessageSquare,
	PanelRightOpen,
	Sparkles,
	Trophy,
	ZoomIn,
	ZoomOut,
} from "lucide-react";
import type React from "react";
import { useEffect, useState } from "react";
import {
	categoryMeta,
	rarityAccentBackgrounds,
	rarityBorderColors,
	rarityLabels,
} from "@/components/achievements/styles";
import type { AchievementCategory, UIAchievement, ViewMode } from "@/components/achievements/types";
import { calculateStats } from "@/components/achievements/utils";
import { Button } from "@/components/ui/button";
import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import {
	SidebarContent,
	SidebarFooter,
	SidebarGroup,
	SidebarGroupContent,
	SidebarGroupLabel,
	SidebarHeader,
	SidebarSeparator,
} from "@/components/ui/sidebar";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";

/** Width of the right sidebar — keep in sync with the CSS variable value. */
const SIDEBAR_WIDTH = "20rem";

const categoryIcons: Record<AchievementCategory, React.ElementType> = {
	pull_requests: GitPullRequest,
	commits: GitCommit,
	communication: MessageSquare,
	issues: CircleDot,
	milestones: Layers,
};

export interface AchievementSidebarProps {
	viewMode: ViewMode;
	onViewModeChange: (mode: ViewMode) => void;
	isLoading: boolean;
	isError: boolean;
	achievements: UIAchievement[];
	/** Whether the viewer is looking at their own achievements. */
	isOwnProfile: boolean;
	/** Username of the user whose achievements are being displayed. */
	targetUsername: string;
}

/**
 * Shared sidebar content rendered identically on desktop (fixed panel) and
 * mobile (Sheet overlay). Extracted so both paths stay in sync.
 */
function SidebarBody({
	viewMode,
	onViewModeChange,
	isLoading,
	isError,
	achievements,
	isOwnProfile,
	targetUsername,
	onZoomIn,
	onZoomOut,
	onFitView,
}: AchievementSidebarProps & {
	onZoomIn: () => void;
	onZoomOut: () => void;
	onFitView: () => void;
}) {
	const stats = calculateStats(achievements);

	return (
		<>
			{/* ── Header: Title + Status ── */}
			<SidebarHeader className="px-5 pt-5 pb-3">
				<div className="flex items-center gap-3">
					<div className="w-9 h-9 rounded-lg bg-foreground flex items-center justify-center shadow-[0_0_15px_rgba(var(--shadow-rgb),0.15)]">
						<Sparkles className="w-4 h-4 text-background" />
					</div>
					<div>
						<h2 className="text-base font-bold text-sidebar-foreground leading-tight">
							{isOwnProfile ? "My Contributor Journey" : `${targetUsername}'s Journey`}
						</h2>
						<p className="text-xs text-muted-foreground">
							{isOwnProfile ? "Track your contributions" : "View their contributions"}
						</p>
					</div>
				</div>

				{isLoading && (
					<div className="mt-3 flex items-center gap-2 px-3 py-1.5 bg-secondary/30 text-muted-foreground rounded-full border border-border text-xs font-medium">
						<Loader2 className="w-3.5 h-3.5 animate-spin" />
						Loading achievements…
					</div>
				)}
				{isError && (
					<div className="mt-3 flex items-center gap-1.5 px-3 py-1.5 bg-destructive/10 text-destructive rounded-full border border-destructive/20 text-xs font-medium">
						<div className="w-2 h-2 rounded-full bg-destructive animate-pulse" />
						Failed to load achievement data
					</div>
				)}
			</SidebarHeader>

			<SidebarContent className="px-3 overflow-x-hidden">
				{/* ── View Mode Toggle ── */}
				<SidebarGroup>
					<SidebarGroupLabel>View</SidebarGroupLabel>
					<SidebarGroupContent>
						<div className="flex items-center gap-2">
							<ToggleGroup
								value={[viewMode]}
								onValueChange={(value) => {
									// Ensure at least one value is selected (last one clicked wins)
									const newValue = value.length > 0 ? (value[value.length - 1] as ViewMode) : viewMode;
									if (newValue !== viewMode) onViewModeChange(newValue);
								}}
								aria-label="View mode"
								className="bg-secondary/50 rounded-lg p-1 flex-1"
							>
								<ToggleGroupItem
									value="tree"
									aria-label="Tree view"
									className="h-8 px-3 flex-1 data-[state=on]:bg-background"
								>
									<MapIcon className="w-4 h-4 mr-1.5" />
									<span className="text-sm">Tree</span>
								</ToggleGroupItem>
								<ToggleGroupItem
									value="list"
									aria-label="List view"
									className="h-8 px-3 flex-1 data-[state=on]:bg-background"
								>
									<List className="w-4 h-4 mr-1.5" />
									<span className="text-sm">List</span>
								</ToggleGroupItem>
							</ToggleGroup>
						</div>

						{/* Zoom controls – only in tree mode */}
						{viewMode === "tree" && (
							<div className="flex items-center gap-1 mt-2 bg-secondary/50 rounded-lg p-1">
								<Button
									variant="ghost"
									size="icon"
									className="h-8 w-8 flex-1"
									onClick={onZoomIn}
									aria-label="Zoom in"
								>
									<ZoomIn className="w-4 h-4" />
								</Button>
								<Button
									variant="ghost"
									size="icon"
									className="h-8 w-8 flex-1"
									onClick={onZoomOut}
									aria-label="Zoom out"
								>
									<ZoomOut className="w-4 h-4" />
								</Button>
								<Button
									variant="ghost"
									size="icon"
									className="h-8 w-8 flex-1"
									onClick={onFitView}
									aria-label="Fit view"
								>
									<Maximize2 className="w-4 h-4" />
								</Button>
							</div>
						)}
					</SidebarGroupContent>
				</SidebarGroup>

				<SidebarSeparator />

				{/* ── Overall Progress ── */}
				<SidebarGroup>
					<SidebarGroupLabel>
						<Trophy className="w-4 h-4 mr-1.5" />
						{isOwnProfile ? "Your Progress" : `${targetUsername}'s Progress`}
					</SidebarGroupLabel>
					<SidebarGroupContent>
						<div className="p-3 rounded-lg bg-secondary/50 border border-border">
							<div className="flex items-center justify-between mb-2">
								<span className="text-sm font-medium text-sidebar-foreground">Total Progress</span>
								<span className="text-lg font-bold text-sidebar-foreground">
									{stats.percentage}%
								</span>
							</div>
							<ProgressRoot.Root
								value={stats.percentage}
								aria-label={`Total achievement progress: ${stats.percentage}%`}
							>
								<ProgressTrack className="h-3 [&>div]:bg-foreground">
									<ProgressIndicator />
								</ProgressTrack>
							</ProgressRoot.Root>{" "}
							<div className="flex justify-between mt-2 text-xs text-muted-foreground">
								<span>
									{stats.unlocked} / {stats.total} Achievements
								</span>
								<span>{stats.available} Available</span>
							</div>
						</div>
					</SidebarGroupContent>
				</SidebarGroup>

				{/* ── Category Breakdown ── */}
				<SidebarGroup>
					<SidebarGroupLabel>Categories</SidebarGroupLabel>
					<SidebarGroupContent className="space-y-2">
						{Object.entries(categoryMeta).map(([key, meta]) => {
							const category = key as AchievementCategory;
							const catStats = stats.byCategory[category];
							if (!catStats || catStats.total === 0) return null;
							const Icon = categoryIcons[category];
							const percentage = Math.round((catStats.unlocked / catStats.total) * 100);

							return (
								<div
									key={category}
									className="p-3 rounded-lg bg-secondary/30 border border-border/50 hover:border-border transition-colors"
								>
									<div className="flex items-center gap-3 mb-2">
										<div className="w-8 h-8 rounded-full flex items-center justify-center bg-foreground/10 text-foreground shrink-0 overflow-hidden">
											<Icon size={16} className="shrink-0" />
										</div>
										<div className="flex-1">
											<div className="flex items-center justify-between">
												<span className="text-sm font-medium text-sidebar-foreground">
													{meta.name}
												</span>
												<span className="text-xs text-muted-foreground">
													{catStats.unlocked}/{catStats.total}
												</span>
											</div>
										</div>
									</div>
									<ProgressRoot.Root
										value={percentage}
										aria-label={`${meta.name} progress: ${percentage}%`}
									>
										<ProgressTrack className="h-1.5 [&>div]:bg-foreground/70">
											<ProgressIndicator />
										</ProgressTrack>
									</ProgressRoot.Root>
								</div>
							);
						})}
					</SidebarGroupContent>
				</SidebarGroup>

				{/* ── Recent Unlocks ── */}
				<SidebarGroup>
					<SidebarGroupLabel>Recent Unlocks</SidebarGroupLabel>
					<SidebarGroupContent className="space-y-1.5">
						{[...achievements]
							.filter(
								(a): a is typeof a & { unlockedAt: Date } =>
									a.status === "unlocked" && !!a.unlockedAt,
							)
							.sort((a, b) => new Date(b.unlockedAt).getTime() - new Date(a.unlockedAt).getTime())
							.slice(0, 5)
							.map((achievement) => {
								const rarity = achievement.rarity ?? "common";
								const unlockedDate = achievement.unlockedAt;
								const Icon = achievement.icon;
								return (
									<div
										key={achievement.id}
										className="flex items-center gap-3 p-2 rounded-lg bg-secondary/20 hover:bg-secondary/40 transition-colors"
									>
										<div
											className={cn(
												"w-8 h-8 rounded-full flex items-center justify-center shrink-0 overflow-hidden",
												rarityAccentBackgrounds[rarity],
												"text-background",
											)}
										>
											<Icon size={18} className="shrink-0" />
										</div>
										<div className="flex-1 min-w-0">
											<p className="text-sm font-medium text-sidebar-foreground truncate">
												{achievement.name}
											</p>
											<p className="text-xs text-muted-foreground">
												{unlockedDate
													? new Date(unlockedDate).toLocaleDateString()
													: "Recently unlocked"}
											</p>
										</div>
									</div>
								);
							})}
					</SidebarGroupContent>
				</SidebarGroup>
			</SidebarContent>

			{/* ── Legend (pinned to bottom) ── */}
			<SidebarFooter className="border-t border-sidebar-border px-4 py-3">
				<span className="text-xs font-semibold text-sidebar-foreground/70 uppercase tracking-wider mb-1">
					Legend
				</span>
				<div className="flex gap-6 text-xs">
					{/* Status column */}
					<div className="space-y-1.5">
						<span className="text-muted-foreground font-medium">Status</span>
						<div className="flex items-center gap-1.5">
							<div className="w-2.5 h-2.5 rounded-full bg-node-unlocked" />
							<span className="text-muted-foreground">Unlocked</span>
						</div>
						<div className="flex items-center gap-1.5">
							<div className="w-2.5 h-2.5 rounded-full bg-node-available" />
							<span className="text-muted-foreground">Available</span>
						</div>
						<div className="flex items-center gap-1.5">
							<div className="w-2.5 h-2.5 rounded-full bg-node-locked" />
							<span className="text-muted-foreground">Locked</span>
						</div>
					</div>
					{/* Rarity column */}
					<div className="space-y-1.5">
						<span className="text-muted-foreground font-medium">Rarity</span>
						{(["common", "uncommon", "rare", "epic", "legendary", "mythic"] as const).map(
							(rarity) => (
								<div key={rarity} className="flex items-center gap-1.5">
									<div
										className={cn(
											"w-2.5 h-2.5 rounded-full border-2",
											rarityBorderColors[rarity],
											"bg-transparent",
										)}
									/>
									<span className="text-muted-foreground">{rarityLabels[rarity]}</span>
								</div>
							),
						)}
					</div>
				</div>
			</SidebarFooter>
		</>
	);
}

export function AchievementSidebar(props: AchievementSidebarProps) {
	const isMobile = useIsMobile();
	const reactFlow = useReactFlow();
	const [mobileOpen, setMobileOpen] = useState(false);

	const handleZoomIn = () => reactFlow.zoomIn();
	const handleZoomOut = () => reactFlow.zoomOut();
	const handleFitView = () => reactFlow.fitView({ padding: 0.15 });

	// On desktop, set a CSS variable on <html> so the root layout's SidebarInset
	// can add a matching right margin — keeping the global header in line.
	useEffect(() => {
		if (!isMobile) {
			document.documentElement.style.setProperty("--right-sidebar-width", SIDEBAR_WIDTH);
		}
		return () => {
			document.documentElement.style.removeProperty("--right-sidebar-width");
		};
	}, [isMobile]);

	const bodyProps = {
		...props,
		onZoomIn: handleZoomIn,
		onZoomOut: handleZoomOut,
		onFitView: handleFitView,
	};

	/* ── Mobile: Sheet overlay triggered by a floating button ── */
	if (isMobile) {
		return (
			<>
				<Button
					variant="secondary"
					size="icon"
					className="fixed bottom-4 right-4 z-40 h-12 w-12 rounded-full shadow-lg"
					onClick={() => setMobileOpen(true)}
					aria-label="Open achievement sidebar"
				>
					<PanelRightOpen className="w-5 h-5" />
				</Button>
				<Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
					<SheetContent side="right" className="w-80 p-0 overflow-y-auto">
						<SheetHeader className="sr-only">
							<SheetTitle>Achievement Sidebar</SheetTitle>
							<SheetDescription>View your achievement progress and controls.</SheetDescription>
						</SheetHeader>
						<SidebarBody {...bodyProps} />
					</SheetContent>
				</Sheet>
			</>
		);
	}

	/* ── Desktop: Fixed right panel ── */
	return (
		<div
			className="fixed top-0 right-0 h-dvh bg-sidebar text-sidebar-foreground border-l border-sidebar-border flex flex-col overflow-y-auto z-10"
			style={{ width: SIDEBAR_WIDTH }}
			data-slot="achievement-sidebar"
			role="complementary"
			aria-label="Achievement sidebar"
		>
			<SidebarBody {...bodyProps} />
		</div>
	);
}
