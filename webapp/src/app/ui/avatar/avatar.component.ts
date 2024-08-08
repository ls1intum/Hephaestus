import { Component, computed, inject, InjectionToken, input, signal } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from 'app/utils';

const AvatarToken = new InjectionToken<AvatarComponent>('AvatarToken');

export function injectAvatar(): AvatarComponent {
  return inject(AvatarToken);
}

type ImageLoadingStatus = 'idle' | 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `<ng-content />`,
  providers: [{ provide: AvatarToken, useExisting: AvatarComponent }],
  host: {
    '[class]': 'computedClass()'
  }
})
export class AvatarComponent {
  readonly state = signal<ImageLoadingStatus>('idle');

  class = input<ClassValue>();
  computedClass = computed(() => cn('relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full', this.class()));

  setState(state: ImageLoadingStatus): void {
    this.state.set(state);
  }
}
