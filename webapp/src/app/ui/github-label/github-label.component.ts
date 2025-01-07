import { Component, computed, input } from '@angular/core';
import type { LabelInfo } from '@app/core/modules/openapi';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';

@Component({
    selector: 'app-github-label',
    imports: [HlmSkeletonModule],
    styleUrls: ['./github-label.component.scss'],
    host: {
        '[style.--label-r]': 'colors().r',
        '[style.--label-g]': 'colors().g',
        '[style.--label-b]': 'colors().b',
        '[style.--label-h]': 'colors().h',
        '[style.--label-s]': 'colors().s',
        '[style.--label-l]': 'colors().l'
    },
    template: `
    @if (isLoading()) {
      <hlm-skeleton class="w-14 h-6" />
    } @else {
      <span class="px-2 py-0.5 rounded-[2rem] text-xs font-medium dark:border gh-label">
        {{ label().name }}
      </span>
    }
  `
})
export class GithubLabelComponent {
  isLoading = input(false);
  label = input.required<LabelInfo>();

  protected colors = computed(() => this.hexToRgb(this.label().color ?? 'FFFFFF'));

  hexToRgb(hex: string) {
    if (hex.charAt(0) === '#') {
      hex = hex.slice(1);
    }
    const bigint = parseInt(hex, 16);
    const r = (bigint >> 16) & 255;
    const g = (bigint >> 8) & 255;
    const b = bigint & 255;

    const hsl = this.rgbToHsl(r, g, b);

    return {
      r: r,
      g: g,
      b: b,
      ...hsl
    };
  }

  rgbToHsl(r: number, g: number, b: number) {
    r /= 255;
    g /= 255;
    b /= 255;

    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    let h = 0,
      s = 0,
      l = (max + min) / 2;

    if (max !== min) {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      switch (max) {
        case r:
          h = (g - b) / d + (g < b ? 6 : 0);
          break;
        case g:
          h = (b - r) / d + 2;
          break;
        case b:
          h = (r - g) / d + 4;
          break;
      }
      h /= 6;
    }

    h = Math.round(h * 360);
    s = Math.round(s * 100);
    l = Math.round(l * 100);

    return { h, s, l };
  }
}
