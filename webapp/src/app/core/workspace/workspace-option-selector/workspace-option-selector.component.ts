import { Component } from '@angular/core';

@Component({
  selector: 'app-workspace-option-selector',
  standalone: true,
  imports: [],
  template: `<p>workspace-option-selector works!</p>`
})
export class WorkspaceOptionSelectorComponent {
  // Two variants:
  // - Compact for desktop with tooltip
  // - Full for mobile as list container for the sidebar
}
