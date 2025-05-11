import type { Message, ChatSummary, PullRequestsOverview } from './types';

// Parse the content of a message to extract summary data if present
export function getSummary(message: Message): ChatSummary | null {
  if (!message.content) return null;

  try {
    // Try to parse the content as JSON representing a chat summary
    if (message.content.startsWith('{"response":') && message.content.includes('"status":')) {
      return JSON.parse(message.content) as ChatSummary;
    }
  } catch (e) {
    console.error('Error parsing message content as summary:', e);
  }

  return null;
}

// Parse the content of a message to extract pull requests data if present
export function getPullRequests(message: Message): PullRequestsOverview | null {
  if (!message.content) return null;

  try {
    // Try to parse the content as JSON representing pull request overview
    if (message.content.startsWith('{"response":') && message.content.includes('"development":')) {
      return JSON.parse(message.content) as PullRequestsOverview;
    }
  } catch (e) {
    console.error('Error parsing message content as pull requests:', e);
  }

  return null;
}