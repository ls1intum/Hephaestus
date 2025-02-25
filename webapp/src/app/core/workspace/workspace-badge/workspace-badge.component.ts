import { Component, input, output } from '@angular/core';
import { BrnSheetContentDirective } from '@spartan-ng/brain/sheet';
import { HlmSheetComponent, HlmSheetContentComponent, HlmSheetFooterComponent, HlmSheetHeaderComponent, HlmSheetTitleDirective } from '@spartan-ng/ui-sheet-helm';
import { HlmMenuSeparatorComponent } from '@spartan-ng/ui-menu-helm';
import { WorkspaceThumbComponent } from '../workspace-thumb/workspace-thumb.component';
import { WorkspaceOptionSelectorComponent } from '../workspace-option-selector/workspace-option-selector.component';
import { WorkspaceAddButtonComponent } from '../workspace-add-button/workspace-add-button.component';
import { NgScrollbarModule } from 'ngx-scrollbar';
import { HlmScrollAreaDirective } from '@spartan-ng/ui-scrollarea-helm';
type Workspace = {
  id: string;
  title: string;
  iconUrl: string;
};

@Component({
  imports: [
    BrnSheetContentDirective,
    HlmSheetComponent,
    HlmSheetContentComponent,
    HlmSheetHeaderComponent,
    HlmSheetFooterComponent,
    HlmSheetTitleDirective,
    HlmMenuSeparatorComponent,
    WorkspaceThumbComponent,
    WorkspaceOptionSelectorComponent,
    WorkspaceAddButtonComponent,
    NgScrollbarModule,
    HlmScrollAreaDirective,
    WorkspaceAddButtonComponent,
    HlmScrollAreaDirective
  ],
  templateUrl: './workspace-badge.component.html'
})
export class WorkspaceBadgeComponent {
  selectedWorkspace = input.required<Workspace>();
  workspaces = input.required<Workspace[]>();

  select = output<Workspace>();
  signOut = output<Workspace>();
}
