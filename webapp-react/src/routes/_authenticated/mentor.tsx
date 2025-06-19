import { ComingSoon } from "@/components/shared/ComingSoon";
import environment from '@/environment';
import { keycloakService, useAuth } from '@/integrations/auth';
import { v4 as uuidv4 } from 'uuid';

import { useChat } from '@ai-sdk/react';
import { createFileRoute } from "@tanstack/react-router";
import { DefaultChatTransport } from "ai";
import { useState } from 'react';

export const Route = createFileRoute("/_authenticated/mentor")({
	component: RouteComponent,
});

function RouteComponent() {
	const { hasRole } = useAuth();

	if (!hasRole("mentor_access")) {
		return <div className="h-1/2 flex items-center justify-center">
			<ComingSoon />
		</div>;
	}

	return <MentorContainer />;
}


function MentorContainer() {
  const { error, status, sendMessage, messages, reload, stop } = useChat({
    generateId: () => uuidv4(),
    transport: new DefaultChatTransport({ 
      api: `${environment.serverUrl}/mentor/chat`,
      headers: {
        "Authorization": `Bearer ${keycloakService.getToken()}`
      }
    }),
  });

  console.log(messages);
  return (
    <div className="flex flex-col w-full max-w-md py-24 mx-auto stretch">
      {messages.map(m => (
        <div key={m.id} className="whitespace-pre-wrap">
          {m.role === 'user' ? 'User: ' : 'AI: '}
          {m.parts.map(part => {
            if (part.type === 'text') {
              return part.text;
            }
          })}
        </div>
      ))}

      {(status === 'submitted' || status === 'streaming') && (
        <div className="mt-4 text-gray-500">
          {status === 'submitted' && <div>Loading...</div>}
          <button
            type="button"
            className="px-4 py-2 mt-4 text-blue-500 border border-blue-500 rounded-md"
            onClick={stop}
          >
            Stop
          </button>
        </div>
      )}

      {error && (
        <div className="mt-4">
          <div className="text-red-500">An error occurred.</div>
          <button
            type="button"
            className="px-4 py-2 mt-4 text-blue-500 border border-blue-500 rounded-md"
            onClick={() => reload()}
          >
            Retry
          </button>
        </div>
      )}

      <ChatInput status={status} onSubmit={text => sendMessage({ text })} />
    </div>
  );
}


function ChatInput({
  status,
  onSubmit,
  stop,
}: {
  status: string;
  onSubmit: (text: string) => void;
  stop?: () => void;
}) {
  const [text, setText] = useState('');

  return (
    <form
      onSubmit={e => {
        e.preventDefault();
        if (text.trim() === '') return;
        onSubmit(text);
        setText('');
      }}
    >
      <input
        className="fixed bottom-0 w-full max-w-md p-2 mb-8 border border-gray-300 rounded shadow-xl"
        placeholder="Say something..."
        disabled={status !== 'ready'}
        value={text}
        onChange={e => setText(e.target.value)}
      />
      {stop && (status === 'streaming' || status === 'submitted') && (
        <button
          className="fixed bottom-0 w-full max-w-md p-2 mb-8 border border-gray-300 rounded shadow-xl"
          type="submit"
          onClick={stop}
        >
          Stop
        </button>
      )}
    </form>
  );
}
