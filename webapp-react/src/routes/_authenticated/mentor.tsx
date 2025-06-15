import { useChat } from '@ai-sdk/react';
import { createFileRoute } from "@tanstack/react-router";
import { v4 as uuidv4 } from 'uuid';

import { ComingSoon } from "@/components/shared/ComingSoon";
import environment from '@/environment';
import { keycloakService, useAuth } from '@/integrations/auth';

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
	const { messages, input, handleSubmit, handleInputChange, status, error } = useChat({
		streamProtocol: 'data',
    generateId: () => uuidv4(),
    sendExtraMessageFields: true,
		api: `${environment.serverUrl}/mentor/chat`,
    headers: {
      // Use the current token from keycloakService (will be updated by the main.tsx interceptor)
      "Authorization": `Bearer ${keycloakService.getToken()}`
		},
		onError: (error) => {
			console.error('Chat error:', error);
		},
		onFinish: (message) => {
			console.log('Chat finished:', message);
		},
    onResponse: (message) => {
      console.log('Chat response:', message);
    }
	});

	console.log('Chat status:', status, 'Messages:', messages);
	if (error) {
		console.error('Chat error state:', error);
	}

  return (
    <div>
      {messages.map(message => (
        <div key={message.id}>
          <strong>{`${message.role}: `}</strong>
          {message.parts.map((part, index) => {
            switch (part.type) {
              case 'text':
                return <span key={`${message.id}-${index}`}>{part.text}</span>;
              // other cases can handle images, tool calls, etc
            }
          })}
        </div>
      ))}

      <form onSubmit={handleSubmit}>
        <input
          value={input}
          placeholder="Send a message..."
          onChange={handleInputChange}
          disabled={status !== 'ready'}
        />
      </form>
      {status}
    </div>
  );
}