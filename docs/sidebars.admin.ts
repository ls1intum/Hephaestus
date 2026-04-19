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
        {type: 'doc', id: 'dsms/dsfa-prescreen', label: 'DPIA Pre-Screen'},
        {type: 'doc', id: 'dsms/vt-dsms', label: 'Verarbeitungstätigkeit'},
        {type: 'doc', id: 'dsms/toms', label: 'TOMs'},
        {type: 'doc', id: 'dsms/avv-checklist', label: 'AVV Checklist'},
      ],
    },
  ],
};

export default sidebars;
