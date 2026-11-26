// Intermediário para a versão web buscar cifras (Cloudflare Workers, plano grátis).
// O navegador não deixa uma página ler outro site diretamente; este worker busca a página e devolve
// com permissão para a página do app ler. Só aceita os sites da lista abaixo.
const PERMITIDOS = ['cifraclub.com.br'];

export default {
  async fetch(req) {
    const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, OPTIONS' };
    if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
    const alvo = new URL(req.url).searchParams.get('url');
    let u;
    try { u = new URL(alvo); } catch { return new Response('url inválida', { status: 400, headers: cors }); }
    if (u.protocol !== 'https:' || !PERMITIDOS.some((d) => u.hostname === d || u.hostname.endsWith('.' + d))) {
      return new Response('site não permitido', { status: 403, headers: cors });
    }
    const r = await fetch(u.toString(), {
      headers: { 'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
                 'Accept-Language': 'pt-BR,pt;q=0.9' },
      cf: { cacheTtl: 86400 },
    });
    return new Response(r.body, { status: r.status, headers: { ...cors, 'Content-Type': 'text/html; charset=utf-8' } });
  },
};
