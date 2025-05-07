import { BotMessageSquare } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
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

interface StartSessionCardProps {
  isLoading?: boolean;
  hasSessions?: boolean;
  isLastSessionClosed?: boolean;
  onCreateNewSession: () => void;
}

export function StartSessionCard({
  isLoading = false,
  hasSessions = false,
  isLastSessionClosed = true,
  onCreateNewSession
}: StartSessionCardProps) {
  return (
    <div className="flex flex-col items-center justify-center space-y-6">
      {isLoading ? (
        <>
          <Skeleton className="size-20 rounded-full" />
          <div className="grid gap-4 justify-items-center items-center">
            <Skeleton className="w-72 h-5" />
            <Skeleton className="w-96 h-5" />
            <Skeleton className="w-[420px] h-4" />
            <Skeleton className="w-32 h-10 mt-2" />
          </div>
        </>
      ) : (
        <>
          <div className="size-20 border-cyan-500 rounded-full flex items-center justify-center border-4">
            <BotMessageSquare className="text-4xl text-cyan-500" />
          </div>
          <div>
            <h2 className="text-center text-xl text-primary font-semibold max-w-3xl dark:text-white">
              Meet Your Personal AI Mentor:<br />Designed to help you grow and stay focused.
            </h2>
            {hasSessions ? (
              <p className="text-center text-l pt-2">
                Review past sessions or begin a new conversation to stay on track.
              </p>
            ) : (
              <p className="text-center text-l pt-2">
                Begin your first session to stay on track.
              </p>
            )}
          </div>

          {isLastSessionClosed ? (
            <Button onClick={onCreateNewSession} aria-label="Start Session">
              Start Session
            </Button>
          ) : (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button className="w-full gap-2 mt-6 justify-start h-9">
                  Start Session
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
        </>
      )}
    </div>
  );
}