import { Directive, Input, computed, input, signal } from '@angular/core';
import { hlm } from '@spartan-ng/ui-core';
import { type VariantProps, cva } from 'class-variance-authority';
import type { ClassValue } from 'clsx';

export const cardVariants = cva('rounded-lg border border-border bg-card focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 text-card-foreground shadow-sm', {
  variants: {
    variant: {
      default: '',
      profile: 'shadow-md block p-4'
    }
  },
  defaultVariants: {}
});
export type CardVariants = VariantProps<typeof cardVariants>;

@Directive({
  selector: '[hlmCard]',
  standalone: true,
  host: {
    '[class]': '_computedClass()'
  }
})
export class HlmCardDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected _computedClass = computed(() => hlm(cardVariants({ variant: this._variant() }), this.userClass()));

  private readonly _variant = signal<CardVariants['variant']>('default');
  @Input()
  set variant(variant: CardVariants['variant']) {
    this._variant.set(variant);
  }
}
