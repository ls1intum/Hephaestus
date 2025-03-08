<script lang="ts">
  // Add these PKCE helper functions
  function generateCodeVerifier(length = 64) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    let result = '';
    const values = crypto.getRandomValues(new Uint8Array(length));
    for (let i = 0; i < length; i++) {
      result += chars[values[i] % chars.length];
    }
    return result;
  }
  
  async function generateCodeChallenge(codeVerifier: string) {
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    const hash = await crypto.subtle.digest('SHA-256', data);
    
    // Base64 URL encode the hash
    return btoa(String.fromCharCode(...new Uint8Array(hash)))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  }
  
  // Storage for PKCE values
  let codeVerifier = '';

  async function handleLogin() {
    // Generate and store PKCE code verifier
    codeVerifier = generateCodeVerifier();
    // Generate the code challenge from the verifier
    const codeChallenge = await generateCodeChallenge(codeVerifier);
    
    // Create the authorization URL manually instead of using Keycloak's createLoginUrl
    // This avoids CSP issues as we'll direct the user to the Keycloak page directly
    const authUrl = new URL('http://localhost:8081/realms/hephaestus/protocol/openid-connect/auth');
    
    // Add required OAuth parameters
    authUrl.searchParams.append('client_id', 'hephaestus-extension');
    authUrl.searchParams.append('redirect_uri', 'https://mhmfgciagniefgadcmhbmliodjbfblbg.chromiumapp.org');
    authUrl.searchParams.append('response_type', 'code');
    authUrl.searchParams.append('scope', 'openid');
    
    // Add PKCE parameters
    authUrl.searchParams.append('code_challenge', codeChallenge);
    authUrl.searchParams.append('code_challenge_method', 'S256');
    
    // Add a state parameter for security
    const state = crypto.randomUUID();
    authUrl.searchParams.append('state', state);
    
    console.log('Authorization URL:', authUrl.toString());
    
    chrome.identity.launchWebAuthFlow({
      url: authUrl.toString(),
      interactive: true
    }, async (redirectUrl) => {
      if (redirectUrl) {
        console.log('Redirect URL:', redirectUrl);
        await getTokenFromAuthCode(redirectUrl, codeVerifier);
      } else {
        console.error('Authentication failed:', chrome.runtime.lastError);
      }
    });
  }

  async function getTokenFromAuthCode(authUrl: string, codeVerifier: string) {
    try {
      // Extract the code from the URL query parameters, not the hash fragment
      const url = new URL(authUrl);
      const code = url.searchParams.get('code') || new URLSearchParams(url.hash.substring(1)).get('code');
      
      if (!code) {
        console.error('No authorization code found in the URL:', authUrl);
        return;
      }
      
      console.log('Retrieved code:', code);
      
      // Prepare token exchange request
      const tokenEndpoint = 'http://localhost:8081/realms/hephaestus/protocol/openid-connect/token';
      
      const details = {
        'grant_type': 'authorization_code',
        'client_id': 'hephaestus-extension',
        'code': code,
        'redirect_uri': 'https://mhmfgciagniefgadcmhbmliodjbfblbg.chromiumapp.org',
        'code_verifier': codeVerifier
      };
      
      // Convert details to URL-encoded form data
      const formBody = Object.entries(details)
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        .join('&');
      
      // Make the token request
      const response = await fetch(tokenEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: formBody
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Token request failed: ${response.status} ${response.statusText}\n${errorText}`);
      }
      
      // Parse the response
      const tokenData = await response.json();
      console.log('Token data received:', tokenData);
      
      // Decode and log the JWT payload
      if (tokenData.access_token) {
        const tokenParts = tokenData.access_token.split('.');
        if (tokenParts.length === 3) {
          const payload = JSON.parse(atob(tokenParts[1]));
          console.log('Token payload:', payload);
        }
        
        // Store tokens for future use (consider using chrome.storage instead)
        // localStorage.setItem('access_token', tokenData.access_token);
        // localStorage.setItem('refresh_token', tokenData.refresh_token);
        
        return tokenData;
      }
    } catch (error) {
      console.error('Error exchanging code for token:', error);
    }
  }

  async function handleClick() {  
    try {
      let [tab] = await chrome.tabs.query({
        active: true,
        currentWindow: true
      });
      
      if (tab?.id) {
        chrome.scripting.executeScript({
          target: { tabId: tab.id },
          func: () => {
            document.body.style.backgroundColor = 'red';
          }
        });
      }
    } catch (err) {
      console.log(err);
    }
  }
</script>

<h1>Welcome to SvelteKit</h1>

<button on:click={handleClick}>
  Change Background
</button>
<button on:click={handleLogin} class="cursor-pointer">
  Login
</button>
