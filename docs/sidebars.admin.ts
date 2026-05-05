import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  adminSidebar: [
    {type: 'doc', id: 'production-setup', label: 'Production Setup'},
    {type: 'doc', id: 'legal-pages', label: 'Legal Pages'},
    {
      type: 'category',
      label: 'DSMS Submission Package',
      link: {type: 'doc', id: 'dsms/dsms'},
      items: [
        {type: 'doc', id: 'dsms/submission-guide', label: 'Submission Guide'},
        {type: 'doc', id: 'dsms/dpia-prescreen', label: 'DPIA Pre-Screen'},
        {type: 'doc', id: 'dsms/record-of-processing', label: 'Record of Processing (VVT)'},
        {type: 'doc', id: 'dsms/toms', label: 'TOMs'},
        {type: 'doc', id: 'dsms/processor-checklist', label: 'Processor Checklist (AVV)'},
      ],
    },
  ],
};

export default sidebars;
