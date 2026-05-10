// Stale-while-revalidate service worker for offline support.
const CACHE = "insane-lineup-v1";

self.addEventListener("install", (e) => { self.skipWaiting(); });

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;

  // Don't cache Supabase responses; we already cache the parsed lineup in
  // localStorage via multiplatform-settings. This keeps offline reads instant
  // without serving stale upstream API responses.
  const url = new URL(req.url);
  if (url.pathname.startsWith("/rest/")) return;

  event.respondWith(
    caches.open(CACHE).then((cache) =>
      cache.match(req).then((cached) => {
        const fresh = fetch(req).then((res) => {
          if (res && res.status === 200 && res.type !== "opaque") {
            cache.put(req, res.clone()).catch(() => {});
          }
          return res;
        }).catch(() => cached);
        return cached || fresh;
      })
    )
  );
});
