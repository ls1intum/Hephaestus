import { Component, isDevMode } from '@angular/core';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HeaderComponent } from '@app/core/header/header.component';
import { FooterComponent } from './core/footer/footer.component';
import { Title, Meta } from '@angular/platform-browser';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AngularQueryDevtools, HeaderComponent, FooterComponent],
  providers: [Meta],
  templateUrl: './app.component.html',
  styles: []
})
export class AppComponent {
  title = 'Hephaestus';

  constructor(
    private titleService: Title,
    private metaService: Meta,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  isDevMode() {
    return isDevMode();
  }

  ngOnInit() {
    this.router.events.subscribe((e) => {
      console.log('router event', e);
      this.updateTitle();
      this.updateMeta();
    });
  }

  // Update document title
  updateTitle() {
    const snapshot = this.route.snapshot;
    const id = snapshot.paramMap.get('id');
    const titleParam = id ?? this.titleService.getTitle();
    const title = titleParam ? `${titleParam} | Hephaestus` : 'Hephaestus';
    this.titleService.setTitle(title);
  }

  updateMeta() {
    const snapshot = this.route.snapshot.firstChild;
    const title = this.titleService.getTitle();
    const metaTags = snapshot?.firstChild?.data['meta'];
    console.log('metaTags', snapshot);
    if (metaTags) {
      const iconURL = 'https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer.svg';
      this.metaService.updateTag({ name: 'description', content: metaTags.description });
      this.metaService.updateTag({ property: 'og:type', content: 'website' });
      this.metaService.updateTag({ property: 'og:title', content: title });
      this.metaService.updateTag({ property: 'og:description', content: metaTags.description });
      this.metaService.updateTag({ property: 'og:image', content: iconURL });
      this.metaService.updateTag({ property: 'og:image:width', content: '67' });
      this.metaService.updateTag({ property: 'og:image:height', content: '60' });
      this.metaService.updateTag({ property: 'og:url', content: 'https://hephaestus.ase.cit.tum.de' + (snapshot.url.pop()?.path ?? '') });

      // this.metaService.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
      this.metaService.updateTag({ name: 'twitter:title', content: title });
      this.metaService.updateTag({ name: 'twitter:description', content: metaTags.description });
      this.metaService.updateTag({ name: 'twitter:image', content: iconURL });
      this.metaService.updateTag({ name: 'twitter:image:width', content: '67' });
      this.metaService.updateTag({ name: 'twitter:image:height', content: '60' });
    }
  }
}
