import { format } from 'date-fns';
import { BotIcon } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from "@/lib/utils";
import { getSummary, getPullRequests } from './message-parser';
import { ChatSummary } from './ChatSummary';
import { PullRequestsOverview } from './PullRequestsOverview';
import type { Message } from './types';
import { MessageSender } from './types';

interface MessagesProps {
  messages: Message[];
  isLoading?: boolean;
  className?: string;
}

export function Messages({ messages = [], isLoading = false, className }: MessagesProps) {
  return (
    <div className={cn("flex flex-col gap-4", className)}>
      {isLoading ? (
        // Loading skeleton UI
        Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className={`flex w-full ${i % 2 === 0 ? 'justify-end' : ''}`}>
            {i % 2 === 0 ? (
              <div>
                <Skeleton className="rounded-lg inline-block w-64 h-12" />
                <div className="flex justify-end">
                  <Skeleton className="h-4 w-32 justify-end" />
                </div>
              </div>
            ) : (
              <>
                <Skeleton className="w-10 h-10 rounded-full" />
                <div className="ml-3">
                  <Skeleton className="rounded-lg inline-block w-64 h-12" />
                  <Skeleton className="h-4 w-32" />
                </div>
              </>
            )}
          </div>
        ))
      ) : (
        // Actual messages list
        messages.map((message) => {
          const summary = getSummary(message);
          const pullRequests = getPullRequests(message);
          const isUser = message.sender === MessageSender.User;

          return (
            <div 
              key={message.id} 
              className={`flex w-full ${isUser ? 'justify-end' : 'justify-start'}`}
            >
              <div 
                className={`flex space-x-2 ${isUser ? 'flex-row-reverse' : ''} ${
                  summary == null ? 'md:max-w-[60%]' : ''
                }`}
              >
                {message.sender === MessageSender.Mentor && (
                  <div className="mr-2 flex flex-col">
                    <div className="w-10 h-10 bg-transparent border-2 border-cyan-500 rounded-full flex items-center justify-center">
                      <BotIcon className="text-2xl text-cyan-500" />
                    </div>
                  </div>
                )}
                <div 
                  className={`flex flex-col space-y-2 ${
                    isUser ? 'items-end' : 'items-start'
                  }`}
                >
                  {summary !== null && (
                    <ChatSummary 
                      status={summary.status || []} 
                      impediments={summary.impediments || []} 
                      promises={summary.promises || []} 
                    />
                  )}
                  <div
                    className={cn(
                      "p-3 px-4 rounded-lg inline-block w-fit",
                      isUser ? "bg-cyan-500 dark:bg-cyan-600 text-white" : "bg-muted text-primary",
                      summary !== null ? "md:max-w-[60%]" : ""
                    )}
                  >
                    {summary !== null ? (
                      <p>{summary.response}</p>
                    ) : pullRequests !== null ? (
                      <p>{pullRequests.response}</p>
                    ) : (
                      <p>{message.content}</p>
                    )}
                  </div>
                  {pullRequests !== null && (
                    <PullRequestsOverview 
                      pullRequests={pullRequests.development || []} 
                    />
                  )}
                  <span className="text-xs text-muted-foreground">
                    {isUser ? 'You' : 'AI Mentor'} · {format(new Date(message.sentAt), 'p')}
                  </span>
                </div>
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}