import { ipcRenderer } from 'electron';

const statusElement = document.getElementById('status') as HTMLParagraphElement;
const usernameInput = document.getElementById('username') as HTMLInputElement;
const launchButton = document.getElementById('launch') as HTMLButtonElement;

async function updateStatus() {
  try {
    const status = await ipcRenderer.invoke('get-server-status');
    statusElement.textContent = `Online: ${status.online}, Players: ${status.players}`;
  } catch (error) {
    statusElement.textContent = 'Unable to fetch status';
  }
}

launchButton.addEventListener('click', async () => {
  const username = usernameInput.value.trim();
  if (!username) {
    alert('Please enter a username');
    return;
  }
  await ipcRenderer.invoke('launch-game', username);
  alert('Launching Minecraft...');
});

updateStatus();