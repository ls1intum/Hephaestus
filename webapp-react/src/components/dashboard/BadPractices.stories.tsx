import type { Meta, StoryObj } from '@storybook/react';
import { BadPractices } from './BadPractices';

const meta = {
  title: 'Dashboard/BadPractices',
  component: BadPractices,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof BadPractices>;

export default meta;
type Story = StoryObj<typeof meta>;

const sampleBadPractices = [
  {
    id: '1',
    type: 'Security',
    message: 'Hardcoded API credentials detected',
    repository: 'auth-service',
    filePath: 'src/config/auth.js',
    severity: 'high' as const,
    url: 'https://github.com/org/auth-service/blob/main/src/config/auth.js',
  },
  {
    id: '2',
    type: 'Bug',
    message: 'Possible memory leak in component lifecycle',
    repository: 'frontend-app',
    filePath: 'src/components/Dashboard.tsx',
    severity: 'medium' as const,
    url: 'https://github.com/org/frontend-app/blob/main/src/components/Dashboard.tsx',
  },
  {
    id: '3',
    type: 'Vulnerability',
    message: 'Outdated dependency with known security issues',
    repository: 'api-service',
    filePath: 'package.json',
    severity: 'high' as const,
    url: 'https://github.com/org/api-service/blob/main/package.json',
  },
  {
    id: '4',
    type: 'Code quality',
    message: 'Excessive function complexity',
    repository: 'data-processor',
    filePath: 'src/services/processor.js',
    severity: 'low' as const,
    url: 'https://github.com/org/data-processor/blob/main/src/services/processor.js',
  },
  {
    id: '5',
    type: 'Performance',
    message: 'Inefficient database query',
    repository: 'user-service',
    filePath: 'src/repositories/UserRepository.js',
    severity: 'medium' as const,
    url: 'https://github.com/org/user-service/blob/main/src/repositories/UserRepository.js',
  }
];

export const Default: Story = {
  args: {
    badPractices: sampleBadPractices,
  },
};

export const HighSeverity: Story = {
  args: {
    badPractices: sampleBadPractices.filter(p => p.severity === 'high'),
  },
};

export const MediumSeverity: Story = {
  args: {
    badPractices: sampleBadPractices.filter(p => p.severity === 'medium'),
  },
};

export const LowSeverity: Story = {
  args: {
    badPractices: sampleBadPractices.filter(p => p.severity === 'low'),
  },
};

export const Empty: Story = {
  args: {
    badPractices: [],
  },
};