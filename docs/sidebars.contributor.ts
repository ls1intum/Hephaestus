import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  contributorSidebar: [
    {
      type: 'doc',
      id: 'overview',
      label: 'Overview',
    },
    {
      type: 'category',
      label: 'Development Workflow',
      items: [
        'local-development',
        'testing',
        'e2e-testing',
        'coding-guidelines',
        'api-error-handling',
        'workspace-context',
        'dockerless-postgres',
      ],
    },
    {
      type: 'category',
      label: 'Architecture & Data',
      items: [
        'system-design',
        'instance-admin',
        'sync-lifecycle',
        'migration-unified-integration',
        'database-schema',
        'database-migration',
        'achievements',
      ],
    },
    {
      type: 'category',
      label: 'Operations',
      items: ['release-management', 'ci-cd'],
    },
    {
      type: 'category',
      label: 'AI Development',
      items: ['ai-agent-workflow', 'unified-pi-runtime', 'agent/agent-workspace-abi', 'llm-cost-vocabulary'],
    },
    {
      type: 'category',
      label: 'Practices & Feedback',
      items: [
        'practice-review-pipeline',
        'artifact-source-contract',
        'practice-catalogue',
        'practice-feedback-schema',
        'practice-feedback-language',
        'practice-review-glossary',
        'evaluation-provenance',
      ],
    },
  ],
};

export default sidebars;
