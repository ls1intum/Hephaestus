import { Component, computed, input, output } from '@angular/core';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { hlm } from '@spartan-ng/ui-core';

@Component({
  selector: 'app-workspace-thumb',
  standalone: true,
  imports: [HlmAvatarModule, LucideAngularModule],
  template: `
    <button [class]="computedClass()" (click)="handleClick($event)">
      <hlm-avatar [variant]="variant()" shape="square">
        <img [src]="iconUrl()" hlmAvatarImage />
        <span class="inset-2 rounded-md" hlmAvatarFallback>
          <lucide-angular [img]="Hammer" class="size-8 sm:size-6" />
        </span>
      </hlm-avatar>
    </button>
  `
})
export class WorkspaceThumbComponent {
  protected Hammer = Hammer;

  variant = input<'base' | 'medium'>('base');
  isSelected = input<boolean>();
  hoverRingEnabled = input<boolean>(true);
  iconUrl = input<string>();
  onClick = output<MouseEvent>();

  computedClass = computed(() => {
    return hlm(
      'block rounded-md ring-offset-background ring-offset-2 duration-200 transition-all',
      this.isSelected() ? 'ring-2 ring-primary' : this.hoverRingEnabled() && 'hover:ring-2 hover:ring-muted-foreground/50'
    );
  });

  handleClick(event: MouseEvent) {
    this.onClick.emit(event);
  }
}
