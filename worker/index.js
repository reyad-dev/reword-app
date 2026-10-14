// ReWord Vault Sync Worker
// السيرفر دا بيشيل blob مشفر فقط - مش بيشوف أي باسورد
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    };

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    const headers = {
      ...corsHeaders,
      'Content-Type': 'application/json'
    };

    if (url.pathname === '/vault') {
      // GET /vault?id=<sha256hash>  → جلب الخزينة المشفرة
      if (request.method === 'GET') {
        try {
          const id = url.searchParams.get('id');
          if (!id || id.length !== 64) return new Response(JSON.stringify({ error: 'Invalid ID' }), { status: 400, headers });
          if (!env.VAULTS) {
            return new Response(JSON.stringify({ error: 'KV namespace VAULTS is not bound/configured in Cloudflare Worker settings.' }), { status: 500, headers });
          }
          const val = await env.VAULTS.get(id);
          if (!val) return new Response(JSON.stringify({ error: 'Not found' }), { status: 404, headers });
          return new Response(val, { headers });
        } catch (e) {
          return new Response(JSON.stringify({ error: 'Worker GET error: ' + e.message }), { status: 500, headers });
        }
      }

      // POST /vault  body: {id, data, ts}  → حفظ الخزينة المشفرة
      if (request.method === 'POST') {
        try {
          const { id, data, ts } = await request.json();
          if (!id || !data || id.length !== 64) return new Response(JSON.stringify({ error: 'Invalid' }), { status: 400, headers });
          // حماية ضد الكتابة بـ timestamp قديم (conflict detection)
          const existing = await env.VAULTS.get(id, { type: 'json' });
          if (existing && ts && ts <= existing.ts) {
            return new Response(JSON.stringify({ conflict: true, serverTs: existing.ts }), { status: 409, headers });
          }
          // TTL: سنة كاملة
          await env.VAULTS.put(id, JSON.stringify({ data, ts: ts || Date.now() }), { expirationTtl: 31536000 });
          return new Response(JSON.stringify({ ok: true }), { headers });
        } catch (e) {
          return new Response(JSON.stringify({ error: e.message }), { status: 500, headers });
        }
      }
    }

    // Health check
    if (url.pathname === '/') return new Response(JSON.stringify({ status: 'ReWord Sync Worker is running' }), { headers });

    return new Response(JSON.stringify({ error: 'Not found' }), { status: 404, headers });
  }
};
