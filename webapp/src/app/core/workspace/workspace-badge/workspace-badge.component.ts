import { Component } from '@angular/core';

@Component({
  selector: 'app-workspace-badge',
  standalone: true,
  imports: [],
  template: `<p>workspace-badge works!</p>`
})
export class WorkspaceBadgeComponent {
  // Workspace thumb + text
  // Opens sidebar on mobile
  // Hides thumb on desktop
  // Contains dropdown with workspace actions on desktop (in sidebar on mobile)
}
