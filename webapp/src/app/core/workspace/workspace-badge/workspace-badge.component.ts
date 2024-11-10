import { Component, input } from '@angular/core';
import { WorkspaceThumbComponent } from '../workspace-thumb/workspace-thumb.component';

@Component({
  selector: 'app-workspace-badge',
  standalone: true,
  imports: [WorkspaceThumbComponent],
  template: `
    <div class="flex items-center gap-2">
      <div class="block md:hidden">
        <app-workspace-thumb [iconUrl]="iconUrl()" [hoverRingEnabled]="false"></app-workspace-thumb>
      </div>
      <span class="text-xl font-semibold">
        {{ title() }}
      </span>
    </div>
  `
})
export class WorkspaceBadgeComponent {
  // Open sidebar on mobile, smaller than md
  // Hide icon on desktop, show on mobile
  // Contains dropdown with workspace actions on desktop (in sidebar on mobile)

  title = input.required<string>();
  iconUrl = input<string>();
}
