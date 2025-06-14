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
import { ChevronsUpDown, Plus } from "lucide-react";
import { useEffect, useState } from "react";

interface Workspace {
	name: string;
	logoUrl: string;
}

export function WorkspaceSwitcher({
	workspaces,
	activeWorkspace,
	onWorkspaceChange,
	onAddWorkspace,
}: {
	workspaces: Workspace[];
	activeWorkspace: Workspace;
	onWorkspaceChange?: (workspace: Workspace) => void;
	onAddWorkspace?: () => void;
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

	return (
		<SidebarMenu>
			<SidebarMenuItem>
				<DropdownMenu open={isDropdownOpen} onOpenChange={setIsDropdownOpen}>
					<DropdownMenuTrigger asChild>
						<SidebarMenuButton
							size="lg"
							className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
						>
							<div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground overflow-clip">
								<img
									src={activeWorkspace.logoUrl}
									alt={`Workspace logo for ${activeWorkspace.name}`}
								/>
							</div>
							<div className="grid flex-1 text-left text-sm leading-tight">
								<span className="truncate font-semibold">
									{activeWorkspace.name}
								</span>
							</div>
							<ChevronsUpDown className="ml-auto" />
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
								key={workspace.name}
								onClick={() => onWorkspaceChange?.(workspace)}
								className="gap-2 p-2"
							>
								<div className="flex size-6 items-center justify-center rounded-sm border overflow-clip">
									<img
										src={workspace.logoUrl}
										alt={`Workspace logo for ${workspace.name}`}
										className="shrink-0"
									/>
								</div>
								{workspace.name}
								<DropdownMenuShortcut>⌘{index + 1}</DropdownMenuShortcut>
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
