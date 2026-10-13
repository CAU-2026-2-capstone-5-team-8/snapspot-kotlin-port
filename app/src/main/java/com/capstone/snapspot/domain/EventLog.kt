package com.capstone.snapspot.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Device event log for field measurements. Timestamps are epoch milliseconds. The JSON form is the
 * same as the React Native app's export, so the existing metrics scripts can read both.
 */
enum class EventKind(val wire: String) {
    GEOFENCE_ENTER("geofence_enter"),
    GEOFENCE_EXIT("geofence_exit"),
    REPLAN("replan"),
    NOTIFY_SHOWN("notify_shown"),
    NOTIFY_SUPPRESSED("notify_suppressed"),
    ;

    companion object {
        fun fromWire(s: String?): EventKind? = entries.firstOrNull { it.wire == s }
    }
}

/** Detail values are strings, numbers, booleans or null. */
typealias EventDetail = Map<String, Any?>

data class LogEvent(
    val t: Long,
    val kind: EventKind,
    /** Place id, or branch region id ("b|<brandKey>|<kakaoId>"), or the refresh region id. */
    val id: String? = null,
    val detail: EventDetail? = null,
)

/** Oldest events fall off first; ~500 events is a day of walking with room to spare. */
const val MAX_EVENTS = 500

fun appendCapped(list: List<LogEvent>, event: LogEvent, max: Int = MAX_EVENTS): List<LogEvent> {
    val next = list + event
    return if (next.size > max) next.takeLast(max) else next
}

private val json = Json

/** Reads a stored log, dropping anything malformed instead of failing. */
fun parseLog(raw: String?): List<LogEvent> {
    if (raw.isNullOrEmpty()) return emptyList()
    val parsed = try {
        json.parseToJsonElement(raw)
    } catch (_: Exception) {
        return emptyList()
    }
    if (parsed !is JsonArray) return emptyList()
    return parsed.mapNotNull(::eventOf)
}

private fun eventOf(e: JsonElement): LogEvent? {
    if (e !is JsonObject) return null
    val t = (e["t"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return null
    val kind = EventKind.fromWire((e["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull) ?: return null
    val id = (e["id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val detail = (e["detail"] as? JsonObject)?.mapValues { (_, v) -> plain(v) }
    return LogEvent(t, kind, id, detail)
}

private fun plain(v: JsonElement): Any? = when {
    v is JsonNull -> null
    v is JsonPrimitive && v.isString -> v.content
    v is JsonPrimitive -> v.booleanOrNull ?: v.longOrNull ?: v.doubleOrNull
    else -> null
}

fun encodeLog(events: List<LogEvent>): String =
    JsonArray(events.map(::jsonOf)).toString()

/** An event's detail alone, for storage in a database column. */
fun encodeDetail(detail: EventDetail): String =
    JsonObject(detail.mapValues { (_, v) -> jsonValue(v) }).toString()

/** Reads what [encodeDetail] wrote; anything unreadable gives null. */
fun decodeDetail(raw: String?): EventDetail? {
    if (raw.isNullOrEmpty()) return null
    return try {
        (json.parseToJsonElement(raw) as? JsonObject)?.mapValues { (_, v) -> plain(v) }
    } catch (_: Exception) {
        null
    }
}

private fun jsonOf(e: LogEvent): JsonObject = JsonObject(
    buildMap {
        put("t", JsonPrimitive(e.t))
        put("kind", JsonPrimitive(e.kind.wire))
        e.id?.let { put("id", JsonPrimitive(it)) }
        e.detail?.let { d -> put("detail", JsonObject(d.mapValues { (_, v) -> jsonValue(v) })) }
    },
)

private fun jsonValue(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    else -> JsonPrimitive(v.toString())
}
