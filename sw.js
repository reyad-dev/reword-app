const CACHE_NAME = 'reword-v12';
const ASSETS = [
  './index.html',
  './style.css?v=9',
  './core.js?v=2',
  './ui.js?v=6',
  './manifest.json'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', e => {
  // لا تعترض الطلبات الخارجية (مثل الـ Worker API) أو الطلبات التي ليست GET
  if (e.request.method !== 'GET' || !e.request.url.startsWith(self.location.origin)) {
    return;
  }

  e.respondWith(
    caches.match(e.request).then(cached => {
      return cached || fetch(e.request).catch(() => {
        // أرجع index.html فقط لطلبات الصفحات والـ HTML المحلية عند انقطاع الإنترنت
        if (e.request.mode === 'navigate' || e.request.headers.get('accept')?.includes('text/html')) {
          return caches.match('./index.html');
        }
      });
    })
  );
});

