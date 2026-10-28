// Builds the Pokestock card catalogue from a checkout of tcgdex/cards-database.
// Usage: npx tsx scripts/build-catalog.mts <cards-database dir> <out.json> [datas.json] [pokemon-tcg-data dir]
// Pictures come from TCGdex; English cards TCGdex has no picture for fall back to pokemontcg.io.
// Output is compact JSON the app loads into SQLite.
import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const [, , dbDir, outFile, assetsFile, ptcgDir, cmProductsFile] = process.argv
if (!dbDir || !outFile) { console.error('usage: build-catalog <db dir> <out> [datas.json]'); process.exit(1) }

const species: string[] = JSON.parse(fs.readFileSync(path.join(path.dirname(new URL(import.meta.url).pathname), '..', 'data', 'species.json'), 'utf8'))
const jaNames: Record<string, string> = JSON.parse(fs.readFileSync(path.join(path.dirname(new URL(import.meta.url).pathname), '..', 'data', 'ja-set-names.json'), 'utf8'))
let assets: any = null
if (assetsFile && fs.existsSync(assetsFile)) assets = JSON.parse(fs.readFileSync(assetsFile, 'utf8'))

// Second picture source (English only): pokemontcg.io set and card data.
const ptcgSets: any[] = ptcgDir && fs.existsSync(path.join(ptcgDir, 'sets', 'en.json')) ? JSON.parse(fs.readFileSync(path.join(ptcgDir, 'sets', 'en.json'), 'utf8')) : []
const norm = (x: string) => x.toLowerCase().replace(/&/g, 'and').replace(/pokémon/g, 'pokemon').replace(/[^a-z0-9]/g, '')
const ptcgByName = new Map(ptcgSets.map(s => [norm(s.name), s]))
const ptcgById = new Map(ptcgSets.map(s => [s.id, s]))
const PTCG_ALIAS: Record<string, string> = { '30th-c': 'me55c', '30th': 'me55', 'mee': 'mee', 'sve': 'sve' }
function ptcgSetFor(id: string, name: string) {
  const guess = id.replace('.', 'pt').replace(/^sv0(\d)/, 'sv$1').replace(/^me0(\d)/, 'me$1')
  return ptcgById.get(PTCG_ALIAS[id] || '') || ptcgByName.get(norm(name)) || ptcgById.get(guess) || ptcgById.get(id)
}
const numKey = (n: string) => String(n).toUpperCase().replace(/^([A-Z]*)0*(\d)/, '$1$2')
function ptcgImages(setId: string): Map<string, [string, string]> {
  const m = new Map<string, [string, string]>()
  const f = ptcgDir ? path.join(ptcgDir, 'cards', 'en', setId + '.json') : ''
  if (!f || !fs.existsSync(f)) return m
  for (const c of JSON.parse(fs.readFileSync(f, 'utf8'))) if (c.images?.small) {
    const pics: [string, string] = [c.images.small, c.images.large || c.images.small]
    m.set(numKey(c.number), pics)
    // Also by name, for sets numbered differently in the two sources; a name seen twice can't be trusted.
    const nk = 'name:' + norm(c.name)
    m.set(nk, m.has(nk) ? ['', ''] : pics)
  }
  return m
}

// Cardmarket's product list gives English names for Japanese cards (trainers, items, named Pokémon).
const cmNames = new Map<number, string>()
const cmExp = new Map<number, number>()                    // product -> Cardmarket expansion
const cmByExpName = new Map<string, number[]>()            // "expansion|base name" -> products
if (cmProductsFile && fs.existsSync(cmProductsFile)) {
  try {
    const j = JSON.parse(fs.readFileSync(cmProductsFile, 'utf8'))
    for (const p of (j.products || j)) if (p?.idProduct && p?.name) {
      cmNames.set(p.idProduct, String(p.name))
      if (p.idExpansion) {
        cmExp.set(p.idProduct, p.idExpansion)
        const key = p.idExpansion + '|' + baseName(String(p.name))
        if (!cmByExpName.has(key)) cmByExpName.set(key, [])
        cmByExpName.get(key)!.push(p.idProduct)
      }
    }
  } catch (e) { console.error('Cardmarket product list unreadable', String(e).slice(0, 100)) }
}
function baseName(n: string) { return n.replace(/\s*\[[^\]]*\]/g, '').replace(/\s*\([^)]*\)/g, '').trim().toLowerCase() }
/** The card number Cardmarket puts in a product name, e.g. "Quaxly (SSP 050)" -> 50. */
function cmNumber(n: string): number | null { const m = n.match(/\(([A-Z0-9-]+\s+)?([A-Z]*)0*(\d+)\)\s*$/); return m ? Number(m[3]) : null }
let cmChecked = 0, cmFixed = 0
const isPlain = (k: string) => !k.includes('+') && !k.includes('~')
/**
 * Cardmarket often has two products with the same name in a set (the normal card plus a later-listed stamped or
 * promo version) and the card database sometimes points the normal card at the wrong one (Quaxly SSP 050 showed
 * £12.89 instead of 3p). Cardmarket lists a set's cards in number order, so the right product is the one whose id
 * sits next to the neighbouring cards' ids.
 */
function fixSiblings(rows: any[], setCm: number) {
  if (!setCm || !cmExp.size) return
  const plainCm = (r: any) => { const v = r?.vs?.find((x: any) => isPlain(x[0]) && x[1]); return v ? v[1] as number : 0 }
  for (let i = 0; i < rows.length; i++) {
    const cm = plainCm(rows[i]); if (!cm || cmExp.get(cm) !== setCm) continue
    const sibs = (cmByExpName.get(setCm + '|' + baseName(cmNames.get(cm) || '')) || []).filter(id => id !== cm)
    if (!sibs.length) continue
    const near: number[] = []
    for (const j of [i - 2, i - 1, i + 1, i + 2]) { const n = plainCm(rows[j]); if (n && cmExp.get(n) === setCm) near.push(n) }
    if (near.length < 2) continue
    const dist = (id: number) => Math.min(...near.map(n => Math.abs(n - id)))
    const best = sibs.reduce((a, b) => dist(a) <= dist(b) ? a : b)
    if (dist(cm) > 20 && dist(best) <= 4) {
      for (const v of rows[i].vs) {
        if (isPlain(v[0]) && v[1] === cm) v[1] = best
        else if (!isPlain(v[0]) && v[1] === best) v[1] = cm   // the stamped version gets the other product
      }
      cmFixed++
    }
  }
}
/**
 * The card database sometimes points a version at the wrong Cardmarket product (e.g. a stamped promo instead of
 * the normal card, so a 5p common shows £12). If the product's number or expansion doesn't match the card,
 * look for the right product in the set's own expansion by name and number.
 */
function checkCm(cm: number, localId: string, cardName: string, setCm: number): number {
  if (!cm || !cmNames.size) return cm
  const n = Number((localId.match(/(\d+)/) || [])[1])
  if (!n) return cm
  cmChecked++
  const pname = cmNames.get(cm) || ''
  const pn = cmNumber(pname)
  const exp = cmExp.get(cm)
  // Keep it only when Cardmarket confirms it: same expansion and same number. Products missing from the
  // singles list (e.g. promo/stamped versions) count as unconfirmed.
  const wrongNumber = pn === null || pn !== n
  const wrongExp = setCm > 0 && exp !== setCm
  if (!wrongNumber && !wrongExp) return cm
  if (!setCm) return cm
  const cands = (cmByExpName.get(setCm + '|' + baseName(cardName)) || cmByExpName.get(setCm + '|' + baseName(pname)) || [])
    .filter(id => id !== cm && cmNumber(cmNames.get(id) || '') === n)
  if (cands.length) { cmFixed++; return cands[0] }
  return cm
}
// "Buddy-Buddy Poffin (M1S 081)" -> "Buddy-Buddy Poffin"; "Lopunny [Dashing Kick | Spiral Kick]" -> "Lopunny"
const cleanCm = (n: string) => n.replace(/\s*\[[^\]]*\]\s*$/, '').replace(/\s*\((?=[^)]*\d)[^)]*\)\s*$/, '').replace(/\s*-\s*[A-Z0-9-]+\s*\d+\s*$/, '').trim()
let jaNamed = 0

type Lang = 'EN' | 'JA'
const sets: any[] = []
const cards: any[] = []
const load = async (f: string) => (await import(pathToFileURL(path.resolve(f)).href)).default

function variantKey(v: any): string {
  let k = String(v.type || 'normal')
  if (v.foil) k += '-' + v.foil
  const stamps: string[] = Array.isArray(v.stamp) ? v.stamp : v.stamp ? [v.stamp] : []
  for (const s of stamps) k += '+' + s
  if (v.size && v.size !== 'standard') k += '~' + v.size
  return k
}
const localSort = (a: string, b: string) => {
  const na = /^\d+$/.test(a), nb = /^\d+$/.test(b)
  if (na && nb) return Number(a) - Number(b)
  if (na) return -1
  if (nb) return 1
  return a.localeCompare(b, 'en', { numeric: true })
}

async function walk(root: string, lang: Lang) {
  const nameKey = lang === 'EN' ? 'en' : 'ja'
  for (const serieFile of fs.readdirSync(root).filter(f => f.endsWith('.ts'))) {
    const serieDir = path.join(root, serieFile.slice(0, -3))
    if (!fs.existsSync(serieDir)) continue
    let serie: any
    try { serie = await load(path.join(root, serieFile)) } catch (e) { console.error('serie', serieFile, e); continue }
    if (serie.id === 'tcgp') continue // Pokémon TCG Pocket: digital only, not real cards
    for (const setFile of fs.readdirSync(serieDir).filter(f => f.endsWith('.ts'))) {
      const setDir = path.join(serieDir, setFile.slice(0, -3))
      if (!fs.existsSync(setDir)) continue
      let set: any
      try { set = await load(path.join(serieDir, setFile)) } catch (e) { console.error('set', setFile, e); continue }
      const setName = set.name?.[nameKey]
      if (!setName) continue // not released in this language
      const date = typeof set.releaseDate === 'string' ? set.releaseDate : (set.releaseDate?.[nameKey] || '')
      const files = fs.readdirSync(setDir).filter(f => f.endsWith('.ts'))
      const pset = lang === 'EN' ? ptcgSetFor(set.id, setName) : undefined
      const pimg = pset ? ptcgImages(pset.id) : new Map()
      const rows: any[] = []
      for (const cf of files) {
        let c: any
        try { c = await load(path.join(setDir, cf)) } catch (e) { console.error('card', setDir, cf, String(e).slice(0, 120)); continue }
        const localId = cf.slice(0, -3)
        const native = c.name?.[nameKey]
        if (!native) continue
        const dex = Array.isArray(c.dexId) ? Math.floor(Number(c.dexId[0]) || 0) : 0
        let name = native
        if (lang === 'JA') {
          name = dex >= 1 && dex <= species.length ? species[dex - 1] + (/ex$/i.test(native) ? ' ex' : '') : native
          const cmId = (Array.isArray(c.variants) ? c.variants.find((v: any) => v?.thirdParty?.cardmarket)?.thirdParty?.cardmarket : 0) || c.thirdParty?.cardmarket
          const en = cmId ? cmNames.get(cmId) : undefined
          if (en && /[A-Za-z]/.test(en)) { name = cleanCm(en) || name; jaNamed++ }
        }
        const vs: [string, number][] = []
        const seen = new Set<string>()
        let variants: any[] = []
        if (Array.isArray(c.variants)) variants = c.variants
        else if (c.variants && typeof c.variants === 'object') {
          const map: Record<string, string> = { normal: 'normal', reverse: 'reverse', holo: 'holo', firstEdition: '1st-edition', wPromo: 'promo-stamp' }
          for (const [k, on] of Object.entries(c.variants)) if (on && map[k]) variants.push({ type: map[k] })
        }
        for (const v of variants) {
          const k = variantKey(v)
          if (seen.has(k)) continue
          seen.add(k)
          const plain = !k.includes('+') && !k.includes('~')
          vs.push([k, plain ? checkCm(v.thirdParty?.cardmarket || 0, localId, native, set.thirdParty?.cardmarket || 0) : (v.thirdParty?.cardmarket || 0)])
        }
        if (!vs.length) {
          const r = String(c.rarity || '')
          vs.push([/^(common|uncommon|none|)$/i.test(r) ? 'normal' : 'holo', c.thirdParty?.cardmarket || 0])
        }
        let img: number | [string, string] = assets ? (assets[nameKey]?.[serie.id]?.[set.id]?.[localId] ? 1 : 0) : 1
        if (img === 0 && pimg.size) {
          const byNum = pimg.get(numKey(localId)), byName = pimg.get('name:' + norm(native))
          img = (byNum && byName && byNum[0] !== byName[0] && byName[0]) ? byName : (byNum || (byName && byName[0] ? byName : 0))
        }
        rows.push({ localId, name, rarity: c.rarity || '', dex, cat: c.category || '', vs, ja: lang === 'JA' && name !== native ? native : '', img })
      }
      if (!rows.length) continue
      rows.sort((a, b) => localSort(a.localId, b.localId))
      fixSiblings(rows, set.thirdParty?.cardmarket || 0)
      const si = sets.length
      sets.push({
        k: `${lang}:${set.id}`, lang, id: set.id, name: lang === 'JA' && jaNames[set.id] ? jaNames[set.id] : setName, nameEn: set.name?.en || (lang === 'JA' ? setName : ''),
        series: lang === 'EN' ? (serie.name?.en || serie.id) : (serie.name?.ja || serie.id), serieId: serie.id,
        date, total: set.cardCount?.official || 0, abbr: set.abbreviations?.official || '', cm: set.thirdParty?.cardmarket || 0,
        logo: pset?.images?.logo || (assets?.[nameKey]?.[serie.id]?.[set.id]?.logo ? `https://assets.tcgdex.net/${nameKey}/${serie.id}/${set.id}/logo.webp` : ''),
        symbol: pset?.images?.symbol || (assets?.univ?.[serie.id]?.[set.id]?.symbol ? `https://assets.tcgdex.net/univ/${serie.id}/${set.id}/symbol.webp` : ''),
      })
      for (const r of rows) cards.push([si, r.localId, r.name, r.rarity, r.dex, r.cat, r.vs, r.ja, r.img])
    }
  }
}

await walk(path.join(dbDir, 'data'), 'EN')
await walk(path.join(dbDir, 'data-asia'), 'JA')
console.log(`Cardmarket links checked: ${cmChecked}, corrected: ${cmFixed}`)
const out = { v: 1, built: new Date().toISOString(), stats: { cmChecked, cmFixed, cmProducts: cmNames.size }, sets, cards }
fs.writeFileSync(outFile, JSON.stringify(out))
console.log(`Japanese cards named from Cardmarket: ${jaNamed}`)
const sample = cards.filter(c => sets[c[0]].lang === 'JA' && c[7]).slice(0, 8).map(c => c[2] + ' / ' + c[7])
console.log('sample:', sample.join(' | '))
const pics = cards.filter(c => c[8] === 1).length, alt = cards.filter(c => Array.isArray(c[8])).length
console.log(`pictures: ${pics} TCGdex, ${alt} pokemontcg.io, ${cards.length - pics - alt} none`)
console.log(`${sets.length} sets, ${cards.length} cards (${sets.filter(s => s.lang === 'EN').length} EN sets, ${sets.filter(s => s.lang === 'JA').length} JA sets)`)
