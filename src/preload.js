const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('znClient', {
  initialState: () => ipcRenderer.invoke('app:initial-state'),
  saveSettings: (settings) => ipcRenderer.invoke('settings:save', settings),
  addAccount: () => ipcRenderer.invoke('account:add'),
  selectAccount: (accountId) => ipcRenderer.invoke('account:select', accountId),
  logout: (accountId) => ipcRenderer.invoke('account:logout', accountId),
  launch: (selection) => ipcRenderer.invoke('game:launch', selection),
  cancelLaunch: () => ipcRenderer.invoke('game:cancel'),
  openGameFolder: (version) => ipcRenderer.invoke('app:open-game-folder', version),
  openGameLocation: (location, version) => ipcRenderer.invoke('app:open-game-location', location, version),
  listMods: (version) => ipcRenderer.invoke('mods:list', version),
  onStatus: (callback) => ipcRenderer.on('launch:status', (_event, data) => callback(data)),
  onAccountChanged: (callback) => ipcRenderer.on('account:changed', (_event, data) => callback(data)),
  onGameStarted: (callback) => ipcRenderer.on('game:started', (_event, data) => callback(data)),
  onGameClosed: (callback) => ipcRenderer.on('game:closed', (_event, data) => callback(data)),
  onGameLog: (callback) => ipcRenderer.on('game:log', (_event, data) => callback(data)),
});
