import { HttpClient } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { AvatarComponent } from 'app/ui/avatar/avatar.component';
import { AvatarImageComponent } from 'app/ui/avatar/avatar-image.component';
import { AvatarFallbackComponent } from 'app/ui/avatar/avatar-fallback.component';
import { ButtonComponent } from 'app/ui/button/button.component';

interface Contributor {
  id: number;
  login: string;
  url: string;
  html_url: string;
  avatar_url: string;
}

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [AvatarComponent, AvatarImageComponent, AvatarFallbackComponent, ButtonComponent],
  templateUrl: './about.component.html'
})
export class AboutComponent {
  http = inject(HttpClient);

  query = injectQuery(() => ({
    queryKey: ['contributors'],
    queryFn: async () => lastValueFrom(this.http.get('https://api.github.com/repos/ls1intum/hephaestus/contributors')) as Promise<Contributor[]>,
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
    return data.filter((contributor) => contributor.id !== 5898705);
  });

  JSON = JSON;
}
