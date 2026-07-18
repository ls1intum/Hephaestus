import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  adminSidebar: [
    {type: 'doc', id: 'production-setup', label: 'Production Setup'},
    {type: 'doc', id: 'compatibility-policy', label: 'Compatibility Policy'},
    {type: 'doc', id: 'runtime-roles', label: 'Runtime Roles'},
    {type: 'doc', id: 'agent-image-digests', label: 'Agent image digests'},
    {type: 'doc', id: 'legal-pages', label: 'Legal Pages'},
    {
      type: 'category',
      label: 'Data-Protection Documentation',
      link: {type: 'doc', id: 'dsms/dsms'},
      items: [
        {type: 'doc', id: 'dsms/record-of-processing', label: 'Record of Processing (Art. 30)'},
        {type: 'doc', id: 'dsms/dpia-prescreen', label: 'DPIA Pre-Screen (Art. 35)'},
        {type: 'doc', id: 'dsms/processor-checklist', label: 'Processor Checklist (Art. 28)'},
      ],
    },
  ],
};

export default sidebars;
