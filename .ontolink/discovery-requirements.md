# ReWord 3 - Discovery Requirements

## Project
Electron.js desktop password manager for Windows (local-first, offline).

## Requirements
- req-app-type: Electron desktop app, local storage, Windows target
- req-master-password: Master password auth with hint, re-encryption on change
- req-vault: AES-encrypted vault (websites + accounts) via electron-store
- req-favicon: Google Favicon API auto-fetch, stored as base64
- req-import-export: JSON/XLSX export, JSON/CSV browser import
- req-search: Real-time search by name/URL/username
- req-clipboard: IPC clipboard copy
- req-security-model: sandboxed renderer, contextIsolation, preload bridge
- req-lock: Manual vault lock (clear master password from memory)
- req-reset: Full vault wipe

## Constraints
- constraint-local-only: Fully offline, no cloud
- constraint-windows: win32/x64 target

## Actors
- actor-user: End user (local password management)
- actor-dev: Developer

## Source Artifacts
- main.js (581 lines): IPC handlers, window management, favicon fetch, CSV/JSON/XLSX import-export
- database.js (385 lines): PasswordDatabase class, encryption, CRUD
- encryption.js: AES encryption/decryption, master password hashing
- renderer/renderer.js (1450+ lines): UI logic, event handlers
- renderer/index.html: App HTML structure
- renderer/styles.css: App styles
