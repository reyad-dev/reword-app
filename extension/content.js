// ReWord Autofill Content Script (Manifest V3)
(() => {
  'use strict';

  // Shield Icon SVG
  const SHIELD_SVG = `<svg viewBox="0 0 24 24"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"/></svg>`;

  let activeDropdown = null;
  // Map of active fields to their badge controller: Map<HTMLInputElement, { btn, updatePosition, destroy }>
  const activeBadges = new Map();

  // 5-Layer Bulletproof visibility check
  function isFieldVisible(field) {
    if (!field || !document.contains(field)) return false;
    if (field.type === 'hidden') return false;

    // Layer 1: Physical geometry & dimensions
    const rect = field.getBoundingClientRect();
    if (rect.width < 14 || rect.height < 14) return false;

    // Layer 2: Viewport bounds
    if (rect.bottom < -200 || rect.top > (window.innerHeight + 200) || rect.right < -200 || rect.left > (window.innerWidth + 200)) {
      return false;
    }

    // Layer 3: Native Chrome checkVisibility if supported
    if (typeof field.checkVisibility === 'function') {
      try {
        if (!field.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true, contentVisibilityAuto: true })) {
          return false;
        }
      } catch (_) {}
    }

    // Layer 4: Full Ancestor Tree Walk (detects hidden parent screens, modals, opacity, pointer-events)
    let curr = field;
    while (curr && curr !== document.body && curr !== document.documentElement) {
      const s = window.getComputedStyle(curr);
      if (s.display === 'none') return false;
      if (s.visibility === 'hidden' || s.visibility === 'collapse') return false;
      if (parseFloat(s.opacity) < 0.1) return false;
      if (s.pointerEvents === 'none') return false;
      if (curr.hasAttribute('hidden') || curr.getAttribute('aria-hidden') === 'true') return false;
      curr = curr.parentElement;
    }

    // Layer 5: Hit-testing at center point to ensure field is not obscured by an overlay, loading screen, or dialog
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    if (cx >= 0 && cx <= window.innerWidth && cy >= 0 && cy <= window.innerHeight) {
      const hit = document.elementFromPoint(cx, cy);
      if (hit && hit !== field && !field.contains(hit) && !hit.contains(field)) {
        return false;
      }
    }

    return true;
  }

  // Remove single badge
  function removeBadge(field) {
    const entry = activeBadges.get(field);
    if (entry) {
      entry.destroy();
      activeBadges.delete(field);
    }
  }

  // Completely wipe all badges immediately (e.g. on login or submission)
  function clearAllBadges() {
    closeDropdown();
    for (const [field, entry] of Array.from(activeBadges.entries())) {
      entry.destroy();
    }
    activeBadges.clear();
    const allBtns = document.querySelectorAll('.reword-autofill-btn');
    allBtns.forEach(b => b.remove());
  }

  // Clean all badges whose inputs are no longer visible or removed from DOM
  function cleanDeadBadges() {
    for (const [field, entry] of Array.from(activeBadges.entries())) {
      if (!isFieldVisible(field)) {
        removeBadge(field);
      } else {
        entry.updatePosition();
      }
    }

    // Purge any orphan shield buttons in the DOM not tracked in activeBadges
    const allBtns = document.querySelectorAll('.reword-autofill-btn');
    const trackedBtns = new Set(Array.from(activeBadges.values()).map(e => e.btn));
    allBtns.forEach(btn => {
      if (!trackedBtns.has(btn)) {
        btn.remove();
      }
    });
  }

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
      const bestMatch = inputs.find(i => {
        const name = (i.name || i.id || i.autocomplete || '').toLowerCase();
        return name.includes('user') || name.includes('email') || name.includes('login') || name.includes('phone');
      });
      if (bestMatch) return bestMatch;
      const passIndex = inputs.indexOf(passField);
      if (passIndex > 0) return inputs[passIndex - 1];
      if (inputs.length > 0) return inputs[0];
    }

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
    if (!isFieldVisible(inputField)) return;
    if (activeBadges.has(inputField)) {
      activeBadges.get(inputField).updatePosition();
      return;
    }

    const btn = document.createElement('div');
    btn.className = 'reword-autofill-btn';
    btn.innerHTML = SHIELD_SVG;
    btn.title = 'ReWord Autofill';
    document.body.appendChild(btn);

    function updateBadgePosition() {
      if (!isFieldVisible(inputField)) {
        removeBadge(inputField);
        return;
      }

      const rect = inputField.getBoundingClientRect();
      btn.style.display = 'flex';
      
      const isRtl = window.getComputedStyle(inputField).direction === 'rtl';
      const top = window.scrollY + rect.top + (rect.height - 24) / 2;
      const left = isRtl
        ? window.scrollX + rect.left + 6
        : window.scrollX + rect.right - 30;

      btn.style.top = `${top}px`;
      btn.style.left = `${left}px`;
    }

    // Attach ResizeObserver to field if available to track field removal/shrinking instantly
    let ro = null;
    if (typeof ResizeObserver !== 'undefined') {
      try {
        ro = new ResizeObserver(() => {
          updateBadgePosition();
        });
        ro.observe(inputField);
      } catch (_) {}
    }

    const onScrollOrResize = () => updateBadgePosition();
    window.addEventListener('resize', onScrollOrResize, { passive: true });
    window.addEventListener('scroll', onScrollOrResize, { passive: true });

    btn.addEventListener('mousedown', (e) => {
      e.preventDefault();
      e.stopPropagation();
      openAutofillDropdown(inputField, btn);
    });

    const destroy = () => {
      window.removeEventListener('resize', onScrollOrResize);
      window.removeEventListener('scroll', onScrollOrResize);
      if (ro) {
        ro.disconnect();
        ro = null;
      }
      if (btn.parentNode) {
        btn.parentNode.removeChild(btn);
      }
    };

    activeBadges.set(inputField, {
      btn,
      updatePosition: updateBadgePosition,
      destroy
    });

    updateBadgePosition();
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
    cleanDeadBadges();

    const passInputs = document.querySelectorAll('input[type="password"]');
    passInputs.forEach((passInput) => {
      if (isFieldVisible(passInput)) {
        attachBadge(passInput);
        const userInput = findUsernameField(passInput);
        if (userInput && isFieldVisible(userInput)) {
          attachBadge(userInput);
        }
      }
    });
  }

  // Immediately remove ALL badges when a form is submitted (login occurred)
  document.addEventListener('submit', () => {
    clearAllBadges();
    setTimeout(cleanDeadBadges, 100);
    setTimeout(cleanDeadBadges, 400);
    setTimeout(cleanDeadBadges, 1000);
  }, true);

  // Catch login button clicks even when not inside a traditional form submit
  document.addEventListener('click', (e) => {
    const target = e.target;
    if (!target) return;
    const btn = target.closest('button, input[type="submit"], input[type="button"], [role="button"], a');
    if (btn) {
      const text = (btn.innerText || btn.value || btn.getAttribute('aria-label') || '').toLowerCase();
      if (/log\s*in|sign\s*in|دخول|تسجيل|submit|continue|متابعة/i.test(text)) {
        clearAllBadges();
        setTimeout(cleanDeadBadges, 100);
        setTimeout(cleanDeadBadges, 400);
        setTimeout(cleanDeadBadges, 1000);
      }
    }
  }, true);

  // Catch Enter key on input fields (which frequently triggers AJAX login in SPAs)
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && e.target && (e.target.tagName === 'INPUT')) {
      if (e.target.type === 'password' || e.target.type === 'email' || e.target.type === 'text') {
        setTimeout(cleanDeadBadges, 50);
        setTimeout(cleanDeadBadges, 300);
        setTimeout(cleanDeadBadges, 800);
      }
    }
  }, true);

  // Global click outside to dismiss dropdown
  document.addEventListener('mousedown', (e) => {
    if (activeDropdown && !activeDropdown.contains(e.target) && !e.target.closest('.reword-autofill-btn')) {
      closeDropdown();
    }
  });

  // Track SPA URL changes (pushState, replaceState, popstate, hashchange)
  let lastHref = window.location.href;
  function handleUrlChange() {
    if (window.location.href !== lastHref) {
      lastHref = window.location.href;
      clearAllBadges();
      setTimeout(scanPage, 200);
    }
  }

  window.addEventListener('popstate', () => { handleUrlChange(); cleanDeadBadges(); });
  window.addEventListener('hashchange', () => { handleUrlChange(); cleanDeadBadges(); });

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
    cleanDeadBadges();
    scanPage();
  });

  observer.observe(document.body || document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['style', 'class', 'hidden', 'type', 'aria-hidden', 'disabled']
  });

  // Periodic safety check to ensure dead badges never linger after login or SPA transitions
  setInterval(() => {
    handleUrlChange();
    cleanDeadBadges();
  }, 350);
})();
