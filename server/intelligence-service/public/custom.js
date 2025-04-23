(function() {
  console.log('Theme handler initialized');
  
  // Find React root element to force updates
  function findReactRoot() {
    // Look for common React root attributes
    return document.querySelector('[id="root"], [id="app"], [data-reactroot]') || document.body;
  }
  
  // More aggressive theme switch function
  function applyTheme(themeName) {
    console.log(`Applying theme: ${themeName}`);
    
    // Update localStorage first
    localStorage.setItem('vite-ui-theme', themeName);
    
    const root = window.document.documentElement;
    
    // Remove existing theme classes
    root.classList.remove('light', 'dark');
    
    // Determine the actual theme to apply
    let appliedTheme = themeName;
    if (themeName === 'system') {
      appliedTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    
    // Add the theme class
    root.classList.add(appliedTheme);
    
    // Apply theme variables if they exist
    if (window.theme && window.theme[appliedTheme]) {
      Object.entries(window.theme[appliedTheme]).forEach(([key, value]) => {
        root.style.setProperty(key, value);
      });
    }
    
    // Force React state synchronization
    try {
      // Dispatch storage event to inform other parts of the app
      window.dispatchEvent(new StorageEvent('storage', {
        key: 'vite-ui-theme',
        newValue: themeName,
        storageArea: localStorage
      }));
      
      // Try to find any buttons that might control the theme and click them
      setTimeout(() => {
        const themeButtons = Array.from(document.querySelectorAll('button'))
          .filter(btn => {
            const text = btn.textContent?.toLowerCase() || '';
            return text.includes(themeName) || 
                   (text.includes('theme') && (text.includes('dark') || text.includes('light')));
          });
        
        if (themeButtons.length > 0) {
          console.log('Found theme buttons, attempting to trigger them');
          themeButtons.forEach(btn => {
            // Create and dispatch a click event
            const clickEvent = new MouseEvent('click', {
              bubbles: true,
              cancelable: true,
              view: window
            });
            btn.dispatchEvent(clickEvent);
          });
        }
      }, 100);
      
      // Set a data attribute on the body for any CSS selectors to use
      document.body.setAttribute('data-theme', appliedTheme);
      
      // Force a UI refresh by temporarily adding a style and removing it
      const refreshStyle = document.createElement('style');
      refreshStyle.textContent = `body { opacity: 0.99 !important; }`;
      document.head.appendChild(refreshStyle);
      setTimeout(() => refreshStyle.remove(), 50);
      
    } catch (e) {
      console.error('Error during theme refresh:', e);
    }
  }

  // Function to set access token as a cookie
  function setAccessTokenCookie(token) {
    console.log('Setting access token cookie');
    
    // Set the access_token cookie with a reasonable expiration (e.g., 24 hours)
    const expirationDate = new Date();
    expirationDate.setTime(expirationDate.getTime() + (24 * 60 * 60 * 1000));
    
    // Set cookie with secure attributes if on https
    const secure = window.location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = `access_token=${token}; expires=${expirationDate.toUTCString()}; path=/; SameSite=Strict${secure}`;
    
    console.log('Access token cookie set successfully');
  }
  
  // Listen for system theme changes
  const darkModeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  darkModeMediaQuery.addEventListener('change', (e) => {
    if (localStorage.getItem('vite-ui-theme') === 'system') {
      applyTheme('system');
    }
  });
  
  // Listen for messages from parent window
  window.addEventListener('message', function(event) {
    console.log('Message received in Chainlit:', event.data);
    
    if (typeof event.data === 'string') {
      if (event.data.startsWith('theme:')) {
        const theme = event.data.split(':')[1];
        console.log('Setting theme to:', theme);
        applyTheme(theme);
      } else if (event.data.startsWith('access_token:')) {
        const token = event.data.split(':')[1];
        console.log('Received access token from parent');
        setAccessTokenCookie(token);
      }
    }
  });
})();