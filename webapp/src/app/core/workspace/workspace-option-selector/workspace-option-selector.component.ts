import { Component, computed, input, output } from '@angular/core';
import { WorkspaceOptionComponent } from '../workspace-option/workspace-option.component';
import { hlm } from '@spartan-ng/ui-core';

type Workspace = {
  id: string;
  title: string;
  iconUrl: string;
};

@Component({
  selector: 'app-workspace-option-selector',
  standalone: true,
  imports: [WorkspaceOptionComponent],
  template: `
    <div [class]="computedClass()">
      @for (workspace of workspaces(); track workspace.id) {
        <app-workspace-option
          [isCompact]="isCompact()"
          [isSelected]="selectedWorkspace().id === workspace.id"
          [iconUrl]="workspace.iconUrl"
          [title]="workspace.title"
          (onSelect)="onSelect.emit(workspace)"
          (onSignOut)="onSignOut.emit(workspace)"
        />
      }
    </div>
  `
})
export class WorkspaceOptionSelectorComponent {
  isCompact = input.required<boolean>();
  selectedWorkspace = input.required<Workspace>();
  workspaces = input.required<Workspace[]>();

  onSelect = output<Workspace>();
  onSignOut = output<Workspace>();

  computedClass = computed(() => hlm('flex flex-col', this.isCompact() ? 'gap-3' : 'gap-1'));
}
