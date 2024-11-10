import { ChangeDetectionStrategy, Component, Input, ViewEncapsulation, computed, input, signal } from '@angular/core';
import { BrnAvatarComponent } from '@spartan-ng/ui-avatar-brain';
import { hlm } from '@spartan-ng/ui-core';
import { type VariantProps, cva } from 'class-variance-authority';
import type { ClassValue } from 'clsx';

export const avatarVariants = cva('relative flex shrink-0 overflow-hidden', {
  variants: {
    variant: {
      small: 'h-6 w-6 text-xs',
      base: 'h-7 w-7 text-sm',
      medium: 'h-10 w-10',
      large: 'h-14 w-14 text-lg',
      extralarge: 'h-32 w-32 lg:h-40 lg:w-40 text-xl md:text-3xl'
    },
    shape: {
      circle: 'rounded-full',
      square: 'rounded-md'
    }
  },
  defaultVariants: {
    variant: 'medium',
    shape: 'circle'
  }
});

type AvatarVariants = VariantProps<typeof avatarVariants>;

@Component({
  selector: 'hlm-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  standalone: true,
  host: {
    '[class]': '_computedClass()'
  },
  template: `
    @if (image?.canShow()) {
      <ng-content select="[hlmAvatarImage],[brnAvatarImage]" />
    } @else {
      <ng-content select="[hlmAvatarFallback],[brnAvatarFallback]" />
    }
  `
})
export class HlmAvatarComponent extends BrnAvatarComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() => hlm(avatarVariants({ variant: this._variant(), shape: this._shape() }), this.userClass()));

  private readonly _variant = signal<AvatarVariants['variant']>('medium');
  private readonly _shape = signal<AvatarVariants['shape']>('circle');

  @Input()
  set variant(variant: AvatarVariants['variant']) {
    this._variant.set(variant);
  }

  @Input()
  set shape(shape: AvatarVariants['shape']) {
    this._shape.set(shape);
  }
}
