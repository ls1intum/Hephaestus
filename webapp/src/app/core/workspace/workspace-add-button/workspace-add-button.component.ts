import { Component, computed, input } from '@angular/core';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { hlm } from '@spartan-ng/ui-core';
import { LucideAngularModule, CirclePlus, Plus } from 'lucide-angular';

@Component({
  selector: 'app-workspace-add-button',
  standalone: true,
  imports: [HlmButtonDirective, LucideAngularModule],
  template: `
    <button hlmBtn variant="ghost" [class]="computedClass()" [size]="isCompact() ? 'icon' : 'default'">
      <lucide-angular [img]="isCompact() ? Plus : CirclePlus" class="size-5" />
      @if (!isCompact()) {
        Add a Workspace
      }
    </button>
  `
})
export class WorkspaceAddButtonComponent {
  protected CirclePlus = CirclePlus;
  protected Plus = Plus;

  isCompact = input.required<boolean>();

  computedClass = computed(() => hlm(this.isCompact() ? '' : 'flex gap-2 w-full justify-start'));
}
