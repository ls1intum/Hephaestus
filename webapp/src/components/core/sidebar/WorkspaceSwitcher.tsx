import { ChevronsUpDown, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import type { WorkspaceListItem } from "@/api/types.gen";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuShortcut,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	useSidebar,
} from "@/components/ui/sidebar";
import { Skeleton } from "@/components/ui/skeleton";

export function WorkspaceSwitcher({
	workspaces,
	activeWorkspace,
	onWorkspaceChange,
	onAddWorkspace,
	isLoading = false,
	isAdmin = false,
}: {
	workspaces: WorkspaceListItem[];
	activeWorkspace?: WorkspaceListItem;
	onWorkspaceChange?: (workspace: WorkspaceListItem) => void;
	onAddWorkspace?: () => void;
	isLoading?: boolean;
	isAdmin?: boolean;
}) {
	const { isMobile } = useSidebar();
	const [isDropdownOpen, setIsDropdownOpen] = useState(false);

	useEffect(() => {
		const handleKeyDown = (event: KeyboardEvent) => {
			// Check if Command (Mac) or Control (Windows/Linux) is pressed
			if (event.metaKey || event.ctrlKey) {
				// Check if the key is a number between 1-9
				const keyNum = Number.parseInt(event.key, 10);
				if (!Number.isNaN(keyNum) && keyNum >= 1 && keyNum <= 9) {
					// Get the workspace at index (keyNum - 1)
					const workspaceIndex = keyNum - 1;
					if (workspaceIndex < workspaces.length) {
						event.preventDefault();
						const selectedWorkspace = workspaces[workspaceIndex];
						onWorkspaceChange?.(selectedWorkspace);
					}
				}
			}
		};

		document.addEventListener("keydown", handleKeyDown);
		return () => {
			document.removeEventListener("keydown", handleKeyDown);
		};
	}, [workspaces, onWorkspaceChange]);

	if (isLoading) {
		return (
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton size="lg" className="pointer-events-none">
						<Skeleton className="aspect-square size-8 rounded-lg" />
						<div className="grid flex-1 text-left text-sm leading-tight gap-1 group-data-[collapsible=icon]:hidden">
							<Skeleton className="h-3 w-20" />
							<Skeleton className="h-2.5 w-12" />
						</div>
						<ChevronsUpDown className="ml-auto size-4 opacity-50 group-data-[collapsible=icon]:hidden" />
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		);
	}

	if (workspaces.length === 0) {
		if (isAdmin) {
			return (
				<SidebarMenu>
					<SidebarMenuItem>
						<SidebarMenuButton onClick={onAddWorkspace} size="lg">
							<div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
								<Plus className="size-4" />
							</div>
							<div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
								<span className="truncate font-semibold">Create Workspace</span>
							</div>
						</SidebarMenuButton>
					</SidebarMenuItem>
				</SidebarMenu>
			);
		}

		return (
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton disabled size="lg">
						<div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-muted text-muted-foreground">
							<ChevronsUpDown className="size-4" />
						</div>
						<div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
							<span className="truncate font-semibold">No Workspace</span>
						</div>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		);
	}

	return (
		<SidebarMenu>
			<SidebarMenuItem>
				<DropdownMenu open={isDropdownOpen} onOpenChange={setIsDropdownOpen}>
					<DropdownMenuTrigger asChild>
						<SidebarMenuButton
							size="lg"
							className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
						>
							<div className="flex aspect-square size-8 items-center justify-center rounded-lg text-sidebar-primary-foreground">
								<Avatar className="size-8 rounded-lg">
									<AvatarImage
										src={
											activeWorkspace
												? `https://github.com/${activeWorkspace.accountLogin}.png`
												: undefined
										}
										alt={activeWorkspace?.displayName}
									/>
									<AvatarFallback className="rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
										{(
											activeWorkspace?.displayName ||
											activeWorkspace?.workspaceSlug ||
											"WS"
										)
											.slice(0, 2)
											.toUpperCase()}
									</AvatarFallback>
								</Avatar>
							</div>
							<div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
								<span className="truncate font-semibold">
									{activeWorkspace?.displayName ?? "No workspace"}
								</span>
								<span className="truncate text-xs text-muted-foreground">
									{activeWorkspace?.accountLogin ?? "Select a workspace"}
								</span>
							</div>
							<ChevronsUpDown className="ml-auto group-data-[collapsible=icon]:hidden" />
						</SidebarMenuButton>
					</DropdownMenuTrigger>
					<DropdownMenuContent
						className="w-[--radix-dropdown-menu-trigger-width] min-w-56 rounded-lg"
						align="start"
						side={isMobile ? "bottom" : "right"}
						sideOffset={4}
					>
						<DropdownMenuLabel className="text-xs text-muted-foreground">
							Workspaces
						</DropdownMenuLabel>
						{workspaces.map((workspace, index) => (
							<DropdownMenuItem
								key={workspace.workspaceSlug}
								onClick={() => onWorkspaceChange?.(workspace)}
								className="gap-2 p-2"
							>
								<div className="flex size-6 items-center justify-center rounded-sm overflow-clip">
									<Avatar className="size-6 rounded-sm">
										<AvatarImage
											src={`https://github.com/${workspace.accountLogin}.png`}
											alt={workspace.displayName}
										/>
										<AvatarFallback className="rounded-sm text-xs bg-sidebar-primary text-sidebar-primary-foreground">
											{(workspace.displayName || workspace.workspaceSlug)
												.slice(0, 2)
												.toUpperCase()}
										</AvatarFallback>
									</Avatar>
								</div>
								<div className="flex flex-col leading-tight">
									<span className="font-medium">{workspace.displayName}</span>
									<span className="text-[11px] text-muted-foreground">
										{workspace.accountLogin}
									</span>
								</div>
								<DropdownMenuShortcut>
									{navigator.platform.toLowerCase().includes("mac")
										? "⌘"
										: "Ctrl"}
									{index + 1}
								</DropdownMenuShortcut>
							</DropdownMenuItem>
						))}
						<DropdownMenuSeparator />
						<DropdownMenuItem
							className="gap-2 p-2"
							onClick={() => onAddWorkspace?.()}
							disabled
						>
							<div className="flex size-6 items-center justify-center rounded-md border bg-background">
								<Plus className="size-4" />
							</div>
							<div className="font-medium text-muted-foreground">
								Add workspace
							</div>
						</DropdownMenuItem>
					</DropdownMenuContent>
				</DropdownMenu>
			</SidebarMenuItem>
		</SidebarMenu>
	);
}
