import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/ui-core';
import type { ClassValue } from 'clsx';

export const hlmUl = 'my-6 ml-6 list-disc [&>li]:mt-2';

@Directive({
  selector: '[hlmUl]',
  standalone: true,
  host: {
    '[class]': '_computedClass()'
  }
})
export class HlmUlDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected _computedClass = computed(() => hlm(hlmUl, this.userClass()));
}
