import { Directive, Input, computed, input, signal } from '@angular/core';
import { hlm } from '@spartan-ng/ui-core';
import { type VariantProps, cva } from 'class-variance-authority';
import type { ClassValue } from 'clsx';

export const cardContentVariants = cva('pt-0', {
  variants: {
    variant: {
      default: 'p-6',
      profile: 'flex flex-col gap-2'
    }
  },
  defaultVariants: {}
});
export type CardContentVariants = VariantProps<typeof cardContentVariants>;

@Directive({
  selector: '[hlmCardContent]',
  standalone: true,
  host: {
    '[class]': '_computedClass()'
  }
})
export class HlmCardContentDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected _computedClass = computed(() => hlm(cardContentVariants({ variant: this._variant() }), this.userClass()));

  private readonly _variant = signal<CardContentVariants['variant']>('default');
  @Input()
  set variant(variant: CardContentVariants['variant']) {
    this._variant.set(variant);
  }
}
