import { Message } from '@app/core/modules/openapi';

export interface Summary extends Message {
  status: string[];
  impediments: string[];
  promises: string[];
  response: string;
}

export interface PullRequests extends Message {
  development: PullRequest[];
  response: string;
}

export function getSummary(message: Message): Summary | null {
  const content = message.content;
  if (!content.includes('SUMMARY')) {
    return null;
  }

  const result: Summary = {
    ...message,
    content: '',
    status: [],
    impediments: [],
    promises: [],
    response: ''
  };

  const sections = content.split(/(?=STATUS|IMPEDIMENTS|PROMISES|RESPONSE)/).slice(1);

  sections.forEach((section) => {
    const lines = section.trim().split('\n');
    const sectionType = lines[0].trim();
    const items = lines.slice(1).filter((line) => line.trim());

    switch (sectionType) {
      case 'STATUS':
        result.status = items;
        break;
      case 'IMPEDIMENTS':
        result.impediments = items;
        break;
      case 'PROMISES':
        result.promises = items;
        break;
      case 'RESPONSE':
        result.response = items.join('\n');
        break;
    }
  });

  return result;
}

export interface PullRequest {
  number: number;
  title: string;
  state: string;
  isDraft: boolean;
  isMerged: boolean;
  url: string;
}

export function getPullRequests(message: Message): PullRequests | null {
  const content = message.content;
  if (!content.includes('DEVELOPMENT')) {
    return null;
  }

  const result: PullRequests = {
    ...message,
    development: [],
    response: ''
  };

  const developmentSection = content.split('RESPONSE')[0].split('DEVELOPMENT')[1];
  if (!developmentSection) {
    return result;
  }

  const prBlocks = developmentSection
    .split('PR')
    .slice(1)
    .map((s) => s.trim());

  result.development = prBlocks.map((block) => {
    const pr = {
      number: 0,
      title: '',
      state: '',
      isDraft: false,
      isMerged: false,
      url: ''
    };
    block.split('\n').forEach((line) => {
      const key = line.split(':', 1)[0].trim();
      const value = line.slice(key.length + 1).trim();
      if (!key || !value) return;

      switch (key) {
        case 'Number':
          pr.number = parseInt(value);
          break;
        case 'Title':
          pr.title = value;
          break;
        case 'State':
          pr.state = value;
          break;
        case 'Draft':
          pr.isDraft = value === 'true';
          break;
        case 'Merged':
          pr.isMerged = value === 'true';
          break;
        case 'URL':
          pr.url = value;
          break;
      }
    });
    return pr;
  });

  const responseSection = content.split('RESPONSE')[1];
  if (responseSection) {
    result.response = responseSection.trim();
  }

  return result;
}
