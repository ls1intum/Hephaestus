import { Component, computed, input } from '@angular/core';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { hlm } from '@spartan-ng/brain/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideCirclePlus, lucidePlus } from '@ng-icons/lucide';

@Component({
  selector: 'app-workspace-add-button',
  imports: [HlmButtonDirective, NgIconComponent],
  providers: [provideIcons({ lucideCirclePlus, lucidePlus })],
  template: `
    <button hlmBtn variant="ghost" [class]="computedClass()" [size]="isCompact() ? 'icon' : 'default'">
      <ng-icon [name]="isCompact() ? 'lucidePlus' : 'lucideCirclePlus'" class="size-5" />
      @if (!isCompact()) {
        Add a Workspace
      }
    </button>
  `
})
export class WorkspaceAddButtonComponent {
  isCompact = input.required<boolean>();

  computedClass = computed(() => hlm(this.isCompact() ? '' : 'flex gap-2 w-full justify-start'));
}
