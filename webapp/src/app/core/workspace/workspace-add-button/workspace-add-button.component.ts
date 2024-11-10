import { Component } from '@angular/core';

@Component({
  selector: 'app-workspace-add-button',
  standalone: true,
  imports: [],
  template: `<p>workspace-add-button works!</p>`
})
export class WorkspaceAddButtonComponent {
  // Two variants:
  // - Compact for desktop
  // - Full for mobile
  // Opens sheet on mobile
  // Popover on desktop
}
