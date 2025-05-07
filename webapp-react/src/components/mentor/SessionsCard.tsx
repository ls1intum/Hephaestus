import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Toggle } from '@/components/ui/toggle';
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

export interface Session {
  id: number;
  createdAt: string;
}

interface SessionsCardProps {
  sessions?: Session[];
  selectedSessionId?: number | null;
  onSessionSelect: (sessionId: number) => void;
  onCreateNewSession: () => void;
  isLoading?: boolean;
  isLastSessionClosed?: boolean;
}

export function SessionsCard({
  sessions = [],
  selectedSessionId = null,
  onSessionSelect,
  onCreateNewSession,
  isLoading = false,
  isLastSessionClosed = true,
}: SessionsCardProps) {
  return (
    <Card className="flex flex-col px-6 lg:max-h-[calc(100dvh-200px)] pb-6 overflow-auto">
      {isLastSessionClosed ? (
        <Button 
          onClick={onCreateNewSession} 
          className="w-full gap-2 mt-6 justify-start h-9"
          aria-label="New Session"
        >
          <Plus className="size-4" />
          <span>New Session</span>
        </Button>
      ) : (
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button className="w-full gap-2 mt-6 justify-start h-9">
              <Plus className="size-4" />
              <span>New Session</span>
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
              <AlertDialogDescription>
                You have not finished your most recent session yet. If you start a new session, 
                your current session will be closed. It may affect the quality of your mentorship.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction onClick={onCreateNewSession}>
                Start New Session
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}

      {isLoading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="w-48 h-6 mt-6 pt-3" />
          <Skeleton className="w-full h-9" />
          <Skeleton className="w-full h-9" />
          <Skeleton className="w-full h-9" />
          <Skeleton className="w-full h-9" />
          <Skeleton className="w-full h-9" />
        </div>
      ) : sessions.length > 0 ? (
        <div className="flex flex-col gap-2">
          <h4 className="text-lg font-semibold mt-2 pt-3">Past Sessions</h4>
          {sessions.map((session) => (
            <Toggle
              key={session.id}
              pressed={selectedSessionId === session.id}
              onPressedChange={() => onSessionSelect(session.id)}
              className="justify-start"
            >
              {new Date(session.createdAt).toLocaleString()}
            </Toggle>
          ))}
        </div>
      ) : null}
    </Card>
  );
}