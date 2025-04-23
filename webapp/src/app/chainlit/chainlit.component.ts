import { Component, AfterViewInit, ViewChild, ElementRef } from '@angular/core';

@Component({
  selector: 'app-chainlit',
  imports: [],
  template: `
    <div class="chainlit-container">
      <iframe #chainlitFrame src="http://localhost:8000/chainlit" width="100%" height="100%" frameborder="0" allow="clipboard-write"></iframe>
    </div>
  `,
  styles: `
    .chainlit-container {
      width: 100%;
      height: calc(100dvh - 56px - 96px);
      overflow: hidden;
    }
  `
})
export class ChainlitComponent implements AfterViewInit {
  @ViewChild('chainlitFrame') chainlitFrame!: ElementRef<HTMLIFrameElement>;

  sendMessage() {
    const iframeWindow = this.chainlitFrame.nativeElement.contentWindow;
    if (iframeWindow) {
      // Send a message to the iframe
      iframeWindow.postMessage({ type: 'send_message', content: 'Hello from Angular!' }, '*');
    }
  }

  setLightTheme() {
    this.sendThemeMessage('light');
  }

  setDarkTheme() {
    this.sendThemeMessage('dark');
  }

  sendThemeMessage(theme: 'light' | 'dark' | 'system') {
    const iframeWindow = this.chainlitFrame.nativeElement.contentWindow;
    if (iframeWindow) {
      // Send theme message in the format the custom.js expects
      iframeWindow.postMessage(`theme:${theme}`, '*');
    }
  }

  getAccessToken(): string | null {
    // Extract the access_token from cookies
    const cookieArray = document.cookie.split(';');
    const accessTokenCookie = cookieArray.find((cookie) => cookie.trim().startsWith('access_token='));
    if (accessTokenCookie) {
      return accessTokenCookie.trim().substring('access_token='.length);
    }
    return null;
  }

  sendAccessToken() {
    const accessToken = this.getAccessToken();
    if (accessToken) {
      const iframeWindow = this.chainlitFrame.nativeElement.contentWindow;
      if (iframeWindow) {
        // Send access token to the iframe
        iframeWindow.postMessage(`access_token:${accessToken}`, '*');
      }
    } else {
      console.warn('No access token found in cookies');
    }
  }

  ngAfterViewInit() {
    // Set up a listener for when the iframe loads
    this.chainlitFrame.nativeElement.addEventListener('load', () => {
      try {
        // Small delay to ensure custom.js is loaded
        setTimeout(() => {
          // Initial theme setting
          this.sendThemeMessage('dark');
          // Send access token
          this.sendAccessToken();
        }, 1000);
      } catch (error) {
        console.error('Failed to initialize iframe:', error);
      }
    });
  }
}
