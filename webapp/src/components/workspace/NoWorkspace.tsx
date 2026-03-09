import { Link } from "@tanstack/react-router";
import { Folders, PlusIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";

export function NoWorkspace() {
	return (
		<Empty>
			<EmptyHeader>
				<EmptyMedia variant="icon">
					<Folders />
				</EmptyMedia>
				<EmptyTitle>No workspace</EmptyTitle>
				<EmptyDescription>You&apos;re not a member of any workspace yet.</EmptyDescription>
			</EmptyHeader>
			<Button render={<Link to="/workspaces/new" />}>
				<PlusIcon className="mr-2 size-4" />
				Create Workspace
			</Button>
		</Empty>
	);
}
