import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Directive({
  selector: 'caption[appTableCaption]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableCaptionDirective {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('mt-4 text-sm text-muted-foreground', this.class()));
}
