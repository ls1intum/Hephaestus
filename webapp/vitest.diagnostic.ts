import { PlaywrightBrowserProvider } from '@vitest/browser-playwright';
import { defineBrowserProvider } from '@vitest/browser';
import config from './vitest.config.storybook';
class DiagnosticProvider extends PlaywrightBrowserProvider {
 async openPage(...args: Parameters<PlaywrightBrowserProvider['openPage']>) {
  await super.openPage(...args);
  const page = this.getPage(args[0]);
  page.on('requestfailed', request => console.log('FAILED REQUEST', request.url(), request.failure()));
  page.on('response', response => { if(response.status() >= 400) console.log('HTTP ERROR', response.status(), response.url()); });
 }
}
export default {...config, test: {...config.test, browser: {...config.test?.browser, provider: defineBrowserProvider({name:'playwright', supportedBrowser:['chromium'], options:{}, providerFactory: project => new DiagnosticProvider(project,{contextOptions:{reducedMotion:'reduce'}})})}}};
