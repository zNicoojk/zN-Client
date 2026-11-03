const $ = (selector) => document.querySelector(selector);
const versionSelect = $('#version-select');
const memorySelect = $('#memory-select');
const playButton = $('#play-button');
const playLabel = $('#play-label');
const accountDialog = $('#account-dialog');
const logoutDialog = $('#logout-dialog');
const cancelButton = $('#cancel-installation');

let account = null;
let accounts = [];
let activeAccountId = null;
let pendingLogoutAccount = null;
let latestVersion = '';
let recommendedMemory = 2;
let launchPending = false;
let gameRunning = false;
let accountActionPending = false;
let modsLoadSequence = 0;
let currentStatus = { text: 'Conectando con Minecraft…', percent: 0 };
let gameLogLines = [];

function setStatus(text, percent = 0) {
  const message = String(text || '');
  currentStatus = { text: message, percent: Number(percent) || 0 };
  $('#status-text').textContent = message;
  $('#progress-fill').style.width = `${Math.max(0, Math.min(100, Number(percent) || 0))}%`;
  const busy = /descarg|prepar|instal|conect|abriendo|cancelando/i.test(message);
  $('#status-indicator').classList.toggle('is-busy', busy);
  renderDownloads();
}

function renderDownloads() {
  const stateTitle = $('#download-state-title');
  const stateCopy = $('#download-state-copy');
  const fill = $('#download-progress-fill');
  const track = $('.download-track');
  const cancel = $('#downloads-cancel');
  const percent = launchPending ? Math.max(0, Math.min(100, Number(currentStatus.percent) || 0)) : 0;

  if (launchPending) {
    stateTitle.textContent = 'Minecraft se está preparando';
    stateCopy.textContent = currentStatus.text;
    cancel.hidden = false;
    cancel.disabled = /cancelando/i.test(currentStatus.text);
  } else if (gameRunning) {
    stateTitle.textContent = 'Minecraft está en ejecución';
    stateCopy.textContent = 'El juego se inició correctamente. Puedes consultar su salida en Registros.';
    cancel.hidden = true;
  } else {
    stateTitle.textContent = 'Sin actividad';
    stateCopy.textContent = 'No hay una instalación o descarga en curso.';
    cancel.hidden = true;
  }

  $('#download-percent').textContent = `${percent} %`;
  fill.style.width = `${percent}%`;
  track.setAttribute('aria-valuenow', String(percent));
  $('#sidebar-status-label').textContent = launchPending ? 'Preparando Minecraft' : gameRunning ? 'Minecraft abierto' : 'Launcher listo';
  $('#sidebar-status-orb').classList.toggle('is-busy', launchPending);
}

function redactLogLine(value) {
  return String(value)
    .replace(/\b(access[_-]?token|refresh[_-]?token|client[_-]?token|xsts[_-]?token|authorization)\b\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s,;]+)/gi, '$1=[oculto]')
    .replace(/\bBearer\s+[A-Za-z0-9._~+\/-]{16,}={0,2}/gi, 'Bearer [oculto]')
    .replace(/\beyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\b/g, '[token oculto]');
}

function renderGameLogs() {
  const output = $('#game-log-output');
  const fragment = document.createDocumentFragment();
  if (!gameLogLines.length) {
    const empty = document.createElement('span');
    empty.className = 'log-empty';
    empty.textContent = 'Todavía no hay salida de Minecraft en esta sesión.';
    fragment.append(empty);
  } else {
    for (const line of gameLogLines) {
      const row = document.createElement('span');
      row.className = /\b(error|fatal|exception|failed)\b/i.test(line) ? 'log-line log-line-error' : 'log-line';
      row.textContent = line;
      fragment.append(row, document.createTextNode('\n'));
    }
  }
  output.replaceChildren(fragment);
  $('#logs-line-count').textContent = `${gameLogLines.length} ${gameLogLines.length === 1 ? 'línea' : 'líneas'}`;
  output.scrollTop = output.scrollHeight;
}

function renderPlayButton() {
  playButton.disabled = launchPending || gameRunning;
  if (launchPending) playLabel.textContent = 'INICIANDO…';
  else if (gameRunning) playLabel.textContent = 'MINECRAFT ABIERTO';
  else playLabel.textContent = account ? 'JUGAR AHORA' : 'ELEGIR CUENTA';
}

function renderSetupSummary() {
  $('#summary-version').textContent = versionSelect.value || latestVersion || '—';
  $('#summary-memory').textContent = `${memorySelect.value || 2} GB`;
  $('#hero-selected-version').textContent = versionSelect.value || latestVersion || '—';
  $('#installation-version').textContent = versionSelect.value || latestVersion || '—';
  $('#installation-memory').textContent = `${memorySelect.value || 2} GB`;
  document.querySelectorAll('.ram-preset-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.gb === String(memorySelect.value));
  });
}

function renderAvatar(element, profile, fallback = '＋') {
  const skin = profile?.skin;
  element.textContent = skin ? '' : profile?.name?.slice(0, 1).toUpperCase() || fallback;
  element.classList.toggle('has-skin', Boolean(skin));
  element.style.backgroundImage = skin ? `url("${skin}")` : '';
}

function renderAccount(nextAccount) {
  account = nextAccount || null;
  const label = $('#account-label');
  const avatar = $('#account-avatar');
  const panelAvatar = $('#panel-account-avatar');
  const panelCaption = $('#panel-account-caption');
  const panelName = $('#panel-account-name');
  const panelHint = $('#panel-account-hint');
  label.textContent = account ? account.name : accounts.length ? `Mis cuentas · ${accounts.length}` : 'Mis cuentas';
  renderAvatar(avatar, account);
  renderAvatar(panelAvatar, account);
  panelCaption.textContent = account ? 'SESIÓN ACTIVA' : accounts.length ? `${accounts.length} ${accounts.length === 1 ? 'CUENTA GUARDADA' : 'CUENTAS GUARDADAS'}` : 'INICIA SESIÓN PARA JUGAR';
  panelName.textContent = account ? account.name : accounts.length ? 'Elige una cuenta' : 'Sin cuenta activa';
  panelHint.textContent = account ? 'Tu sesión está protegida en este equipo.' : 'Agrega tu cuenta Microsoft para empezar.';
  renderPlayButton();
}

function renderAccounts() {
  const list = $('#account-list');
  list.replaceChildren();
  if (!accounts.length) {
    const empty = document.createElement('div');
    empty.className = 'account-empty';
    empty.textContent = 'Todavía no hay cuentas guardadas. Agrega una cuenta Microsoft para empezar a jugar.';
    list.append(empty);
  }

  for (const savedAccount of accounts) {
    const row = document.createElement('div');
    row.className = 'account-row';

    const choose = document.createElement('button');
    choose.className = 'account-choice';
    choose.type = 'button';
    choose.disabled = accountActionPending || gameRunning || launchPending;

    const avatar = document.createElement('span');
    avatar.className = 'account-avatar-small';
    renderAvatar(avatar, savedAccount);

    const copy = document.createElement('span');
    copy.className = 'account-choice-copy';
    const name = document.createElement('strong');
    name.textContent = savedAccount.name;
    const hint = document.createElement('small');
    hint.textContent = savedAccount.id === activeAccountId ? 'Sesión activa' : 'Selecciona para jugar';
    copy.append(name, hint);
    choose.append(avatar, copy);

    if (savedAccount.id === activeAccountId) {
      const badge = document.createElement('span');
      badge.className = 'account-active';
      badge.textContent = 'EN USO';
      choose.append(badge);
    }

    choose.addEventListener('click', async () => {
      if (savedAccount.id === activeAccountId && account) {
        accountDialog.close();
        return;
      }
      accountActionPending = true;
      renderAccounts();
      setStatus(`Abriendo la cuenta ${savedAccount.name}…`);
      try {
        const result = await window.znClient.selectAccount(savedAccount.id);
        if (result.ok) {
          applyAccountState(result);
          accountDialog.close();
          setStatus(`Sesión iniciada como ${result.account.name}.`);
        } else {
          setStatus(result.error);
        }
      } catch (error) {
        setStatus(error.message || 'No se pudo abrir esa cuenta.');
      } finally {
        accountActionPending = false;
        renderAccounts();
      }
    });

    const remove = document.createElement('button');
    remove.className = 'account-remove';
    remove.type = 'button';
    remove.textContent = 'Cerrar sesión';
    remove.disabled = accountActionPending || gameRunning || launchPending;
    remove.setAttribute('aria-label', `Cerrar sesión de ${savedAccount.name}`);
    remove.addEventListener('click', () => {
      pendingLogoutAccount = savedAccount;
      $('#logout-description').textContent = `¿Quieres quitar la cuenta ${savedAccount.name} de zN Client?`;
      logoutDialog.showModal();
    });

    row.append(choose, remove);
    list.append(row);
  }

  $('#add-account-button').disabled = accountActionPending || gameRunning || launchPending;
}

function applyAccountState(state) {
  accounts = state.accounts || [];
  activeAccountId = state.activeAccountId || null;
  renderAccount(state.account);
  renderAccounts();
}

function renderMods(mods) {
  const list = $('#mods-list');
  const count = $('#mods-count');
  list.replaceChildren();
  $('#mods-version-label').textContent = versionSelect.value || latestVersion || '—';
  count.textContent = `${mods.length} ${mods.length === 1 ? 'mod encontrado' : 'mods encontrados'}`;
  if (!mods.length) {
    const empty = document.createElement('div');
    empty.className = 'mods-empty';
    empty.textContent = 'Todavía no hay mods .jar en la carpeta de esta versión. Agrega tus archivos y actualiza la lista.';
    list.append(empty);
    return;
  }

  for (const mod of mods) {
    const card = document.createElement('article');
    card.className = 'mod-card';
    const icon = document.createElement('div');
    icon.className = 'mod-icon';
    if (mod.icon) {
      const image = document.createElement('img');
      image.src = mod.icon;
      image.alt = '';
      image.loading = 'lazy';
      icon.append(image);
    } else {
      icon.textContent = '✦';
      icon.setAttribute('aria-hidden', 'true');
    }

    const copy = document.createElement('div');
    copy.className = 'mod-copy';
    const fileName = document.createElement('small');
    fileName.textContent = mod.fileName;
    const name = document.createElement('h3');
    name.textContent = mod.name;
    copy.append(fileName, name);
    if (mod.version) {
      const version = document.createElement('span');
      version.className = 'mod-version';
      version.textContent = mod.version;
      copy.append(version);
    }
    if (mod.description) {
      const description = document.createElement('p');
      description.textContent = mod.description;
      copy.append(description);
    }
    card.append(icon, copy);
    list.append(card);
  }

  if (modSearchInput && modSearchInput.value.trim()) {
    modSearchInput.dispatchEvent(new Event('input'));
  }
}

async function loadMods() {
  const requestId = ++modsLoadSequence;
  $('#mods-version-label').textContent = versionSelect.value || latestVersion || '—';
  $('#mods-list').replaceChildren(Object.assign(document.createElement('div'), {
    className: 'mods-empty',
    textContent: 'Leyendo los archivos de mods…',
  }));
  $('#mods-count').textContent = 'Actualizando…';
  try {
    const mods = await window.znClient.listMods(versionSelect.value || latestVersion);
    if (requestId === modsLoadSequence) renderMods(mods);
  } catch (error) {
    if (requestId !== modsLoadSequence) return;
    $('#mods-count').textContent = 'No se pudo leer la carpeta';
    const message = document.createElement('div');
    message.className = 'mods-empty mods-error';
    message.textContent = error.message || 'No se pudo cargar la lista de mods.';
    $('#mods-list').replaceChildren(message);
    setStatus(message.textContent);
  }
}

function openAccountDialog() {
  renderAccounts();
  accountDialog.showModal();
}

async function saveSettings() {
  const button = $('#save-settings');
  const settings = { version: versionSelect.value || latestVersion, memory: Number(memorySelect.value) };
  button.disabled = true;
  try {
    const saved = await window.znClient.saveSettings(settings);
    if (saved?.memory) {
      settings.memory = saved.memory;
      memorySelect.value = String(saved.memory);
    }
    $('#settings-memory').value = String(settings.memory);
    renderSetupSummary();
    setStatus('Ajustes guardados.');
    button.innerHTML = 'Guardado <span aria-hidden="true">✓</span>';
    setTimeout(() => { button.innerHTML = 'Guardar ajustes <span aria-hidden="true">→</span>'; }, 1400);
  } catch (error) {
    setStatus(error.message || 'No se pudieron guardar los ajustes.');
  } finally {
    button.disabled = false;
  }
}

function showView(name) {
  const labels = { home: 'INICIO', installations: 'INSTALACIONES', mods: 'MODS', downloads: 'DESCARGAS', logs: 'REGISTROS', files: 'ARCHIVOS', settings: 'CONFIGURACIÓN' };
  document.querySelectorAll('.view').forEach((view) => view.classList.toggle('active', view.id === `${name}-view`));
  document.querySelectorAll('.nav-item[data-view]').forEach((item) => item.classList.toggle('active', item.dataset.view === name));
  $('#topbar-section').textContent = labels[name] || labels.home;
  if (name === 'mods') loadMods();
}

$('.nav-item[data-view="settings"]').addEventListener('click', () => {
  $('#settings-memory').value = memorySelect.value;
});
$('.brand').addEventListener('click', (event) => {
  event.preventDefault();
  showView('home');
});
document.querySelectorAll('.nav-item[data-view]').forEach((item) => item.addEventListener('click', () => showView(item.dataset.view)));
document.querySelectorAll('[data-open-view]').forEach((button) => button.addEventListener('click', () => showView(button.dataset.openView)));
$('#refresh-mods').addEventListener('click', loadMods);

$('#account-button').addEventListener('click', openAccountDialog);
$('#manage-accounts-button').addEventListener('click', openAccountDialog);
document.querySelectorAll('[data-close-dialog]').forEach((button) => {
  button.addEventListener('click', () => $(`#${button.dataset.closeDialog}`).close());
});

$('#add-account-button').addEventListener('click', async () => {
  accountActionPending = true;
  renderAccounts();
  setStatus('Abriendo el inicio de sesión de Microsoft…');
  try {
    const result = await window.znClient.addAccount();
    if (result.ok) {
      applyAccountState(result);
      setStatus(`Sesión iniciada como ${result.account.name}.`);
    } else {
      setStatus(result.error);
    }
  } catch (error) {
    setStatus(error.message || 'No se pudo agregar la cuenta.');
  } finally {
    accountActionPending = false;
    renderAccounts();
  }
});

$('#keep-account-button').addEventListener('click', () => {
  pendingLogoutAccount = null;
  logoutDialog.close();
});

$('#confirm-logout-button').addEventListener('click', async () => {
  if (!pendingLogoutAccount) return;
  const button = $('#confirm-logout-button');
  button.disabled = true;
  try {
    const result = await window.znClient.logout(pendingLogoutAccount.id);
    if (result.ok) {
      pendingLogoutAccount = null;
      applyAccountState(result);
      logoutDialog.close();
      setStatus('La cuenta se quitó de zN Client.');
    } else {
      setStatus(result.error);
    }
  } catch (error) {
    setStatus(error.message || 'No se pudo cerrar la sesión.');
  } finally {
    button.disabled = false;
  }
});

playButton.addEventListener('click', async () => {
  if (!account) {
    openAccountDialog();
    return;
  }
  launchPending = true;
  renderPlayButton();
  cancelButton.hidden = false;
  cancelButton.disabled = false;
  setStatus('Preparando Minecraft…', 0);
  const result = await window.znClient.launch({ version: versionSelect.value, memory: Number(memorySelect.value) });
  launchPending = false;
  cancelButton.hidden = true;
  if (!result.ok) {
    setStatus(result.error);
    renderPlayButton();
  }
});

cancelButton.addEventListener('click', async () => {
  await cancelLaunch();
});

async function cancelLaunch() {
  cancelButton.disabled = true;
  $('#downloads-cancel').disabled = true;
  setStatus('Cancelando instalación…');
  const result = await window.znClient.cancelLaunch();
  if (!result.ok) {
    cancelButton.hidden = true;
    $('#downloads-cancel').hidden = true;
  }
}

async function openVersionFolder() {
  const version = versionSelect.value || latestVersion;
  try {
    const result = await window.znClient.openGameFolder(version);
    if (result && !result.ok) throw new Error(result.error || 'No se pudo abrir la carpeta.');
    setStatus(`Carpeta de Minecraft ${version} abierta.`);
  } catch (error) {
    setStatus(error.message || 'No se pudo abrir la carpeta.');
  }
}

$('#downloads-cancel').addEventListener('click', cancelLaunch);

$('#installation-play').addEventListener('click', () => playButton.click());
$('#installation-folder').addEventListener('click', openVersionFolder);
$('#downloads-open-folder').addEventListener('click', openVersionFolder);
$('#installation-configure').addEventListener('click', () => {
  showView('home');
  versionSelect.focus();
});

$('#folder-button').addEventListener('click', openVersionFolder);
$('#launch-card-folder-btn')?.addEventListener('click', openVersionFolder);
$('#settings-folder').addEventListener('click', openVersionFolder);
$('#save-settings').addEventListener('click', saveSettings);
memorySelect.addEventListener('change', () => { $('#settings-memory').value = memorySelect.value; renderSetupSummary(); });
$('#settings-memory').addEventListener('change', () => { memorySelect.value = $('#settings-memory').value; renderSetupSummary(); });
versionSelect.addEventListener('change', () => {
  renderSetupSummary();
  if ($('#mods-view').classList.contains('active')) loadMods();
});

$('#use-recommended-memory').addEventListener('click', () => {
  const memory = [...memorySelect.options].find((option) => Number(option.value) === recommendedMemory)?.value;
  if (!memory) return;
  memorySelect.value = memory;
  $('#settings-memory').value = memory;
  renderSetupSummary();
  setStatus(`Memoria ajustada a ${memory} GB, recomendada para este equipo.`);
});

document.querySelectorAll('[data-open-location]').forEach((button) => {
  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      const result = await window.znClient.openGameLocation(button.dataset.openLocation, versionSelect.value);
      if (result && !result.ok) throw new Error(result.error || 'No se pudo abrir la carpeta.');
      const folderName = button.dataset.locationLabel || button.closest('.file-card')?.querySelector('h2')?.textContent || 'Minecraft';
      setStatus(`Carpeta de ${folderName} abierta.`);
    } catch (error) {
      setStatus(error.message || 'No se pudo abrir la carpeta.');
    } finally {
      button.disabled = false;
    }
  });
});

window.znClient.onStatus((status) => {
  setStatus(status.text, status.percent);
});
window.znClient.onAccountChanged((state) => applyAccountState(state));
window.znClient.onGameStarted(() => {
  gameRunning = true;
  launchPending = false;
  cancelButton.hidden = true;
  renderPlayButton();
  renderAccounts();
  renderDownloads();
});
window.znClient.onGameClosed(() => {
  gameRunning = false;
  launchPending = false;
  cancelButton.hidden = true;
  renderPlayButton();
  renderAccounts();
  renderDownloads();
});
window.znClient.onGameLog((line) => {
  const safeLine = redactLogLine(line).slice(0, 500);
  gameLogLines.push(safeLine);
  if (gameLogLines.length > 300) gameLogLines.shift();
  renderGameLogs();
  if (/error|exception|failed/i.test(safeLine)) setStatus(safeLine);
});

$('#clear-live-logs').addEventListener('click', () => {
  gameLogLines = [];
  renderGameLogs();
});

function showToast(message) {
  const existing = document.querySelector('.zn-toast');
  if (existing) existing.remove();
  const toast = document.createElement('div');
  toast.className = 'zn-toast';
  toast.innerHTML = `<span style="color: var(--accent); font-size: 14px;">✦</span> <span>${message}</span>`;
  document.body.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(8px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 2600);
}

// RAM Quick Presets
document.querySelectorAll('.ram-preset-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    const gb = btn.dataset.gb;
    if (!gb) return;
    memorySelect.value = gb;
    const settingsMem = $('#settings-memory');
    if (settingsMem) settingsMem.value = gb;
    renderSetupSummary();
    setStatus(`Memoria RAM ajustada a ${gb} GB.`);
    showToast(`Memoria asignada: ${gb} GB`);
  });
});

// Filter Mods
const modSearchInput = $('#mod-search-input');
if (modSearchInput) {
  modSearchInput.addEventListener('input', () => {
    const query = modSearchInput.value.trim().toLowerCase();
    const cards = document.querySelectorAll('#mods-list .mod-card');
    let visibleCount = 0;
    cards.forEach((card) => {
      const match = !query || card.textContent.toLowerCase().includes(query);
      card.style.display = match ? 'flex' : 'none';
      if (match) visibleCount++;
    });
    if (cards.length > 0) {
      $('#mods-count').textContent = query
        ? `${visibleCount} de ${cards.length} mods`
        : `${cards.length} ${cards.length === 1 ? 'mod encontrado' : 'mods encontrados'}`;
    }
    const emptyNotice = document.querySelector('#mods-list .mods-search-empty');
    if (visibleCount === 0 && cards.length > 0) {
      if (!emptyNotice) {
        const notice = document.createElement('div');
        notice.className = 'mods-empty mods-search-empty';
        notice.textContent = `No se encontraron mods que coincidan con "${query}".`;
        $('#mods-list').appendChild(notice);
      } else {
        emptyNotice.textContent = `No se encontraron mods que coincidan con "${query}".`;
        emptyNotice.style.display = 'block';
      }
    } else if (emptyNotice) {
      emptyNotice.style.display = 'none';
    }
  });
}

// Global Keyboard Shortcuts
window.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    if (accountDialog?.open) accountDialog.close();
    if (logoutDialog?.open) logoutDialog.close();
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'f') {
    if ($('#mods-view')?.classList.contains('active') && modSearchInput) {
      event.preventDefault();
      modSearchInput.focus();
      modSearchInput.select();
    }
  }
});

// Copy Logs Button
const copyLogsBtn = $('#copy-logs-button');
if (copyLogsBtn) {
  copyLogsBtn.addEventListener('click', async () => {
    if (!gameLogLines.length) {
      showToast('No hay registros para copiar aún.');
      return;
    }
    try {
      await navigator.clipboard.writeText(gameLogLines.join('\n'));
      const prev = copyLogsBtn.textContent;
      copyLogsBtn.textContent = '¡Copiado! ✓';
      showToast(`${gameLogLines.length} líneas de registro copiadas.`);
      setTimeout(() => { copyLogsBtn.textContent = prev; }, 1800);
    } catch {
      showToast('No se pudieron copiar los registros.');
    }
  });
}

window.znClient.initialState().then((state) => {
  latestVersion = state.latest;
  recommendedMemory = state.recommendedMemory || 2;
  $('#hero-latest-version').textContent = state.latest;
  $('#memory-advice').textContent = `RAM: ${state.systemMemoryGb} GB total · ${state.availableMemoryGb} GB disponible ahora · sugerido: ${recommendedMemory} GB para Minecraft.`;
  $('#use-recommended-memory').textContent = `Usar ${recommendedMemory} GB`;
  $('#use-recommended-memory').disabled = false;
  for (const select of [memorySelect, $('#settings-memory')]) {
    select.replaceChildren(...state.memoryChoices.map((memory) => new Option(`${memory} GB`, String(memory))));
  }
  versionSelect.replaceChildren(...state.releases.map(({ id }) => {
    const option = document.createElement('option');
    option.value = id;
    option.textContent = id === state.latest ? `${id} · ÚLTIMA VERSIÓN` : id;
    return option;
  }));
  versionSelect.value = state.config.version;
  memorySelect.value = String(state.config.memory);
  $('#settings-memory').value = String(state.config.memory);
  renderSetupSummary();
  applyAccountState(state);
  if (state.gameRunning) {
    gameRunning = true;
    renderPlayButton();
  }
  renderDownloads();
  renderGameLogs();
  setStatus(state.accountError || `Catálogo listo · versión más reciente ${state.latest}.`);
}).catch((error) => {
  versionSelect.replaceChildren(new Option('No se pudo cargar', ''));
  setStatus(error.message || 'No se pudo conectar con el catálogo de Minecraft.');
});
