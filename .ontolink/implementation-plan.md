# ReWord 3 - Implementation Plan

## Project: ReWord Password Manager (Electron)
**Based on:** Architecture Contracts v1 | Discovery Requirements v1  
**Stack:** Electron 28, Node.js, electron-store, AES-256-GCM, ExcelJS

---

## Priority Matrix

| Priority | Area | Effort | Risk |
|---|---|---|---|
| P0 | Security hardening (hardcoded key, clipboard clear) | Medium | HIGH |
| P1 | Performance (PBKDF2 async) | Low | MEDIUM |
| P2 | UX improvements (password generator, strength meter) | Medium | LOW |
| P3 | Build & release pipeline | Low | LOW |

---

## Build Tasks (Breakdown of task-build)

### TASK-B1: Fix RISK-001 — Remove hardcoded encryptionKey
**Owner:** developer  
**Acceptance:** electron-store no longer uses static `'reword-secure-key-2026'` key. Key derived from machine-unique ID or omitted (rely on OS file permissions).  
**Files:** `database.js`  
**Dependency:** none

### TASK-B2: Fix RISK-003 — Clipboard auto-clear after 30 seconds
**Owner:** developer  
**Acceptance:** After `copy-to-clipboard` IPC call, a timer clears the clipboard after 30s. User is notified in UI.  
**Files:** `main.js`, `renderer/renderer.js`  
**Dependency:** none

### TASK-B3: Fix RISK-004 — Move PBKDF2 to async (non-blocking)
**Owner:** developer  
**Acceptance:** `encryption.js` uses `crypto.pbkdf2` (async) instead of `pbkdf2Sync`. `database.js` methods become async accordingly. IPC handlers already `async`.  
**Files:** `encryption.js`, `database.js`  
**Dependency:** none

### TASK-B4: Password strength indicator
**Owner:** developer  
**Acceptance:** Add-account and edit-account forms show strength bar (weak/medium/strong) as user types password.  
**Files:** `renderer/renderer.js`, `renderer/index.html`, `renderer/styles.css`  
**Dependency:** none

### TASK-B5: Password generator
**Owner:** developer  
**Acceptance:** Button in add/edit account modal generates a random secure password (configurable length 12-32, symbols optional).  
**Files:** `renderer/renderer.js`, `renderer/index.html`  
**Dependency:** none

### TASK-B6: Build & package validation
**Owner:** developer  
**Acceptance:** `npm run build` produces `release/ReWord-win32-x64/` directory with a working `.exe`. App launches and all features functional.  
**Files:** `package.json`  
**Dependency:** TASK-B1, TASK-B2, TASK-B3

---

## Verify Tasks (breakdown of task-verify)

### TASK-V1: Verify encryption round-trip
**Acceptance:** encrypt → decrypt with correct password returns original. decrypt with wrong password throws. decrypt with tampered ciphertext throws (GCM auth tag check).

### TASK-V2: Verify clipboard auto-clear
**Acceptance:** After copy, clipboard contains password; after 30s it is cleared.

### TASK-V3: Verify CSV import (Chrome/Firefox/Bitwarden format)
**Acceptance:** Sample CSV files from Chrome, Firefox, and Bitwarden import successfully with correct website/account mapping.

### TASK-V4: Verify XLSX export format
**Acceptance:** Exported XLSX opens in Excel/LibreOffice with correct columns, RTL layout, styled header row.

### TASK-V5: Verify master password change re-encrypts all data
**Acceptance:** After changing master password, all stored passwords decrypt correctly with new password and fail with old.

### TASK-V6: Verify sandbox security model
**Acceptance:** DevTools in renderer: `window.require` is undefined, `window.electronAPI` is defined, no node APIs exposed.

---

## Delivery Tasks (breakdown of task-deliver)

### TASK-D1: Package Windows installer
**Acceptance:** electron-packager produces working win32/x64 package. ASAR bundle applied.

### TASK-D2: Update README / user documentation
**Acceptance:** README covers: install, first launch, master password setup, add sites, import from browser, export backup.

---

## Execution Order

```
[TASK-B1] ─┐
[TASK-B2] ─┤
[TASK-B3] ─┼──► [TASK-B6] ──► [TASK-V1..V6] ──► [TASK-D1, TASK-D2]
[TASK-B4] ─┤
[TASK-B5] ─┘
```

---

## Open Recommendations

### REC-001: Consider app-level auto-lock on idle
Add configurable auto-lock (e.g. 5 min inactivity). Mitigates RISK-002 (master password in memory).

### REC-002: Consider argon2 over PBKDF2
Argon2id is more memory-hard and phishing-resistant. Requires native module. Evaluate trade-off vs simpler deployment.

### REC-003: Add "breach check" via HIBP API (opt-in)
Allow user to check if a stored password appears in known breaches via k-anonymity API. Privacy-safe.
