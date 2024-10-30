import { Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { TitleStrategy, RouterStateSnapshot } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class TemplatePageTitleStrategy extends TitleStrategy {
  constructor(
    private readonly title: Title,
    private metaService: Meta
  ) {
    super();
  }
  override updateTitle(routerState: RouterStateSnapshot) {
    // Update document title
    const id = routerState.root.firstChild?.paramMap.get('id');
    const titleParam = id ?? this.buildTitle(routerState);
    const title = titleParam ? `${titleParam} | Hephaestus` : 'Hephaestus';
    this.title.setTitle(title);

    // Update meta tags
    const metaTags = routerState.root.firstChild?.data['meta'];
    if (metaTags) {
      const iconURL = 'https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer.svg';
      this.metaService.updateTag({ name: 'description', content: metaTags.description });
      this.metaService.updateTag({ property: 'og:type', content: 'website' });
      this.metaService.updateTag({ property: 'og:title', content: title });
      this.metaService.updateTag({ property: 'og:description', content: metaTags.description });
      this.metaService.updateTag({ property: 'og:image', content: iconURL });
      this.metaService.updateTag({ property: 'og:image:width', content: '67' });
      this.metaService.updateTag({ property: 'og:image:height', content: '60' });
      this.metaService.updateTag({ property: 'og:url', content: 'https://hephaestus.ase.cit.tum.de' + routerState.url });

      // this.metaService.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
      this.metaService.updateTag({ name: 'twitter:title', content: title });
      this.metaService.updateTag({ name: 'twitter:description', content: metaTags.description });
      this.metaService.updateTag({ name: 'twitter:image', content: iconURL });
      this.metaService.updateTag({ name: 'twitter:image:width', content: '67' });
      this.metaService.updateTag({ name: 'twitter:image:height', content: '60' });
    }
  }
}
