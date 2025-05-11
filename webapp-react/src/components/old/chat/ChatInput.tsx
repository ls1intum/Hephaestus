import React, { useState } from 'react';
import { Send, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert';
import { Spinner } from '@/components/ui/spinner';

interface ChatInputProps {
  isClosed: boolean;
  isSending: boolean;
  onSendMessage: (message: string) => void;
}

export function ChatInput({ isClosed, isSending, onSendMessage }: ChatInputProps) {
  const [message, setMessage] = useState<string>('');

  const handleSendMessage = () => {
    if (isSending || !message.trim()) {
      return;
    }

    onSendMessage(message);
    setMessage('');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return (
    <div className="flex items-start space-x-3">
      {isClosed ? (
        <Alert className="flex flex-row space-x-3">
          <AlertCircle className="h-4 w-4" />
          <div>
            <AlertTitle>This session is closed.</AlertTitle>
            <AlertDescription>
              Please, start a new session or continue messaging in your most recent one.
            </AlertDescription>
          </div>
        </Alert>
      ) : (
        <Textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Message AI Mentor"
          className="w-full min-h-20"
          style={{ resize: "none" }}
          disabled={isSending || isClosed}
        />
      )}
      <Button 
        disabled={isSending || isClosed}
        onClick={handleSendMessage}
        size="icon"
        aria-label="Send Message"
      >
        {isSending ? (
          <Spinner size="sm" />
        ) : (
          <Send className="h-5 w-5" />
        )}
      </Button>
    </div>
  );
}