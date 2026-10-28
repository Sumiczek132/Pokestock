package com.pokestock.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pokestock.MainActivity
import com.pokestock.R
import com.pokestock.work.Schedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Price alerts on wanted cards: checked after every price update. */
object Alerts {
    fun check(ctx: Context) {
        val prefs = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        for ((k, target) in AppState.store.activeAlerts()) {
            val card = AppState.catalog.byKey[k.substringBefore('|')] ?: continue
            val v = k.substringAfter('|')
            val now = AppState.prices.market(card, v) ?: continue
            if (now > target) continue
            if (prefs.getString(k, "") == today) continue
            prefs.edit().putString(k, today).apply()
            Schedule.notify(ctx, "${card.name} is ${pounds(now)}", "${card.set.name} · ${Labels.variantLabel(card, v)} dropped to your alert price of ${pounds(target)}.", k.hashCode())
        }
    }
}

/** Plain-text lists to send to a shop or a trading group. */
object ShareText {
    fun wanted(): String {
        val sb = StringBuilder("My want list (Pokestock)\n")
        val cat = AppState.catalog
        val slot = AppState.store.doc.slotWants.filter { it.value.v }.keys.mapNotNull { cat.byKey[it.substringAfter('|')] }.filter { AppState.store.total(it.key) == 0 }
        val wish = AppState.store.wishList().mapNotNull { cat.byKey[it] }
        (slot + wish).distinctBy { it.key }.sortedWith(compareBy({ it.set.name }, { it.localId })).forEach { c ->
            sb.append("• ").append(c.name).append(" – ").append(c.set.name).append(" ").append(c.numberLabel)
            if (c.set.lang == "JA") sb.append(" (Japanese)")
            AppState.lowPrice(c)?.let { sb.append(" ~").append(pounds(it)) }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun missing(set: CardSet, mode: String): String {
        val sb = StringBuilder("Missing from ${set.name} (${Binder.modeName(mode)})\n")
        Binder.pockets(set, mode).filter { !it.owned }.forEach { pk ->
            sb.append(pk.card.localId).append(" ").append(pk.card.name)
            if (mode != "base") sb.append(" – ").append(Labels.variantLabel(pk.card, pk.variant))
            sb.append('\n')
        }
        return sb.toString()
    }

    fun send(ctx: Context, text: String, title: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, title)
        ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Home screen widget: collection value and change since yesterday. */
class ValueWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = WidgetData.render(ctx, mgr, ids)
}

object WidgetData {
    /** Saves today's value (and keeps yesterday's for the change), then redraws any widgets. */
    fun publish(ctx: Context) {
        if (!AppState.ready || AppState.catalog.sets.isEmpty()) return
        val prefs = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val value = AppState.portfolioValue()
        val e = prefs.edit()
        if (prefs.getString("date", "") != today) {
            e.putLong("prev", prefs.getLong("value", value)).putString("date", today)
        }
        e.putLong("value", value).putString("prices", AppState.prices.date).apply()
        val mgr = AppWidgetManager.getInstance(ctx)
        render(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, ValueWidget::class.java)))
    }

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val prefs = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val value = prefs.getLong("value", -1)
        val prev = prefs.getLong("prev", value)
        val diff = value - prev
        val pd = prefs.getString("prices", "") ?: ""
        val pi = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        for (id in ids) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_value)
            rv.setTextViewText(R.id.w_value, if (value < 0) "Open Pokestock" else poundsRound(value))
            rv.setTextViewText(R.id.w_change, if (value < 0) "" else (if (diff >= 0) "▲ " else "▼ ") + pounds(Math.abs(diff)) + " today")
            rv.setTextColor(R.id.w_change, if (diff >= 0) 0xFF45CF86.toInt() else 0xFFFF7466.toInt())
            rv.setTextViewText(R.id.w_date, pd.takeIf { it.isNotEmpty() }?.let { "Prices " + LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM")) } ?: "")
            rv.setOnClickPendingIntent(R.id.w_root, pi)
            mgr.updateAppWidget(id, rv)
        }
    }
}
