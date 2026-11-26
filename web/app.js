/* Violeiro Preguiçoso — versão web (Linux, Windows, qualquer navegador moderno) */
'use strict';

const INI = '\u0001', FIM = '\u0002';
const DOMINIO = 'cifraclub.com.br';
const BASE = 'https://www.' + DOMINIO + '/';
const SUFIXO_TITULO = 'Cifra Club';
const $ = (id) => document.getElementById(id);
const avisar = (t) => { $('status').textContent = t || ''; };

// ---------------- armazenamento (IndexedDB) ----------------
const DB = {
  db: null,
  abrir() {
    return new Promise((ok, erro) => {
      const r = indexedDB.open('violeiro', 1);
      r.onupgradeneeded = () => r.result.createObjectStore('cifras', { keyPath: 'url' });
      r.onsuccess = () => { this.db = r.result; ok(); };
      r.onerror = () => erro(r.error);
    });
  },
  _tx(modo) { return this.db.transaction('cifras', modo).objectStore('cifras'); },
  pegar(url) {
    return new Promise((ok) => { const r = this._tx('readonly').get(url); r.onsuccess = () => ok(r.result || null); r.onerror = () => ok(null); });
  },
  todas() {
    return new Promise((ok) => { const r = this._tx('readonly').getAll(); r.onsuccess = () => ok(r.result || []); r.onerror = () => ok([]); });
  },
  guardar(c) {
    c.quando = Date.now();
    return new Promise((ok) => { const r = this._tx('readwrite').put(c); r.onsuccess = () => ok(); r.onerror = () => ok(); });
  },
  apagar(url) {
    return new Promise((ok) => { const r = this._tx('readwrite').delete(url); r.onsuccess = () => ok(); r.onerror = () => ok(); });
  },
};

const cfg = {
  get token() { return localStorage.getItem('token') || ''; },
  get proxy() { return localStorage.getItem('proxy') || ''; },
};

// ---------------- nomes e endereços ----------------
function slug(s) {
  return (s || '').toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '')
    .replace(/&/g, ' e ').replace(/['’]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}
function limparTitulo(t) {
  let s = (t || '').replace(/\s*[([][^)\]]*[)\]]/g, ' ');
  s = s.replace(/\s+-\s+(.*(remaster|ao vivo|live|vers[aã]o|version|edit|mix|ac[uú]stic|acoustic|mono|stereo|single|radio|clipe|oficial|official|lyric|letra|\d{4}).*)$/i, '');
  s = s.replace(/\s+(feat\.?|ft\.?|part\.?|participa[cç][aã]o)\s+.*$/i, '');
  return s.trim();
}
function limparArtista(a) {
  return (a || '').replace(/\s*-\s*topic$/i, '').replace(/vevo$/i, '')
    .replace(/\s+(feat\.?|ft\.?|part\.?|participa[cç][aã]o)\s+.*$/i, '').trim();
}
function candidatos(artista, titulo) {
  artista = (artista || '').trim(); titulo = (titulo || '').trim();
  const pares = [];
  if (artista) pares.push([artista, titulo]);
  const i = titulo.indexOf(' - ');
  if (i > 0) { const a = titulo.slice(0, i).trim(), b = titulo.slice(i + 3).trim(); pares.push([a, b], [b, a]); }
  const urls = new Set();
  for (const [a0, t0] of pares) {
    const la = limparArtista(a0);
    for (const a of [la, la.split(/\s*(,|&| x | X | e )\s*/)[0], a0]) {
      for (const t of [limparTitulo(t0), t0]) {
        const sa = slug(a), st = slug(t);
        if (sa && st) urls.add(BASE + sa + '/' + st + '/');
      }
    }
  }
  return [...urls];
}
function urlDeMusica(u) {
  try {
    const x = new URL(u);
    if (!x.hostname.endsWith(DOMINIO)) return null;
    const seg = x.pathname.split('/').filter(Boolean);
    if (seg.length < 2 || seg.length > 3) return null;
    const reservados = ['letra', 'blog', 'academy', 'aulas', 'estilos', 'top', 'mais-acessadas', 'pesquisa', 'busca', 'conta',
      'contribuicoes', 'cifra-club-pro', 'afinador', 'dicionario-de-acordes', 'metronomo', 'videoaulas', 'news', 'artistas'];
    if (reservados.includes(seg[0]) || seg[0].includes('.') || seg[1].includes('.')) return null;
    if (seg.length === 3 && !seg[2].endsWith('.html')) return null;
    return BASE + seg[0] + '/' + seg[1] + '/';
  } catch { return null; }
}
function chave(a, t) { return slug(limparArtista(a)) + '|' + slug(limparTitulo(t)); }

// ---------------- leitura de páginas ----------------
function entidades(s) {
  const t = document.createElement('textarea');
  return s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m) => { t.innerHTML = m; return t.value; });
}
function lerPaginaDoSite(url, html) {
  const i = html.indexOf('<pre');
  if (i < 0) return null;
  const ini = html.indexOf('>', i) + 1, fim = html.indexOf('</pre>', ini);
  if (ini <= 0 || fim < 0) return null;
  let pre = html.slice(ini, fim)
    .replace(/<b(\s[^>]*)?>([\s\S]*?)<\/b>/gi, INI + '$2' + FIM)
    .replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '');
  pre = entidades(pre).replace(/​/g, '').replace(/\r/g, '').replace(INI + FIM, '').replace(/^\n+/, '').replace(/\s+$/, '');
  if (!pre.trim()) return null;
  let og = (html.match(/property="og:title"\s+content="([^"]*)"/) || [])[1] || (html.match(/<title>([\s\S]*?)<\/title>/i) || [])[1] || '';
  og = entidades(og).replace(new RegExp('\\s*-\\s*' + SUFIXO_TITULO + '\\s*$'), '').trim();
  const k = og.lastIndexOf(' - ');
  return { url, titulo: k > 0 ? og.slice(0, k).trim() : og, artista: k > 0 ? og.slice(k + 3).trim() : '', texto: pre };
}
async function baixar(url) {
  if (!cfg.proxy) throw new Error('configure o intermediário (⚙️) para buscar na internet');
  const r = await fetch(cfg.proxy + encodeURIComponent(url));
  if (r.status === 404) return null;
  if (!r.ok) throw new Error('intermediário respondeu ' + r.status);
  return await r.text();
}
async function buscarNaInternet(artista, titulo) {
  for (const url of candidatos(artista, titulo)) {
    const html = await baixar(url);
    if (!html) continue;
    const c = lerPaginaDoSite(url, html);
    if (c) return c;
  }
  return null;
}

// ---------------- acordes ----------------
const SUS = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
const BEM = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'Gb', 'G', 'Ab', 'A', 'Bb', 'B'];
const RE_ACORDE = /^\(?[A-G][#b]?(?:maj|min|dim|aug|sus|add|[mM]|º|°|\+|-|[0-9]|\(|\)|#|b|\/(?=[0-9]))*(?:\/[A-G][#b]?)?\)?$/;
const RE_IGNORA = /^(\[.*\]|\|+|x\d+|\d+x|-+|\(?x?\d+\)?|%|\.+)$/i;
const ehAcorde = (t) => RE_ACORDE.test(t);
function indice(n) {
  let i = SUS.indexOf(n); if (i < 0) i = BEM.indexOf(n);
  return i >= 0 ? i : ({ Cb: 11, Fb: 4, 'E#': 5, 'B#': 0 }[n] ?? -1);
}
function transpor(ac, s) {
  s = ((s % 12) + 12) % 12; if (!s) return ac;
  const bemol = /^[A-G]b/.test(ac);
  return ac.replace(/(^|\/)([A-G])([#b]?)/g, (m, p, l, a) => {
    const i = indice(l + a); return p + (i < 0 ? l + a : (bemol ? BEM : SUS)[(i + s) % 12]);
  });
}
function transporTexto(t, s) {
  if (!(((s % 12) + 12) % 12)) return t;
  let out = '', i = 0, divida = 0;
  while (i < t.length) {
    const c = t[i];
    if (c === INI) {
      const e = t.indexOf(FIM, i); if (e < 0) { out += t.slice(i); break; }
      const ac = t.slice(i + 1, e), novo = transpor(ac, s);
      out += INI + novo + FIM; divida += novo.length - ac.length;
      if (divida < 0) { out += ' '.repeat(-divida); divida = 0; }
      i = e + 1; continue;
    }
    if (c === '\n') divida = 0;
    else if (c === ' ' && divida > 0 && t[i + 1] === ' ') { divida--; i++; continue; }
    out += c; i++;
  }
  return out;
}
function marcarAcordes(texto) {
  return texto.split('\n').map((l) => {
    const toks = l.trim().split(/\s+/).filter(Boolean);
    if (!toks.length) return l;
    let n = 0;
    for (const t of toks) { if (ehAcorde(t)) n++; else if (!RE_IGNORA.test(t)) return l; }
    if (!n) return l;
    return l.replace(/\S+/g, (t) => {
      if (!ehAcorde(t)) return t;
      let pre = '', pos = '';
      if (t.startsWith('(')) { pre = '('; t = t.slice(1); }
      if (t.endsWith(')') && (t.split(')').length > t.split('(').length)) { pos = ')'; t = t.slice(0, -1); }
      return pre + INI + t + FIM + pos;
    });
  }).join('\n');
}

// ---------------- importar ----------------
const novoId = () => 'local:' + Date.now() + '-' + Math.floor(Math.random() * 1000);
function semExtensao(n) { const k = (n || '').lastIndexOf('.'); return (k > 0 ? n.slice(0, k) : n || '').replace(/_/g, ' ').trim(); }
function separar(s) {
  s = (s || '').replace(/\s*[-|–]\s*(Cifra|Cifras|Letra|Tab)[^-|–]*$/i, '').trim();
  let k = s.indexOf(' - '); if (k < 0) k = s.indexOf(' – ');
  return k > 0 ? [s.slice(k + 3).trim(), s.slice(0, k).trim()] : [s, ''];
}
function deTexto(titulo, artista, texto) {
  let t = texto.replace(/\r/g, '').replace(/\t/g, '    ').replace(/​/g, '').replace(/ /g, ' ')
    .replace(/^\n+/, '').replace(/\s+$/, '');
  return { url: novoId(), titulo: (titulo || '').trim() || 'Sem título', artista: (artista || '').trim(),
    texto: t.includes(INI) ? t : marcarAcordes(t), minha: true };
}
function deHtml(html, nomeArquivo) {
  if (html.includes(SUFIXO_TITULO) && html.includes('<pre')) {
    const c = lerPaginaDoSite(novoId(), html);
    if (c) { c.minha = true; c.titulo = c.titulo || semExtensao(nomeArquivo); return c; }
  }
  let corpo = html;
  const p = html.indexOf('<pre');
  if (p >= 0) {
    const ini = html.indexOf('>', p) + 1, fim = html.indexOf('</pre>', ini);
    if (ini > 0 && fim > ini) corpo = html.slice(ini, fim);
  } else {
    corpo = corpo.replace(/<(script|style|head|nav|footer)[^>]*>[\s\S]*?<\/\1>/gi, '').replace(/<\/(p|div|li|h[1-6]|tr)>/gi, '\n');
  }
  corpo = entidades(corpo.replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '')).replace(/\n{3,}/g, '\n\n');
  const titulo = entidades((html.match(/<title>([\s\S]*?)<\/title>/i) || [])[1] || '').trim() || semExtensao(nomeArquivo);
  const [t, a] = separar(titulo);
  return deTexto(t, a, corpo);
}
async function deArquivo(f) {
  const nome = f.name || 'cifra', ext = nome.toLowerCase();
  const buf = await f.arrayBuffer();
  const b = new Uint8Array(buf.slice(0, 4));
  if (f.type.includes('pdf') || ext.endsWith('.pdf') || (b[0] === 0x25 && b[1] === 0x50 && b[2] === 0x44 && b[3] === 0x46)) {
    const [t, a] = separar(semExtensao(nome));
    return { url: novoId(), titulo: t, artista: a, texto: '', pdf: new Blob([buf], { type: 'application/pdf' }), minha: true };
  }
  let txt = new TextDecoder('utf-8').decode(buf);
  if (txt.includes('�')) txt = new TextDecoder('windows-1252').decode(buf);
  if (f.type.includes('html') || /\.html?$/.test(ext) || /<html|<!doctype html/i.test(txt.slice(0, 2000))) return deHtml(txt, nome);
  const [t, a] = separar(semExtensao(nome));
  return deTexto(t, a, txt);
}
function adivinharNome(texto) {
  for (const l of texto.split('\n')) {
    const s = l.trim();
    if (!s || s.startsWith('[')) continue;
    const toks = s.split(/\s+/);
    if (toks.some(ehAcorde) && toks.length < 6) continue;
    if (s.length > 60) break;
    return separar(s);
  }
  return ['', ''];
}
function lista(texto) {
  return texto.split('\n').map((l) => l.replace(/^\s*(\d+[.)-]|[-*•])\s*/, '').trim())
    .filter((s) => s && !s.startsWith('#'))
    .map((s) => { s = s.replace(/\s*[;\t]\s*/g, ' - '); if (!s.includes(' - ') && s.split(',').length === 2) s = s.replace(',', ' - '); return ['', s.replace(/\s+/g, ' ')]; });
}

// ---------------- procurar (minhas cifras primeiro) ----------------
function combina(m, artista, titulo) {
  const t = slug(limparTitulo(titulo)); if (!t) return false;
  const a = slug(limparArtista(artista)), mt = slug(m.titulo), ma = slug(m.artista);
  const tudo = '-' + mt + '-' + ma + '-';
  if (!(t === mt || tudo.includes('-' + t + '-'))) return false;
  if (!a || !ma) return true;
  return ma === a || tudo.includes('-' + a + '-') || a.includes(ma) || ma.includes(a);
}
async function acharGuardada(artista, titulo) {
  const todas = await DB.todas();
  const pares = [[artista, titulo]];
  if (!artista && titulo.includes(' - ')) { const k = titulo.indexOf(' - '); pares.push([titulo.slice(0, k), titulo.slice(k + 3)], [titulo.slice(k + 3), titulo.slice(0, k)]); }
  for (const [a, t] of pares) { const m = todas.find((x) => x.minha && combina(x, a, t)); if (m) return m; }
  const cand = candidatos(artista, titulo), k = chave(artista, titulo);
  return todas.find((x) => chave(x.artista, x.titulo) === k || cand.includes(x.url)) || null;
}
async function procurar(artista, titulo) {
  let c = await acharGuardada(artista, titulo);
  if (c) return c;
  c = await buscarNaInternet(artista, titulo);
  if (c) { const velha = await DB.pegar(c.url); c.minha = !!(velha && velha.minha); await DB.guardar(c); }
  return c;
}

// ---------------- tela inicial ----------------
async function montarListas() {
  const todas = await DB.todas();
  const minhas = todas.filter((c) => c.minha).sort((a, b) => a.titulo.localeCompare(b.titulo, 'pt'));
  const recentes = todas.sort((a, b) => b.quando - a.quando).slice(0, 25);
  const desenhar = (el, l, vazio, comAcoes) => {
    el.innerHTML = '';
    if (!l.length) { el.innerHTML = '<div class="apagado">' + vazio + '</div>'; return; }
    for (const c of l) {
      const d = document.createElement('div'); d.className = 'item';
      const tipo = c.pdf ? '📄 ' : c.url.startsWith('local:') ? '📝 ' : c.minha ? '⭐ ' : '';
      const nome = document.createElement('span');
      nome.textContent = tipo + c.titulo;
      const sm = document.createElement('small'); sm.textContent = c.artista ? '  ·  ' + c.artista : '';
      nome.appendChild(sm);
      d.appendChild(nome);
      if (comAcoes) {
        const ac = document.createElement('span'); ac.className = 'acoes';
        const br = document.createElement('button'); br.textContent = '✏️'; br.title = 'Renomear';
        br.onclick = async (e) => { e.stopPropagation(); const t = prompt('Música', c.titulo); if (t === null) return;
          const a = prompt('Artista', c.artista); if (a === null) return; c.titulo = t; c.artista = a; await DB.guardar(c); montarListas(); };
        const bx = document.createElement('button'); bx.textContent = '🗑️'; bx.title = 'Apagar / tirar das minhas';
        bx.onclick = async (e) => { e.stopPropagation(); if (!confirmar('Tirar "' + c.titulo + '" das minhas cifras?')) return;
          if (c.url.startsWith('local:')) await DB.apagar(c.url); else { c.minha = false; await DB.guardar(c); } montarListas(); };
        ac.append(br, bx); d.appendChild(ac);
      }
      d.onclick = () => abrir(c);
      el.appendChild(d);
    }
  };
  desenhar($('listaMinhas'), minhas, 'Nenhuma ainda. Importe, cole, arraste arquivos para cá ou toque ☆ numa cifra.', true);
  desenhar($('listaRecentes'), recentes, 'Nenhuma cifra aberta ainda.', false);
}
function confirmar(msg) { return window.confirm(msg); }

async function buscar(artista, musica, sinc) {
  if (/^https?:\/\//.test(musica)) return importarLink(musica);
  avisar('Procurando…');
  try {
    const c = await procurar(artista, musica);
    if (c) { avisar(''); abrir(c, sinc); }
    else avisar('Não achei pelo endereço direto. Use 🌐 Pesquisar na web, copie o link da cifra e cole no campo Música.');
  } catch (e) { avisar('Não consegui buscar: ' + e.message); }
}
async function importarLink(url) {
  const daFonte = urlDeMusica(url);
  avisar('Lendo a página…');
  try {
    const html = await baixar(daFonte || url);
    if (!html) throw new Error('página não encontrada');
    if (daFonte) { const c = lerPaginaDoSite(daFonte, html); if (c) { await DB.guardar(c); avisar(''); return abrir(c); } }
    const c = deHtml(html, url.split('/').filter(Boolean).pop());
    dialogoColar(c.texto.replaceAll(INI, '').replaceAll(FIM, ''), (c.artista ? c.artista + ' - ' : '') + c.titulo);
    avisar('Confira o texto e salve.');
  } catch (e) { avisar('Não consegui ler a página: ' + e.message + '\nSelecione o texto da cifra na página, copie e use 📋 Colar cifra.'); }
}
async function importarArquivos(files) {
  let ok = 0, ultima = null; const falhas = [];
  for (const f of files) {
    try { const c = await deArquivo(f); await DB.guardar(c); ok++; ultima = c; }
    catch (e) { falhas.push(f.name + ': ' + e.message); }
  }
  avisar(ok + ' cifra(s) importada(s).' + (falhas.length ? '\nNão importei:\n• ' + falhas.join('\n• ') : ''));
  montarListas();
  if (ok === 1 && files.length === 1) abrir(ultima);
}
function dialogoColar(texto, tituloSugerido) {
  let [t, a] = tituloSugerido ? separar(tituloSugerido) : adivinharNome(texto || '');
  $('pTitulo').value = t; $('pArtista').value = a; $('pTexto').value = texto || '';
  $('dColar').showModal();
}
async function baixarLista(itens) {
  if (!itens.length) return avisar('A lista está vazia.');
  let ok = 0; const faltou = [];
  for (let i = 0; i < itens.length; i++) {
    const [a, m] = itens[i];
    avisar('Buscando ' + (i + 1) + ' de ' + itens.length + ': ' + m);
    try {
      const c = await procurar(a, m);
      if (c) { c.minha = true; await DB.guardar(c); ok++; } else faltou.push(m);
      await new Promise((r) => setTimeout(r, 700));
    } catch (e) { faltou.push(m + ' (' + e.message + ')'); if (!cfg.proxy) break; }
  }
  avisar('Lista: ' + ok + ' de ' + itens.length + ' cifras guardadas.' + (faltou.length ? '\nNão achei:\n• ' + faltou.join('\n• ') : ''));
  montarListas();
}

// ---------------- identificação pelo microfone ----------------
let ouvindo = false;
async function ouvir() {
  if (ouvindo) return null;
  if (!navigator.mediaDevices?.getUserMedia) throw new Error('este navegador não dá acesso ao microfone');
  ouvindo = true;
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const rec = new MediaRecorder(stream); const partes = [];
    rec.ondataavailable = (e) => partes.push(e.data);
    const inicio = Date.now();
    const parado = new Promise((r) => { rec.onstop = r; });
    rec.start();
    for (let s = 10; s > 0; s--) { info('🎤 Ouvindo… ' + s + ' s'); await new Promise((r) => setTimeout(r, 1000)); }
    rec.stop(); await parado; stream.getTracks().forEach((t) => t.stop());
    info('Identificando…');
    const fd = new FormData();
    if (cfg.token) fd.append('api_token', cfg.token);
    fd.append('return', 'apple_music,spotify');
    const tipo = rec.mimeType || 'audio/webm';
    fd.append('file', new Blob(partes, { type: tipo }), 'amostra.' + (tipo.includes('ogg') ? 'ogg' : tipo.includes('mp4') ? 'm4a' : 'webm'));
    const r = await fetch('https://api.audd.io/', { method: 'POST', body: fd });
    const j = await r.json();
    if (j.status === 'error') throw new Error((j.error && j.error.error_message) || 'erro no reconhecimento' + (cfg.token ? '' : ' (sem token: veja ⚙️)'));
    const res = j.result; if (!res) return null;
    const tc = (res.timecode || '').split(':').reduce((acc, p) => acc * 60 + (+p || 0), 0) * 1000;
    const dur = (res.spotify && res.spotify.duration_ms) || (res.apple_music && res.apple_music.durationInMillis) || 0;
    return { artista: res.artist, titulo: res.title, sinc: { dur, pos0: tc, t0: inicio, tocando: true } };
  } finally { ouvindo = false; }
}
let info = avisar;

// ---------------- tela da cifra ----------------
const L = { cifra: null, semitons: 0, fonte: 0, rolando: false, pos: 0, vel: 25, ultimo: 0, pausaAte: 0, sinc: null };
const rolo = $('rolo');

function abrir(c, sinc) {
  Object.assign(L, { cifra: c, semitons: 0, rolando: false, pos: 0, sinc: sinc || null });
  $('lTitulo').textContent = c.titulo; $('lArtista').textContent = c.artista ? '— ' + c.artista : '';
  $('leitor').classList.add('aberto'); document.body.style.overflow = 'hidden';
  rolo.scrollTop = 0;
  DB.guardar(c).then(montarListas);
  info = (t) => { $('lInfo').textContent = t || ''; };
  render();
  atualizar();
  if (sinc) setTimeout(() => (sinc.dur || sinc.pos0 ? sincronizar() : tocar()), 500);
  history.pushState({ leitor: 1 }, '');
}
function fechar() {
  L.rolando = false; $('leitor').classList.remove('aberto'); document.body.style.overflow = '';
  info = avisar; if (document.fullscreenElement) document.exitFullscreen();
}
async function render() {
  const c = L.cifra, pre = $('cifra'), pags = $('paginas');
  pags.innerHTML = '';
  if (c.pdf) {
    pre.style.display = 'none';
    if (!window.pdfjsLib) { pre.style.display = ''; pre.textContent = 'O leitor de PDF não carregou (sem internet?).'; return; }
    pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
    const doc = await pdfjsLib.getDocument({ data: new Uint8Array(await c.pdf.arrayBuffer()) }).promise;
    const largura = Math.min(rolo.clientWidth - 24, 1000) * (window.devicePixelRatio || 1);
    for (let i = 1; i <= Math.min(doc.numPages, 40); i++) {
      const pg = await doc.getPage(i);
      const v1 = pg.getViewport({ scale: 1 }), v = pg.getViewport({ scale: largura / v1.width });
      const cv = document.createElement('canvas'); cv.width = v.width; cv.height = v.height;
      await pg.render({ canvasContext: cv.getContext('2d'), viewport: v }).promise;
      pags.appendChild(cv);
    }
    return;
  }
  pre.style.display = '';
  const t = transporTexto(c.texto, L.semitons);
  const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  pre.innerHTML = esc(t).replace(new RegExp(INI + '([^' + FIM + ']*)' + FIM, 'g'), '<b>$1</b>').replace(new RegExp('[' + INI + FIM + ']', 'g'), '');
  if (L.fonte) { pre.style.fontSize = L.fonte + 'px'; return; }
  // fonte que faz a linha mais comprida caber na tela
  const maior = Math.max(20, ...t.replace(new RegExp('[' + INI + FIM + ']', 'g'), '').split('\n').map((l) => l.length));
  const cv = document.createElement('canvas').getContext('2d'); cv.font = '100px ' + getComputedStyle(pre).fontFamily;
  const px = 100 * (rolo.clientWidth - 24) / (cv.measureText('M').width * maior);
  pre.style.fontSize = Math.max(12, Math.min(26, px)) + 'px';
}
const conteudo = () => (L.cifra && L.cifra.pdf ? $('paginas') : $('cifra'));
const maxRolagem = () => Math.max(0, rolo.scrollHeight - rolo.clientHeight);
function quadro(agora) {
  const dt = L.ultimo ? (agora - L.ultimo) / 1000 : 0; L.ultimo = agora;
  if (L.rolando && $('leitor').classList.contains('aberto')) {
    if (Date.now() < L.pausaAte) L.pos = rolo.scrollTop;
    else { L.pos = Math.min(maxRolagem(), L.pos + L.vel * dt); rolo.scrollTop = L.pos; }
  }
  requestAnimationFrame(quadro);
}
requestAnimationFrame(quadro);
['wheel', 'touchstart', 'touchmove', 'mousedown'].forEach((ev) => rolo.addEventListener(ev, () => { L.pausaAte = Date.now() + 2500; }, { passive: true }));
function tocar() { L.rolando = true; L.pos = rolo.scrollTop; atualizar(); }
function pausar() { L.rolando = false; atualizar(); }
function sincronizar() {
  const s = L.sinc; if (!s) return tocar();
  const el = conteudo(), altura = el.offsetHeight - (el === $('cifra') ? parseFloat(getComputedStyle(el).paddingBottom) : 0);
  if (s.dur > 0 && altura > 0) {
    const p = s.pos0 + (Date.now() - s.t0);
    L.vel = altura * 1000 / s.dur;
    L.pos = Math.max(0, Math.min(maxRolagem(), altura * p / s.dur - rolo.clientHeight / 3));
    rolo.scrollTop = L.pos;
  }
  tocar();
}
function atualizar() {
  $('cPlay').textContent = L.rolando ? '⏸' : '▶';
  $('cVel').textContent = Math.round(L.vel) + ' px/s';
  $('cTom').textContent = 'Tom ' + (L.semitons ? (L.semitons > 0 ? '+' : '') + L.semitons : 'original');
  $('cEstrela').textContent = L.cifra && L.cifra.minha ? '★' : '☆';
  const s = L.sinc;
  info(!s ? 'rolagem manual · espaço: rolar · ↑↓ velocidade · +/− tom · role com o mouse para ajustar'
    : s.dur ? 'sincronizado pelo microfone' : 'identificada');
}
function mudarTom(d) {
  if (L.cifra.pdf) return info('PDF: não dá para mudar o tom. Cole o texto da cifra para isso.');
  const rel = maxRolagem() ? rolo.scrollTop / maxRolagem() : 0;
  L.semitons = Math.max(-11, Math.min(11, L.semitons + d)); render();
  requestAnimationFrame(() => { L.pos = rel * maxRolagem(); rolo.scrollTop = L.pos; }); atualizar();
}
function mudarFonte(d) {
  if (L.cifra.pdf) return info('PDF: use o zoom do navegador (Ctrl + / Ctrl −).');
  L.fonte = Math.max(9, Math.min(40, (L.fonte || parseFloat($('cifra').style.fontSize) || 16) + d)); render();
}
function mudarVel(f) { L.vel = Math.max(3, Math.min(400, L.vel * f)); atualizar(); }

// ---------------- ligações ----------------
$('bBuscar').onclick = () => buscar($('edArtista').value.trim(), $('edMusica').value.trim());
$('edMusica').onkeydown = $('edArtista').onkeydown = (e) => { if (e.key === 'Enter') $('bBuscar').click(); };
$('bPesquisar').onclick = () => {
  const q = ($('edArtista').value + ' ' + $('edMusica').value).trim();
  window.open('https://www.google.com/search?q=' + encodeURIComponent('site:' + DOMINIO + ' ' + q), '_blank');
  avisar('Abra a cifra certa na outra aba, copie o endereço (link) e cole no campo Música → 🔎 Buscar.');
};
$('bOuvir').onclick = async () => {
  try {
    const r = await ouvir();
    if (!r) return avisar('Não reconheci a música. Tente com o som mais alto ou mais perto.');
    avisar('Achei: ' + r.titulo + ' — ' + r.artista);
    $('edMusica').value = r.titulo; $('edArtista').value = r.artista;
    buscar(r.artista, r.titulo, r.sinc);
  } catch (e) { avisar('Erro na identificação: ' + e.message); }
};
$('bImportar').onclick = () => $('arquivos').click();
$('arquivos').onchange = (e) => { importarArquivos([...e.target.files]); e.target.value = ''; };
$('bColar').onclick = async () => { let t = ''; try { t = await navigator.clipboard.readText(); } catch {} dialogoColar(t); };
$('pSalvar').onclick = async () => {
  const txt = $('pTexto').value; if (!txt.trim()) return;
  const c = deTexto($('pTitulo').value, $('pArtista').value, txt);
  await DB.guardar(c); $('dColar').close(); montarListas(); abrir(c);
};
$('pCancelar').onclick = () => $('dColar').close();
$('bLista').onclick = async () => { let t = ''; try { t = await navigator.clipboard.readText(); } catch {} $('lTexto').value = t; $('dLista').showModal(); };
$('bListaArq').onclick = () => $('arquivoLista').click();
$('arquivoLista').onchange = async (e) => { const f = e.target.files[0]; e.target.value = ''; if (!f) return; $('lTexto').value = await f.text(); $('dLista').showModal(); };
$('lBuscar').onclick = () => { $('dLista').close(); baixarLista(lista($('lTexto').value)); };
$('lCancelar').onclick = () => $('dLista').close();

// backup das minhas cifras (para levar para outro navegador)
$('bExportar').onclick = async () => {
  const todas = (await DB.todas()).filter((c) => c.minha);
  const saida = [];
  for (const c of todas) {
    const x = { ...c };
    if (c.pdf) x.pdf = await new Promise((ok) => { const r = new FileReader(); r.onload = () => ok(r.result); r.readAsDataURL(c.pdf); });
    saida.push(x);
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([JSON.stringify({ violeiro: 1, cifras: saida })], { type: 'application/json' }));
  a.download = 'minhas-cifras.json'; a.click();
  avisar(saida.length + ' cifra(s) no backup.');
};
$('bRestaurar').onclick = () => $('arquivoBackup').click();
$('arquivoBackup').onchange = async (e) => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  try {
    const j = JSON.parse(await f.text()); let n = 0;
    for (const c of j.cifras || []) {
      if (typeof c.pdf === 'string') c.pdf = await (await fetch(c.pdf)).blob();
      await DB.guardar(c); n++;
    }
    avisar(n + ' cifra(s) restaurada(s).'); montarListas();
  } catch (er) { avisar('Backup inválido: ' + er.message); }
};

// configurações
$('cfgToken').value = cfg.token; $('cfgProxy').value = cfg.proxy;
$('cfgToken').onchange = () => localStorage.setItem('token', $('cfgToken').value.trim());
$('cfgProxy').onchange = () => localStorage.setItem('proxy', $('cfgProxy').value.trim());

// controles do leitor
$('cFechar').onclick = () => history.back();
$('cPlay').onclick = () => (L.rolando ? pausar() : tocar());
$('cDevagar').onclick = () => mudarVel(0.85);
$('cRapido').onclick = () => mudarVel(1.18);
$('cMenos').onclick = () => mudarFonte(-1);
$('cMais').onclick = () => mudarFonte(1);
$('cBemol').onclick = () => mudarTom(-1);
$('cSust').onclick = () => mudarTom(1);
$('cTela').onclick = () => (document.fullscreenElement ? document.exitFullscreen() : $('leitor').requestFullscreen());
$('cEstrela').onclick = async () => {
  const c = L.cifra;
  if (c.minha && c.url.startsWith('local:')) return info('Cifra sua: para apagar, use 🗑️ na tela inicial.');
  c.minha = !c.minha; await DB.guardar(c); atualizar(); montarListas();
};
$('cOuvir').onclick = async () => {
  try {
    const r = await ouvir(); if (!r) return info('Não reconheci a música.');
    const mesma = chave(r.artista, r.titulo) === chave(L.cifra.artista, L.cifra.titulo) || combina(L.cifra, r.artista, r.titulo);
    if (mesma) { L.sinc = r.sinc; sincronizar(); atualizar(); }
    else buscar(r.artista, r.titulo, r.sinc);
  } catch (e) { info('Erro: ' + e.message); }
};
window.addEventListener('popstate', () => { if ($('leitor').classList.contains('aberto')) fechar(); });
document.addEventListener('keydown', (e) => {
  if (!$('leitor').classList.contains('aberto') || e.target.matches('input, textarea')) return;
  const k = e.key;
  if (k === ' ') { e.preventDefault(); $('cPlay').click(); }
  else if (k === 'ArrowUp') { e.preventDefault(); mudarVel(1.18); }
  else if (k === 'ArrowDown') { e.preventDefault(); mudarVel(0.85); }
  else if (k === '+' || k === '=') mudarTom(1);
  else if (k === '-') mudarTom(-1);
  else if (k === 'f' || k === 'F') $('cTela').click();
  else if (k === 'Escape' && !document.fullscreenElement) history.back();
  else if (k === 'Home') { rolo.scrollTop = 0; L.pos = 0; }
});
window.addEventListener('resize', () => { if (L.cifra && !L.cifra.pdf && !L.fonte && $('leitor').classList.contains('aberto')) render(); });

// arrastar arquivos e colar texto direto na página
document.addEventListener('dragover', (e) => e.preventDefault());
document.addEventListener('drop', (e) => { e.preventDefault(); if (e.dataTransfer.files.length) importarArquivos([...e.dataTransfer.files]); });
document.addEventListener('paste', (e) => {
  if (e.target.matches('input, textarea') || $('leitor').classList.contains('aberto')) return;
  const t = e.clipboardData.getData('text');
  if (/^https?:\/\/\S+$/.test(t.trim())) importarLink(t.trim());
  else if (t.split('\n').length >= 4) dialogoColar(t);
});

// iniciar
DB.abrir().then(montarListas).catch((e) => avisar('Este navegador bloqueou o armazenamento: ' + e.message));
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
