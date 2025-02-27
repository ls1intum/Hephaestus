import { HttpClient } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { MetaService } from '@app/core/modules/openapi';
@Component({
  selector: 'app-about',
  imports: [HlmAvatarModule],
  templateUrl: './about.component.html'
})
export class AboutComponent {
  http = inject(HttpClient);
  metaService = inject(MetaService);

  query = injectQuery(() => ({
    queryKey: ['contributors'],
    queryFn: async () => lastValueFrom(this.metaService.getContributors()),
    gcTime: Infinity
  }));

  projectManager = computed(() => {
    const data = this.query.data();
    if (!data) {
      return undefined;
    }
    // 5898705 is the id of the project manager Felix T.J. Dietrich
    return data.find((contributor) => contributor.id === 5898705);
  });

  contributors = computed(() => {
    const data = this.query.data();
    if (!data) {
      return undefined;
    }
    return data.filter((contributor) => contributor.id !== 5898705 && !contributor.login.includes('[bot]'));
  });
}
