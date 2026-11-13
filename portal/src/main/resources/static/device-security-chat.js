/* Password-encrypted support chat. The server stores ciphertext only.
   Members share a strong passphrase via an independent trusted channel. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  let operatorMode = false;
  const apiBase = '/api/device-security/support';

  function base64url(bytes) {
    let s = '';
    bytes.forEach(b => { s += String.fromCharCode(b); });
    return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
  function unbase64url(str) {
    const s = str.replace(/-/g, '+').replace(/_/g, '/');
    const pad = '='.repeat((4 - s.length % 4) % 4);
    return Uint8Array.from(atob(s + pad), c => c.charCodeAt(0));
  }
  async function derive(passphrase, salt, usage) {
    if (passphrase.length < 12) throw new Error('Chat passphrase must contain at least 12 characters');
    const material = await crypto.subtle.importKey('raw', enc.encode(passphrase),
      'PBKDF2', false, ['deriveKey']);
    return crypto.subtle.deriveKey({
      name: 'PBKDF2', salt, iterations: 210000, hash: 'SHA-256'
    }, material, {name: 'AES-GCM', length: 256}, false, [usage]);
  }
  async function encryptMessage(plain, passphrase) {
    if (!plain || plain.length > 3000) throw new Error('Enter a message of up to 3000 characters');
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const key = await derive(passphrase, salt, 'encrypt');
    const ciphertext = new Uint8Array(await crypto.subtle.encrypt(
      {name: 'AES-GCM', iv, tagLength: 128}, key, enc.encode(plain)));
    return ['v1', base64url(salt), base64url(iv), base64url(ciphertext)].join('.');
  }
  async function decryptMessage(blob, passphrase) {
    const p = blob.split('.');
    if (p.length !== 4 || p[0] !== 'v1') throw new Error('Unsupported message format');
    const salt = unbase64url(p[1]), iv = unbase64url(p[2]), ciphertext = unbase64url(p[3]);
    if (salt.length !== 16 || iv.length !== 12) throw new Error('Invalid envelope');
    const key = await derive(passphrase, salt, 'decrypt');
    return dec.decode(await crypto.subtle.decrypt(
      {name: 'AES-GCM', iv, tagLength: 128}, key, ciphertext));
  }
  async function api(url, options={}) {
    const response = await fetch(url, {credentials: 'same-origin', cache: 'no-store', ...options});
    if (!response.ok) throw new Error('HTTP ' + response.status + (response.status === 403 ? ' · operator access required' : ''));
    const type = response.headers.get('Content-Type') || '';
    return type.includes('json') ? response.json() : response.text();
  }
  const post = (url, payload) => api(url, {method: 'POST',
    headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)});
  const chatStatus = value => { $('supportStatus').textContent = value; };
  const requestStatus = value => { $('supportRequestsStatus').textContent = value; };

  async function loadOperatorInbox() {
    const inbox = await api(apiBase + '/admin/inbox');
    const selected = $('operatorRecipient').value;
    const control = $('operatorRecipient');
    control.replaceChildren();
    for (const item of inbox) {
      const option = document.createElement('option');
      option.value = item.owner_username || item.OWNER_USERNAME;
      option.textContent = option.value + ' (' + (item.message_count || item.MESSAGE_COUNT) + ' messages)';
      control.append(option);
    }
    if ([...control.options].some(opt => opt.value === selected)) control.value = selected;
  }

  async function loadMessages() {
    chatStatus('Loading encrypted messages…');
    try {
      const passphrase = $('chatPassphrase').value;
      if (passphrase.length < 12) throw new Error('Enter the shared chat passphrase');
      if (operatorMode) await loadOperatorInbox();
      const username = $('operatorRecipient').value;
      if (operatorMode && !username) {
        $('supportHistory').textContent = 'No support conversations yet.';
        chatStatus('No conversations');
        return;
      }
      const path = operatorMode
        ? apiBase + '/admin/conversations/' + encodeURIComponent(username)
        : apiBase + '/messages';
      const messages = await api(path);
      const log = $('supportHistory');
      log.replaceChildren();
      if (!messages.length) log.textContent = 'No messages yet.';
      for (const message of messages) {
        const block = document.createElement('div');
        block.className = 'chat-message';
        const role = message.sender_role || message.SENDER_ROLE;
        const encrypted = message.ciphertext || message.CIPHERTEXT;
        const date = message.sent_at || message.SENT_AT;
        let plain;
        try { plain = await decryptMessage(encrypted, passphrase); }
        catch (e) { plain = '[Could not decrypt — verify the shared passphrase]'; }
        const sender = document.createElement('strong');
        sender.textContent = role + ' · ' + (date ? new Date(date).toLocaleString() : '');
        const body = document.createElement('p');
        body.textContent = plain;
        block.append(sender, body);
        log.append(block);
      }
      chatStatus(messages.length + ' encrypted message(s) loaded.');
    } catch (e) { chatStatus('Unable to load conversation: ' + e.message); }
  }

  async function sendMessage() {
    try {
      const message = $('supportMessage').value.trim();
      const encrypted = await encryptMessage(message, $('chatPassphrase').value);
      const username = $('operatorRecipient').value;
      if (operatorMode && !username) throw new Error('Select a conversation');
      const path = operatorMode
        ? apiBase + '/admin/conversations/' + encodeURIComponent(username)
        : apiBase + '/messages';
      await post(path, {ciphertext: encrypted});
      $('supportMessage').value = '';
      chatStatus('Encrypted message sent.');
      await loadMessages();
    } catch (e) { chatStatus('Message not sent: ' + e.message); }
  }

  async function loadRequests() {
    try {
      const records = await api(apiBase + (operatorMode ? '/admin/recovery' : '/recovery'));
      const wrap = $('supportRequests');
      wrap.replaceChildren();
      if (!records.length) { wrap.textContent = 'No recovery requests.'; return; }
      for (const item of records) {
        const id = item.id || item.ID;
        const state = item.state || item.STATE;
        const file = item.file_label || item.FILE_LABEL;
        const owner = item.owner_username || item.OWNER_USERNAME;
        const reason = item.reason || item.REASON;
        const row = document.createElement('div');
        row.className = 'request-row';
        const label = document.createElement('div');
        label.textContent = (operatorMode ? owner + ' · ' : '') + file + ' · ' + state;
        const details = document.createElement('p');
        details.textContent = reason;
        row.append(label, details);
        if (operatorMode && state === 'PENDING') {
          for (const decision of ['approve','deny']) {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = decision === 'approve' ? 'Approve review' : 'Deny review';
            button.addEventListener('click', async () => {
              try {
                await api(apiBase + '/admin/recovery/' + encodeURIComponent(id) + '/' + decision,
                  {method: 'POST'});
                requestStatus('Review decision recorded.');
                await loadRequests();
              } catch (e) { requestStatus(e.message); }
            });
            row.append(button);
          }
        }
        wrap.append(row);
      }
    } catch (e) { requestStatus('Could not load requests: ' + e.message); }
  }

  async function submitRequest() {
    try {
      await post(apiBase + '/recovery', {
        fileLabel: $('requestFile').value.trim(), reason: $('requestReason').value.trim()
      });
      $('requestReason').value = '';
      requestStatus('Operator review requested. Offline recovery remains available.');
      await loadRequests();
    } catch (e) { requestStatus('Request not submitted: ' + e.message); }
  }

  async function init() {
    try {
      await loadOperatorInbox();
      operatorMode = true;
      $('operatorChatTools').hidden = false;
      $('clientRecoveryTools').hidden = true;
      $('chatRole').textContent = 'Operator';
      await loadRequests();
    } catch (e) {
      operatorMode = false;
      $('operatorChatTools').hidden = true;
      $('clientRecoveryTools').hidden = false;
      $('chatRole').textContent = 'Device owner';
      await loadRequests();
    }
    $('loadSupport').addEventListener('click', loadMessages);
    $('sendSupport').addEventListener('click', sendMessage);
    $('loadRequests').addEventListener('click', loadRequests);
    $('sendRecoveryRequest').addEventListener('click', submitRequest);
    $('operatorRecipient').addEventListener('change', loadMessages);
  }
  init();
})();
