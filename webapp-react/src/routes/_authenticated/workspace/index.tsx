import { createFileRoute } from "@tanstack/react-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { 
  getRepositoriesToMonitorOptions,
  getRepositoriesToMonitorQueryKey,
  addRepositoryToMonitorMutation, 
  removeRepositoryToMonitorMutation 
} from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useState } from "react";
import { PlusIcon, TrashIcon, RefreshCw, Github } from "lucide-react";
import { 
  Card, 
  CardContent, 
  CardDescription, 
  CardFooter, 
  CardHeader, 
  CardTitle 
} from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { toast, Toaster } from "sonner";

export const Route = createFileRoute("/_authenticated/workspace/")({
  component: Workspace,
});

type Repository = {
  owner: string;
  name: string;
};

function Workspace() {
  const queryClient = useQueryClient();
  const [newRepository, setNewRepository] = useState("");
  const [repositoryToDelete, setRepositoryToDelete] = useState<Repository | null>(null);

  // Fetch repositories to monitor
  const { data: repositories = [], isLoading, error, refetch } = useQuery({
    ...getRepositoriesToMonitorOptions({}),
  });
  
  // Add repository mutation
  const addRepositoryMutation = useMutation({
    ...addRepositoryToMonitorMutation(),
    onSuccess: () => {
      // Invalidate the repositories query to refetch data
      queryClient.invalidateQueries({ queryKey: getRepositoriesToMonitorQueryKey({}) });
      setNewRepository(""); // Clear input
      toast.success("Repository added successfully");
    },
    onError: (error) => {
      toast.error(`Failed to add repository: ${error.message || "Unknown error"}`);
    }
  });

  // Remove repository mutation
  const removeRepositoryMutation = useMutation({
    ...removeRepositoryToMonitorMutation(),
    onSuccess: () => {
      // Invalidate the repositories query to refetch data
      queryClient.invalidateQueries({ queryKey: getRepositoriesToMonitorQueryKey({}) });
      toast.success("Repository removed successfully");
    },
    onError: (error) => {
      toast.error(`Failed to remove repository: ${error.message || "Unknown error"}`);
    }
  });

  // Handler for adding a repository
  const handleAddRepository = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRepository.trim()) return;

    // Format should be owner/repo
    if (!newRepository.includes("/")) {
      toast.error("Repository should be in the format 'owner/repo'");
      return;
    }

    const [owner, repo] = newRepository.trim().split("/");
    
    addRepositoryMutation.mutate({
      path: { 
        owner, 
        name: repo 
      }
    });
  };

  // Handler for removing a repository
  const handleRemoveRepository = () => {
    if (!repositoryToDelete) return;
    
    removeRepositoryMutation.mutate({
      path: { 
        owner: repositoryToDelete.owner,
        name: repositoryToDelete.name
      }
    });
    
    setRepositoryToDelete(null);
  };

  return (
    <div className="container py-6">
      <div className="flex flex-col gap-6">
        <div className="flex items-center justify-between">
          <h1 className="text-3xl font-bold">Workspace</h1>
          <Button 
            variant="outline" 
            size="sm" 
            onClick={() => refetch()}
            disabled={isLoading}
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center">
              <Github className="mr-2" />
              Repositories to Monitor
            </CardTitle>
            <CardDescription>
              Manage the GitHub repositories that Hephaestus monitors for your account.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {error ? (
              <div className="text-red-500">
                Error loading repositories: {(error as Error).message || "Unknown error"}
              </div>
            ) : isLoading ? (
              <div className="flex justify-center items-center h-24">
                <RefreshCw className="h-6 w-6 animate-spin" />
              </div>
            ) : repositories.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                You are not monitoring any repositories yet. 
                Add your first repository below.
              </div>
            ) : (
              <div className="space-y-2">
                {repositories.map((repo, index) => {
                  // Split the repository string into owner and name
                  const [owner, name] = repo.split("/");
                  return (
                    <div key={index} className="flex items-center justify-between p-2 bg-muted/50 rounded-md">
                      <div className="flex items-center">
                        <Github className="h-4 w-4 mr-2" />
                        <span>{repo}</span>
                      </div>
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button 
                            variant="ghost" 
                            size="sm"
                            onClick={() => setRepositoryToDelete({ owner, name })}
                          >
                            <TrashIcon className="h-4 w-4" />
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Are you sure?</AlertDialogTitle>
                            <AlertDialogDescription>
                              This will remove {owner}/{name} from monitoring. You can add it again later if needed.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancel</AlertDialogCancel>
                            <AlertDialogAction onClick={handleRemoveRepository}>
                              Remove
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                  );
                })}
              </div>
            )}

            <form 
              onSubmit={handleAddRepository} 
              className="flex space-x-2 mt-4"
            >
              <Input
                placeholder="owner/repo (e.g. microsoft/vscode)"
                value={newRepository}
                onChange={(e) => setNewRepository(e.target.value)}
                className="flex-1"
              />
              <Button 
                type="submit" 
                disabled={addRepositoryMutation.isPending || !newRepository}
              >
                <PlusIcon className="h-4 w-4 mr-1" />
                Add
              </Button>
            </form>
          </CardContent>
          <CardFooter className="text-sm text-muted-foreground">
            Repositories added here will be analyzed by Hephaestus for insights.
          </CardFooter>
        </Card>
        
        <Toaster position="bottom-right" />
      </div>
    </div>
  );
}