package com.creatora.app.data.platform

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/** A number that may be missing: null when the field is absent or null (never zero). */
fun JSONObject.longOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null

fun JSONObject.stringOrNull(name: String): String? = if (has(name) && !isNull(name)) optString(name).ifBlank { null } else null

private val MetaTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

/** Meta writes times as `2024-05-01T10:00:00+0000`; the rest of the app reads ISO instants (`...Z`). Null when unreadable. */
fun metaTimeToIso(value: String?): String? =
    value?.let { runCatching { OffsetDateTime.parse(it, MetaTime).toInstant().toString() }.getOrNull() }

fun epochSecondsToIso(seconds: Long): String = Instant.ofEpochSecond(seconds).toString()

private val Hashtag = Regex("#([\\p{L}\\p{M}\\p{N}_]+)")

/** The hashtags in a caption, without the #, as the video's tags (real data the creator wrote). */
fun hashtagsOf(caption: String?): List<String> = caption?.let { Hashtag.findAll(it).map { m -> m.groupValues[1].lowercase() }.distinct().toList() }.orEmpty()

fun firstLine(text: String?, max: Int = 100): String = text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(max).orEmpty()
