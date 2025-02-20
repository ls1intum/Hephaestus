import { Component, computed, input, output } from '@angular/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideHammer } from '@ng-icons/lucide';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { BrnSheetTriggerDirective } from '@spartan-ng/brain/sheet';
import { hlm } from '@spartan-ng/brain/core';

@Component({
  selector: 'app-workspace-thumb',
  imports: [BrnSheetTriggerDirective, HlmAvatarModule, NgIconComponent],
  providers: [provideIcons({ lucideHammer })],
  template: `
    <button [class]="computedClass()" (click)="handleClick($event)" brnSheetTrigger>
      <hlm-avatar [variant]="variant()" shape="square">
        <img [src]="iconUrl()" hlmAvatarImage />
        <span class="inset-2 rounded-md" hlmAvatarFallback>
          <ng-icon name="lucideHammer" class="size-6" />
        </span>
      </hlm-avatar>
    </button>
  `
})
export class WorkspaceThumbComponent {
  variant = input<'small' | 'base' | 'medium'>('base');
  isSelected = input<boolean>();
  hoverRingEnabled = input<boolean>(true);
  iconUrl = input<string>();
  select = output<MouseEvent>();

  computedClass = computed(() => {
    return hlm(
      'block rounded-md ring-offset-background ring-offset-2 duration-200 transition-all',
      this.isSelected() ? 'ring-2 ring-primary' : this.hoverRingEnabled() && 'hover:ring-2 hover:ring-muted-foreground/50'
    );
  });

  handleClick(event: MouseEvent) {
    this.select.emit(event);
  }
}
