import { Component, computed, input, OnDestroy, OnInit, signal } from '@angular/core';
import { cn } from 'app/utils';
import { ClassValue } from 'clsx';
import { injectAvatarConfig } from './avatar-config';
import { injectAvatar } from './avatar.component';

@Component({
  selector: 'app-avatar-fallback',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()',
    '[style.display]': 'visible() ? "flex" : "none"'
  }
})
export class AvatarFallbackComponent implements OnInit, OnDestroy {
  private readonly avatar = injectAvatar();
  private config = injectAvatarConfig();
  private delayElapsed = signal(false);
  private timeout: NodeJS.Timeout | null = null;

  class = input<ClassValue>();
  delayMs = input(this.config.delayMs);
  computedClass = computed(() => cn('absolute inset-0 flex items-center justify-center rounded-full bg-muted', this.class()));
  visible = computed(() => this.avatar.state() !== 'loaded' && this.delayElapsed());

  ngOnInit(): void {
    this.timeout = setTimeout(() => this.delayElapsed.set(true), this.delayMs());
  }

  ngOnDestroy(): void {
    if (this.timeout) {
      clearTimeout(this.timeout);
    }
  }
}
