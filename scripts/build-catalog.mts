// Builds the Pokestock card catalogue from a checkout of tcgdex/cards-database.
// Usage: npx tsx scripts/build-catalog.mts <cards-database dir> <out.json> [datas.json]
// Output is compact JSON the app loads into SQLite.
import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const [, , dbDir, outFile, assetsFile] = process.argv
if (!dbDir || !outFile) { console.error('usage: build-catalog <db dir> <out> [datas.json]'); process.exit(1) }

const species: string[] = JSON.parse(fs.readFileSync(path.join(path.dirname(new URL(import.meta.url).pathname), '..', 'data', 'species.json'), 'utf8'))
const jaNames: Record<string, string> = JSON.parse(fs.readFileSync(path.join(path.dirname(new URL(import.meta.url).pathname), '..', 'data', 'ja-set-names.json'), 'utf8'))
let assets: any = null
if (assetsFile && fs.existsSync(assetsFile)) assets = JSON.parse(fs.readFileSync(assetsFile, 'utf8'))

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
    for (const setFile of fs.readdirSync(serieDir).filter(f => f.endsWith('.ts'))) {
      const setDir = path.join(serieDir, setFile.slice(0, -3))
      if (!fs.existsSync(setDir)) continue
      let set: any
      try { set = await load(path.join(serieDir, setFile)) } catch (e) { console.error('set', setFile, e); continue }
      const setName = set.name?.[nameKey]
      if (!setName) continue // not released in this language
      const date = typeof set.releaseDate === 'string' ? set.releaseDate : (set.releaseDate?.[nameKey] || '')
      const files = fs.readdirSync(setDir).filter(f => f.endsWith('.ts'))
      const rows: any[] = []
      for (const cf of files) {
        let c: any
        try { c = await load(path.join(setDir, cf)) } catch (e) { console.error('card', setDir, cf, String(e).slice(0, 120)); continue }
        const localId = cf.slice(0, -3)
        const native = c.name?.[nameKey]
        if (!native) continue
        const dex = Array.isArray(c.dexId) ? (c.dexId[0] || 0) : 0
        let name = native
        if (lang === 'JA') {
          name = dex && dex <= species.length ? species[dex - 1] + (/ex$/i.test(native) ? ' ex' : '') : native
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
          vs.push([k, v.thirdParty?.cardmarket || 0])
        }
        if (!vs.length) {
          const r = String(c.rarity || '')
          vs.push([/^(common|uncommon|none|)$/i.test(r) ? 'normal' : 'holo', c.thirdParty?.cardmarket || 0])
        }
        const img = assets ? (assets[nameKey]?.[serie.id]?.[set.id]?.[localId] ? 1 : 0) : 1
        rows.push({ localId, name, rarity: c.rarity || '', dex, cat: c.category || '', vs, ja: lang === 'JA' && name !== native ? native : '', img })
      }
      if (!rows.length) continue
      rows.sort((a, b) => localSort(a.localId, b.localId))
      const si = sets.length
      sets.push({
        k: `${lang}:${set.id}`, lang, id: set.id, name: lang === 'JA' && jaNames[set.id] ? jaNames[set.id] : setName, nameEn: set.name?.en || (lang === 'JA' ? setName : ''),
        series: lang === 'EN' ? (serie.name?.en || serie.id) : (serie.name?.ja || serie.id), serieId: serie.id,
        date, total: set.cardCount?.official || 0, abbr: set.abbreviations?.official || '', cm: set.thirdParty?.cardmarket || 0,
      })
      for (const r of rows) cards.push([si, r.localId, r.name, r.rarity, r.dex, r.cat, r.vs, r.ja, r.img])
    }
  }
}

await walk(path.join(dbDir, 'data'), 'EN')
await walk(path.join(dbDir, 'data-asia'), 'JA')
const out = { v: 1, built: new Date().toISOString(), sets, cards }
fs.writeFileSync(outFile, JSON.stringify(out))
console.log(`${sets.length} sets, ${cards.length} cards (${sets.filter(s => s.lang === 'EN').length} EN sets, ${sets.filter(s => s.lang === 'JA').length} JA sets)`)
