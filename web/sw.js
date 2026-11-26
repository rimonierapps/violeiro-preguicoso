// Guarda a página para funcionar sem internet (as cifras ficam no IndexedDB).
const CACHE = 'violeiro-v2';
const ARQUIVOS = ['./', 'index.html', 'app.js', 'manifest.webmanifest', 'icone.svg',
  'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js'];
self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ARQUIVOS)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  const ehApp = u.origin === location.origin || u.hostname === 'cdnjs.cloudflare.com';
  if (!ehApp) return; // buscas na internet passam direto
  // rede primeiro (pega versões novas), cache se estiver sem internet
  e.respondWith(fetch(e.request).then((r) => {
    const copia = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copia)); return r;
  }).catch(() => caches.match(e.request)));
});
