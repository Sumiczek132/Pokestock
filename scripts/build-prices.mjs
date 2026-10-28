// Downloads Cardmarket's public Pokémon price guide and the ECB EUR→GBP rate,
// keeps only products in the catalogue, writes today's prices in pence, and
// rolls them into a bounded price history (35 daily points + 52 weekly points).
// Usage: node scripts/build-prices.mjs <catalog.json> <prices-out.json> [history-prev.json] [history-out.json]
import fs from 'node:fs'

const [, , catalogFile, outFile, prevHistFile, histOutFile] = process.argv
const catalog = JSON.parse(fs.readFileSync(catalogFile, 'utf8'))
const wanted = new Set()
for (const c of catalog.cards) for (const [, cm] of c[6]) if (cm) wanted.add(cm)

const ecb = await (await fetch('https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml')).text()
const m = ecb.match(/currency='GBP' rate='([\d.]+)'/)
if (!m) throw new Error('No GBP rate from ECB')
const gbp = Number(m[1])

const guide = await (await fetch('https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_6.json')).json()
if (guide.version !== 1) throw new Error('Unexpected price guide version ' + guide.version)

const p = v => (typeof v === 'number' && v > 0) ? Math.round(v * gbp * 100) : 0
const prices = {}
for (const g of guide.priceGuides) {
  if (!wanted.has(g.idProduct)) continue
  // [trend, avg30, low, trendHolo, avg30Holo, lowHolo] in pence
  prices[g.idProduct] = [p(g.trend), p(g.avg30), p(g.low), p(g['trend-holo']), p(g['avg30-holo']), p(g['low-holo'])]
}
const date = (guide.createdAt || new Date().toISOString()).slice(0, 10)
fs.writeFileSync(outFile, JSON.stringify({ v: 1, date, createdAt: guide.createdAt, eurGbp: gbp, prices }))
console.log(`${Object.keys(prices).length} priced products, EUR→GBP ${gbp}, guide ${guide.createdAt}`)

if (histOutFile) {
  let h = { v: 1, days: [], d: {}, weeks: [], w: {} }
  if (prevHistFile && fs.existsSync(prevHistFile)) {
    try { h = JSON.parse(fs.readFileSync(prevHistFile, 'utf8')) } catch { console.log('Previous history unreadable, starting fresh') }
  }
  const push = (dates, series, keep, when) => {
    const replace = dates.length && dates[dates.length - 1] === when
    if (!replace) dates.push(when)
    const n = dates.length
    const ids = new Set([...Object.keys(series), ...Object.keys(prices)])
    for (const id of ids) {
      const s = series[id] || [[], []]
      while (s[0].length < n - (replace ? 0 : 1)) { s[0].unshift(0); s[1].unshift(0) }
      const cur = prices[id] || [0, 0, 0, 0, 0, 0]
      if (replace) { s[0][n - 1] = cur[0]; s[1][n - 1] = cur[3] } else { s[0].push(cur[0]); s[1].push(cur[3]) }
      series[id] = s
    }
    const drop = Math.max(0, dates.length - keep)
    if (drop) {
      dates.splice(0, drop)
      for (const id in series) { series[id][0].splice(0, drop); series[id][1].splice(0, drop) }
    }
    for (const id in series) if (!series[id][0].some(Boolean) && !series[id][1].some(Boolean)) delete series[id]
  }
  push(h.days, h.d, 35, date)
  // one weekly point per ISO week, dated by the latest day seen that week
  const wk = d => { const t = new Date(d + 'T12:00:00Z'); const day = (t.getUTCDay() + 6) % 7; t.setUTCDate(t.getUTCDate() - day); return t.toISOString().slice(0, 10) }
  const lastWeek = h.weeks.length ? wk(h.weeks[h.weeks.length - 1]) : ''
  if (lastWeek === wk(date)) h.weeks[h.weeks.length - 1] = date
  push(h.weeks, h.w, 52, date)
  fs.writeFileSync(histOutFile, JSON.stringify(h))
  console.log(`history: ${h.days.length} days, ${h.weeks.length} weeks, ${Object.keys(h.d).length} products`)
}
