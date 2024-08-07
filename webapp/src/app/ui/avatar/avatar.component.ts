import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import type { ClassValue } from 'clsx';
import type { VariantProps } from 'class-variance-authority';
import { cn } from 'app/utils';
import { cva } from 'app/storybook.helper';
import { NgOptimizedImage } from '@angular/common';

const [avatarVariants, args, argTypes] = cva('relative flex shrink-0 overflow-hidden rounded-full', {
  variants: {
    size: {
      default: 'h-10 w-10',
      sm: 'h-6 w-6 text-xs',
      lg: 'h-14 w-14 text-lg'
    }
  },
  defaultVariants: {
    size: 'default'
  }
});

export { args, argTypes };

interface AvatarVariants extends VariantProps<typeof avatarVariants> {}

@Component({
  selector: 'app-avatar',
  standalone: true,
  imports: [NgOptimizedImage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './avatar.component.html'
})
export class AppAvatarComponent {
  class = input<ClassValue>('');
  size = input<AvatarVariants['size']>('default');

  src = input<string>('');
  alt = input<string>('');
  imageClass = input<string>('');
  fallback = input<string>('https://placehold.co/56');

  canShow = signal(true);

  onError = () => {
    this.canShow.set(false);
  };

  computedClass = computed(() => cn(avatarVariants({ size: this.size() }), this.class()));

  computedSrc = computed(() => (this.canShow() ? this.src() : this.fallback()));

  computedImageClass = computed(() => cn('aspect-square object-cover h-full w-full', this.imageClass()));
}
