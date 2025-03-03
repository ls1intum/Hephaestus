import { Component, computed, input, output } from '@angular/core';
import { WorkspaceThumbComponent } from '../workspace-thumb/workspace-thumb.component';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideEllipsis, lucideLogOut } from '@ng-icons/lucide';
import { hlm } from '@spartan-ng/brain/core';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/brain/tooltip';
import { BrnContextMenuTriggerDirective, BrnMenuTriggerDirective } from '@spartan-ng/brain/menu';
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
    NgIconComponent
  ],
  providers: [provideIcons({ lucideEllipsis, lucideLogOut })],
  templateUrl: './workspace-option.component.html'
})
export class WorkspaceOptionComponent {
  isCompact = input.required<boolean>();
  isSelected = input<boolean>();
  iconUrl = input<string>();
  title = input.required<string>();

  select = output<void>();
  signOut = output<void>();

  computedClass = computed(() => hlm('flex items-center gap-2 hover:bg-accent/50 p-3 rounded-xl cursor-pointer duration-300 transition-all', this.isSelected() && 'bg-accent'));
}
