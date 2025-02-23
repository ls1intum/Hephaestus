import { Component, computed, input, output } from '@angular/core';
import { WorkspaceOptionComponent } from '../workspace-option/workspace-option.component';
import { hlm } from '@spartan-ng/brain/core';

type Workspace = {
  id: string;
  title: string;
  iconUrl: string;
};

@Component({
  selector: 'app-workspace-option-selector',
  imports: [WorkspaceOptionComponent],
  template: `
    <div [class]="computedClass()">
      @for (workspace of workspaces(); track workspace.id) {
        <app-workspace-option
          [isCompact]="isCompact()"
          [isSelected]="selectedWorkspace().id === workspace.id"
          [iconUrl]="workspace.iconUrl"
          [title]="workspace.title"
          (select)="select.emit(workspace)"
          (signOut)="signOut.emit(workspace)"
        />
      }
    </div>
  `
})
export class WorkspaceOptionSelectorComponent {
  isCompact = input.required<boolean>();
  selectedWorkspace = input.required<Workspace>();
  workspaces = input.required<Workspace[]>();

  select = output<Workspace>();
  signOut = output<Workspace>();

  computedClass = computed(() => hlm('flex flex-col', this.isCompact() ? 'gap-3' : 'gap-1'));
}
