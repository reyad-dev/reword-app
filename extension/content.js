// ReWord Autofill Content Script (Manifest V3)
(() => {
  'use strict';

  // Shield Icon SVG
  const SHIELD_SVG = `<svg viewBox="0 0 24 24"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"/></svg>`;

  let activeDropdown = null;
  const attachedFields = new Set();

  // Helper to trigger realistic input events for React, Vue, etc.
  function setFieldValue(field, value) {
    if (!field) return;
    field.focus();
    
    // Support React 16+ value tracker
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
    if (nativeSetter) {
      nativeSetter.call(field, value);
    } else {
      field.value = value;
    }

    field.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
    field.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
    field.classList.add('reword-filled-success');
    setTimeout(() => field.classList.remove('reword-filled-success'), 1500);
  }

  // Find the username field associated with a password field
  function findUsernameField(passField) {
    const form = passField.closest('form');
    if (form) {
      const inputs = Array.from(form.querySelectorAll('input:not([type="hidden"]):not([type="password"]):not([type="submit"]):not([type="button"])'));
      // Prefer explicit username/email fields
      const bestMatch = inputs.find(i => {
        const name = (i.name || i.id || i.autocomplete || '').toLowerCase();
        return name.includes('user') || name.includes('email') || name.includes('login') || name.includes('phone');
      });
      if (bestMatch) return bestMatch;
      // Otherwise return the input right before password
      const passIndex = inputs.indexOf(passField);
      if (passIndex > 0) return inputs[passIndex - 1];
      if (inputs.length > 0) return inputs[0];
    }

    // If outside a form, look in previous sibling elements
    let prev = passField.previousElementSibling;
    while (prev) {
      if (prev.tagName === 'INPUT' && prev.type !== 'password' && prev.type !== 'hidden') return prev;
      const inside = prev.querySelector?.('input:not([type="password"]):not([type="hidden"])');
      if (inside) return inside;
      prev = prev.previousElementSibling;
    }

    return null;
  }

  // Remove existing dropdown
  function closeDropdown() {
    if (activeDropdown) {
      activeDropdown.remove();
      activeDropdown = null;
    }
  }

  // Position and attach the ReWord badge inside the input field
  function attachBadge(inputField) {
    if (attachedFields.has(inputField)) return;
    attachedFields.add(inputField);

    const btn = document.createElement('div');
    btn.className = 'reword-autofill-btn';
    btn.innerHTML = SHIELD_SVG;
    btn.title = 'ReWord Autofill';
    document.body.appendChild(btn);

    function updateBadgePosition() {
      if (!document.contains(inputField) || inputField.offsetParent === null) {
        btn.style.display = 'none';
        return;
      }
      const rect = inputField.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) {
        btn.style.display = 'none';
        return;
      }
      btn.style.display = 'flex';
      
      const isRtl = window.getComputedStyle(inputField).direction === 'rtl';
      const top = window.scrollY + rect.top + (rect.height - 24) / 2;
      const left = isRtl
        ? window.scrollX + rect.left + 6
        : window.scrollX + rect.right - 30;

      btn.style.top = `${top}px`;
      btn.style.left = `${left}px`;
    }

    updateBadgePosition();
    window.addEventListener('resize', updateBadgePosition, { passive: true });
    window.addEventListener('scroll', updateBadgePosition, { passive: true });

    btn.addEventListener('mousedown', (e) => {
      e.preventDefault();
      e.stopPropagation();
      openAutofillDropdown(inputField, btn);
    });

    inputField.addEventListener('focus', () => {
      // Auto prompt on focus if appropriate
    });
  }

  // Open Autofill Dropdown
  function openAutofillDropdown(field, anchorBtn) {
    closeDropdown();

    chrome.runtime.sendMessage({
      action: 'GET_MATCHING_CREDENTIALS',
      url: window.location.href
    }, (res) => {
      if (chrome.runtime.lastError || !res) return;

      const dropdown = document.createElement('div');
      dropdown.className = 'reword-dropdown';

      const rect = field.getBoundingClientRect();
      dropdown.style.top = `${window.scrollY + rect.bottom + 6}px`;
      dropdown.style.left = `${window.scrollX + rect.left}px`;
      dropdown.style.width = `${Math.max(rect.width, 260)}px`;

      if (res.isLocked) {
        dropdown.innerHTML = `
          <div class="reword-dropdown-header">
            <div class="reword-dropdown-brand">${SHIELD_SVG}<span>ReWord</span></div>
            <span>الخزينة مقفلة</span>
          </div>
          <div class="reword-dropdown-locked">
            <div>🔒 الخزينة مقفلة حالياً</div>
            <div style="font-size:11px;color:#a1a1aa;margin-top:4px;">افتح إضافة ReWord من شريط المتصفح لإلغاء القفل</div>
          </div>
        `;
      } else if (res.matches && res.matches.length > 0) {
        let itemsHtml = `
          <div class="reword-dropdown-header">
            <div class="reword-dropdown-brand">${SHIELD_SVG}<span>ReWord</span></div>
            <span>${res.matches.length} حساب متاح</span>
          </div>
        `;

        res.matches.forEach((acc, idx) => {
          const letter = (acc.user || acc.siteName || 'R').charAt(0).toUpperCase();
          itemsHtml += `
            <div class="reword-dropdown-item" data-index="${idx}">
              <div class="reword-item-avatar">${letter}</div>
              <div class="reword-item-info">
                <span class="reword-item-user">${acc.user}</span>
                <span class="reword-item-sub">${acc.siteName || res.domain}</span>
              </div>
            </div>
          `;
        });

        dropdown.innerHTML = itemsHtml;

        // Attach item click handlers
        dropdown.querySelectorAll('.reword-dropdown-item').forEach((item) => {
          item.addEventListener('mousedown', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const idx = parseInt(item.getAttribute('data-index'), 10);
            const acc = res.matches[idx];
            if (acc) {
              const usernameField = field.type === 'password' ? findUsernameField(field) : field;
              const passwordField = field.type === 'password' ? field : field.form?.querySelector('input[type="password"]');

              if (usernameField && acc.user) setFieldValue(usernameField, acc.user);
              if (passwordField && acc.pass) setFieldValue(passwordField, acc.pass);
            }
            closeDropdown();
          });
        });
      } else {
        dropdown.innerHTML = `
          <div class="reword-dropdown-header">
            <div class="reword-dropdown-brand">${SHIELD_SVG}<span>ReWord</span></div>
            <span>${res.domain || 'الموقع الحالي'}</span>
          </div>
          <div class="reword-dropdown-locked">
            <div>لا توجد حسابات مسجلة لهذا الموقع</div>
            <div style="font-size:11px;color:#a1a1aa;margin-top:4px;">يمكنك حفظ بيانات الدخول من نافذة الإضافة</div>
          </div>
        `;
      }

      document.body.appendChild(dropdown);
      activeDropdown = dropdown;
    });
  }

  // Scan page for login fields
  function scanPage() {
    const passInputs = document.querySelectorAll('input[type="password"]');
    passInputs.forEach((passInput) => {
      attachBadge(passInput);
      const userInput = findUsernameField(passInput);
      if (userInput) attachBadge(userInput);
    });
  }

  // Global click outside to dismiss dropdown
  document.addEventListener('mousedown', (e) => {
    if (activeDropdown && !activeDropdown.contains(e.target) && !e.target.closest('.reword-autofill-btn')) {
      closeDropdown();
    }
  });

  // Listen for 1-click autofill message from popup
  chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg.action === 'TRIGGER_AUTOFILL') {
      const passInput = document.querySelector('input[type="password"]');
      const userInput = passInput ? findUsernameField(passInput) : document.querySelector('input[type="email"], input[type="text"], input[name*="user"], input[name*="login"]');
      
      let filled = false;
      if (userInput && msg.user) {
        setFieldValue(userInput, msg.user);
        filled = true;
      }
      if (passInput && msg.pass) {
        setFieldValue(passInput, msg.pass);
        filled = true;
      }
      sendResponse({ success: filled });
    }
    return true;
  });

  // Initial Scan
  scanPage();

  // Observer for dynamic SPAs (Single Page Applications)
  const observer = new MutationObserver(() => {
    scanPage();
  });

  observer.observe(document.body || document.documentElement, {
    childList: true,
    subtree: true
  });
})();
