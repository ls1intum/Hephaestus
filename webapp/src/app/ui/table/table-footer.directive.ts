import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Directive({
  selector: 'tfoot[appTableFooter]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableFooterDirective {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('border-t bg-muted/50 font-medium [&>tr]:last:border-b-0', this.class()));
}
