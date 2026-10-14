# ReWord 3 - Architecture Contracts

## Project Overview
**ReWord** is a local-first, offline Electron desktop password manager for Windows (win32/x64).  
Stack: Electron 28, Node.js, electron-store, AES-256-GCM encryption, ExcelJS.

---

## Domain Boundaries

### 1. Main Process (main.js) — Trusted Host
- **Owns:** window lifecycle, IPC dispatch, database instance, file I/O, network calls (favicon only)
- **Contracts:**
  - Exposes exactly 20 IPC channels through `ipcMain.handle`
  - Only process that touches `PasswordDatabase` directly
  - Network access restricted to `https://www.google.com/s2/favicons` (GET, read-only)
  - All dialog interactions (open/save) run in main process via `dialog` module

### 2. Preload Bridge (preload.js) — Boundary Layer
- **Owns:** contextBridge API surface exposed to renderer as `window.electronAPI`
- **Contract:**
  - Maps renderer calls → IPC invoke: 20 methods total
  - Zero business logic; pure pass-through
  - Runs with `sandbox: true`, `contextIsolation: true`, `nodeIntegration: false`
  - No direct node API exposure; all data through structured IPC messages

### 3. Renderer Process (renderer/) — Untrusted UI
- **Owns:** HTML/CSS/JS UI, view state, user interaction handling
- **Contract:**
  - MUST NOT access node APIs directly
  - All operations via `window.electronAPI.*`
  - Manages UI state in-memory only (no local storage, no file access)
  - Three files: `index.html` (structure), `styles.css` (design), `renderer.js` (logic)

### 4. Database Layer (database.js) — Data Persistence
- **Owns:** electron-store CRUD, data schema, encryption key management
- **Data Schema Contract:**
  ```
  Store {
    websites: Website[],
    master: { hash: hex, salt: hex, hint: string } | null
  }
  Website {
    id: number (Date.now()),
    name: string,
    url: string,
    favicon: string (base64 data URI),
    accounts: Account[],
    created_at: ISO8601
  }
  Account {
    id: number,
    username: string,
    password: string (AES-256-GCM encrypted, base64),
    notes: string,
    created_at: ISO8601,
    updated_at: ISO8601
  }
  ```
- **State contract:** `masterPassword` field in-memory only; nulled on lock/close
- **Duplicate prevention:** website name (case-insensitive), account (username+decrypted password)

### 5. Encryption Layer (encryption.js) — Cryptography
- **Algorithm:** AES-256-GCM
- **Key derivation:** PBKDF2-SHA256, 100,000 iterations, 32-byte key
- **Wire format:** `base64(salt[32] || iv[16] || tag[16] || ciphertext)`
- **Master password hash:** PBKDF2-SHA512, 100,000 iterations, 64-byte hash
- **Contract:** Stateless singleton; never stores keys internally; all keys derived on-the-fly

---

## IPC API Contract (20 channels)

| Channel | Payload | Returns |
|---|---|---|
| `check-master-password` | — | `{ exists: bool }` |
| `set-master-password` | `{ password, hint }` | `{ success }` |
| `verify-master-password` | `password` | `{ success: bool }` |
| `get-password-hint` | — | `{ success, hint }` |
| `reset-vault` | — | `{ success }` |
| `change-master-password` | `{ currentPassword, newPassword, newHint }` | `{ success }` |
| `add-website` | `{ name, url, favicon }` | `{ success, id }` |
| `update-website` | `{ id, name, url, favicon }` | `{ success }` |
| `get-all-websites` | — | `{ success, websites[] }` |
| `search-websites` | `query: string` | `{ success, websites[] }` |
| `delete-website` | `websiteId` | `{ success }` |
| `add-account` | `{ websiteId, username, password, notes }` | `{ success, id }` |
| `update-account` | `{ websiteId, accountId, username, password, notes }` | `{ success }` |
| `delete-account` | `{ websiteId, accountId }` | `{ success }` |
| `get-stats` | — | `{ success, stats: { websites, accounts } }` |
| `copy-to-clipboard` | `text` | `{ success }` |
| `lock-vault` | — | `{ success }` |
| `export-backup` | — | `{ success } \| { canceled }` |
| `import-backup` | — | `{ success } \| { canceled }` |
| `export-xlsx` | — | `{ success } \| { canceled }` |
| `import-csv` | — | `{ success, importedWebsites, importedAccounts, skipped, total }` |
| `fetch-favicon` | `websiteUrl` | `{ success, favicon }` |

---

## Decisions

### DEC-001: Local-only storage, no cloud
**Basis:** Privacy-first design. Passwords never leave the device. electron-store on local filesystem.

### DEC-002: AES-256-GCM with per-record random salt+IV
**Basis:** Authenticated encryption prevents tampering. Per-record randomness ensures ciphertexts are unique even for identical plaintext.

### DEC-003: Sandbox + contextIsolation + preload bridge pattern
**Basis:** Defense-in-depth. Renderer has no node access. XSS cannot escalate to system access.

### DEC-004: electron-store as persistence layer (not SQLite)
**Basis:** Simpler deployment with no native binaries. Acceptable for single-user local app. Trade-off: no query optimization, full array loaded on each read.

### DEC-005: No clipboard auto-clear (known gap)
**Basis:** Not yet implemented. Copied passwords remain in clipboard indefinitely. Recommended: auto-clear after 30s.

### DEC-006: Google Favicon API for icons
**Basis:** Simple, no API key needed. Stores as base64 data URI in electron-store. Falls back gracefully if offline.

---

## Known Risks

### RISK-001: electron-store encryptionKey is hardcoded
The store-level encryption key `'reword-secure-key-2026'` is static and embedded in source code. This protects against casual file browsing but not against reverse engineering.

### RISK-002: masterPassword held in-process memory
The decrypted master password is held in `PasswordDatabase.masterPassword` for the session duration. Memory dump could expose it.

### RISK-003: No clipboard auto-clear
Passwords copied to clipboard persist indefinitely until overwritten.

### RISK-004: PBKDF2 on main process (blocking)
Key derivation (100k iterations) runs synchronously on the main process, which blocks the UI briefly during login and encryption operations.
