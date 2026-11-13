/* Opaque, client-side-encrypted recovery-key backups. No plaintext key in the portal. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const base = '/api/device-security/escrow';
  let admin = false;
  const message = text => { $('escrowStatus').textContent = text; };

  async function api(path, options = {}) {
    const r = await fetch(base + path, {
      credentials: 'same-origin', cache: 'no-store', ...options
    });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
  }

  function button(label, handler) {
    const el = document.createElement('button');
    el.type = 'button';
    el.textContent = label;
    el.addEventListener('click', handler);
    return el;
  }

  async function load() {
    try {
      const pending = await api('/admin/requests');
      admin = true;
      $('escrowOperatorArea').hidden = false;
      $('escrowOwnerArea').hidden = true;
      renderOperator(pending);
    } catch (error) {
      admin = false;
      $('escrowOperatorArea').hidden = true;
      $('escrowOwnerArea').hidden = false;
      try {
        const [entries, requests] = await Promise.all([
          api('/entries'), api('/requests')
        ]);
        renderOwner(entries, requests);
      } catch (e) {
        message('Unable to load encrypted backups: ' + e.message);
      }
    }
  }

  function renderOperator(requests) {
    const wrap = $('escrowOperatorList');
    wrap.replaceChildren();
    if (!requests.length) { wrap.textContent = 'No recovery-key release requests.'; return; }
    for (const raw of requests) {
      const data = normalize(raw);
      const div = document.createElement('div');
      div.className = 'request-row';
      const title = document.createElement('strong');
      title.textContent = data.owner_username + ' · ' + data.file_label + ' · ' + data.state;
      div.append(title);
      if (data.state === 'PENDING') {
        for (const decision of ['approve','deny']) {
          div.append(button(decision === 'approve' ? 'Approve one-time release' : 'Deny release', async () => {
            try {
              await api('/admin/requests/' + encodeURIComponent(data.id) + '/' + decision, {
                method: 'POST'
              });
              message('Operator decision recorded. The owner can retrieve the protected backup once within 15 minutes.');
              await load();
            } catch (e) { message(e.message); }
          }));
        }
      }
      wrap.append(div);
    }
    message(requests.length + ' recovery-key release request(s) loaded.');
  }

  function renderOwner(entries, requests) {
    const select = $('escrowOwnerEntries');
    const selected = select.value;
    select.replaceChildren();
    for (const raw of entries) {
      const data = normalize(raw);
      const option = document.createElement('option');
      option.value = data.id;
      option.textContent = data.file_label + ' · ' + data.id;
      select.append(option);
    }
    if ([...select.options].some(o => o.value === selected)) select.value = selected;
    const wrap = $('escrowOwnerRequests');
    wrap.replaceChildren();
    for (const raw of requests) {
      const data = normalize(raw);
      const row = document.createElement('div');
      row.className = 'request-row';
      row.textContent = data.file_label + ' · ' + data.state;
      wrap.append(row);
    }
    if (!requests.length) wrap.textContent = 'No encrypted recovery release requests.';
    message(entries.length + ' encrypted recovery backup(s) found. The backup ciphertext is only released after operator approval.');
  }

  function normalize(record) {
    return Object.fromEntries(Object.entries(record).map(([k,v]) => [k.toLowerCase(), v]));
  }

  $('escrowRefresh').addEventListener('click', load);
  $('escrowOwnerRequest').addEventListener('click', async () => {
    const entryId = $('escrowOwnerEntries').value;
    if (!entryId) { message('Select a cloud recovery backup.'); return; }
    try {
      await api('/entries/' + encodeURIComponent(entryId) + '/request', {method:'POST'});
      message('Release request sent to the operator.');
      await load();
    } catch (e) { message('Could not send request: ' + e.message); }
  });
  load();
})();
