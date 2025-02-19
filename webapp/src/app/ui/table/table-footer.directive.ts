import { Directive, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Directive({
  selector: 'tfoot[appTableFooter]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableFooterDirective {
  class = input<ClassValue>();
  computedClass = computed(() => cn('border-t bg-muted/50 font-medium [&>tr]:last:border-b-0', this.class()));
}
