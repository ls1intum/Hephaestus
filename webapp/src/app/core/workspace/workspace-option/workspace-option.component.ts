import { Component, computed, input, output } from '@angular/core';
import { WorkspaceThumbComponent } from '../workspace-thumb/workspace-thumb.component';
import { LucideAngularModule, Ellipsis, LogOut } from 'lucide-angular';
import { hlm } from '@spartan-ng/ui-core';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { BrnContextMenuTriggerDirective, BrnMenuTriggerDirective } from '@spartan-ng/ui-menu-brain';
import { HlmMenuComponent, HlmMenuGroupComponent, HlmMenuItemDirective, HlmMenuItemIconDirective } from '@spartan-ng/ui-menu-helm';

@Component({
  selector: 'app-workspace-option',
  imports: [
    HlmTooltipComponent,
    BrnContextMenuTriggerDirective,
    HlmTooltipTriggerDirective,
    BrnTooltipContentDirective,
    BrnMenuTriggerDirective,
    HlmMenuComponent,
    HlmMenuItemDirective,
    HlmMenuItemIconDirective,
    HlmMenuGroupComponent,
    WorkspaceThumbComponent,
    LucideAngularModule
  ],
  templateUrl: './workspace-option.component.html'
})
export class WorkspaceOptionComponent {
  protected Ellipsis = Ellipsis;
  protected LogOut = LogOut;

  isCompact = input.required<boolean>();
  isSelected = input<boolean>();
  iconUrl = input<string>();
  title = input.required<string>();

  select = output<void>();
  signOut = output<void>();

  computedClass = computed(() => hlm('flex items-center gap-2 hover:bg-accent/50 p-3 rounded-xl cursor-pointer duration-300 transition-all', this.isSelected() && 'bg-accent'));
}
