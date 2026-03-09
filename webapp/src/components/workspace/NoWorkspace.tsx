import { Folders, PlusIcon } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { CreateWorkspaceDialog } from "@/components/workspace/create-workspace";

export function NoWorkspace() {
	const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);

	return (
		<>
			<Empty>
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Folders />
					</EmptyMedia>
					<EmptyTitle>No workspace</EmptyTitle>
					<EmptyDescription>You&apos;re not a member of any workspace yet.</EmptyDescription>
				</EmptyHeader>
				<Button onClick={() => setIsCreateDialogOpen(true)}>
					<PlusIcon className="mr-2 size-4" />
					Create Workspace
				</Button>
			</Empty>
			<CreateWorkspaceDialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen} />
		</>
	);
}
