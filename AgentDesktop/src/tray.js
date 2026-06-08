// Gestionnaire du system tray - icône dans la barre des tâches
const { Tray, Menu, nativeImage, app } = require('electron');
const path = require('path');
const config = require('./config');

let tray = null;
let onShowLogin = null;
let onQuit = null;

function create(callbacks) {
  onShowLogin = callbacks.onShowLogin;
  onQuit = callbacks.onQuit;
  
  // Créer une icône simple (16x16 bleu)
  const icon = createTrayIcon();
  tray = new Tray(icon);
  
  tray.setToolTip('Antigone RH Agent');
  updateMenu();
  
  tray.on('click', () => {
    if (config.isLoggedIn()) {
      // Recalculer le menu juste avant l'ouverture pour refléter
      // l'etat de pointage le plus recent.
      updateMenu();
      tray.popUpContextMenu();
    } else {
      // Ouvrir la fenêtre de login
      if (onShowLogin) onShowLogin();
    }
  });
  
  console.log('[Tray] Icône système créée');
}

function updateMenu() {
  if (!tray) return;
  
  const isLoggedIn = config.isLoggedIn();
  const username = config.get('username') || '';
  const clockedIn = config.get('clockedIn') || false;
  
  const menuTemplate = [];
  
  // Header
  menuTemplate.push({
    label: 'Antigone RH Agent v1.0',
    enabled: false
  });
  menuTemplate.push({ type: 'separator' });
  
  if (isLoggedIn) {
    menuTemplate.push({
      label: `👤 Connecté: ${username}`,
      enabled: false
    });
    menuTemplate.push({
      label: clockedIn ? '🟢 Pointé (En service)' : '🔴 Non pointé',
      enabled: false
    });
    menuTemplate.push({ type: 'separator' });
    menuTemplate.push({
      label: '🔄 Rafraîchir config',
      click: async () => {
        const api = require('./api');
        await api.fetchConfig();
        updateMenu();
      }
    });
  } else {
    menuTemplate.push({
      label: '🔑 Se connecter',
      click: () => {
        if (onShowLogin) onShowLogin();
      }
    });
  }
  
  menuTemplate.push({ type: 'separator' });
  menuTemplate.push({
    label: '❌ Quitter',
    click: () => {
      if (onQuit) onQuit();
    }
  });
  
  const contextMenu = Menu.buildFromTemplate(menuTemplate);
  tray.setContextMenu(contextMenu);
}

// Mettre à jour le tooltip
function setStatus(status) {
  if (tray) {
    tray.setToolTip(`Antigone RH Agent - ${status}`);
  }
}

// Créer une icône tray adaptée à chaque plateforme
function createTrayIcon() {
  const platform = process.platform;
  const size = platform === 'darwin' ? 22 : 16;
  const buffer = Buffer.alloc(size * size * 4);
  const cx = size / 2, cy = size / 2, r = size / 2 - 1;

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const idx = (y * size + x) * 4;
      const dist = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2);
      if (dist <= r) {
        if (platform === 'darwin') {
          // macOS : icône template blanche (le système adapte dark/light)
          buffer[idx] = 255; buffer[idx + 1] = 255; buffer[idx + 2] = 255;
        } else {
          // Windows : bleu Antigone
          buffer[idx] = 25; buffer[idx + 1] = 118; buffer[idx + 2] = 210;
        }
        buffer[idx + 3] = 255;
      } else {
        buffer[idx + 3] = 0;
      }
    }
  }

  const icon = nativeImage.createFromBuffer(buffer, { width: size, height: size });
  if (platform === 'darwin') {
    icon.setTemplateImage(true); // Permet l'adaptation dark/light macOS
  }
  return icon;
}

function destroy() {
  if (tray) {
    tray.destroy();
    tray = null;
  }
}

module.exports = { create, updateMenu, setStatus, destroy };
