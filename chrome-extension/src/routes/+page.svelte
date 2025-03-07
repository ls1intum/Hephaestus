<script lang="ts">
  let message = 'Hello, world!';

  async function handleClick() {
    message = 'Button clicked!';

    let [tab] = await chrome.tabs.query({
      active: true,
      currentWindow: true
    });
    chrome.scripting.executeScript({
      target: { tabId: tab.id! },
      func: () => {
        document.body.style.backgroundColor = 'red';
      }
    });

  }
</script>

<h1>Welcome to SvelteKit</h1>

<button on:click={handleClick}>
  Click me
</button>
<p>{message}</p>
