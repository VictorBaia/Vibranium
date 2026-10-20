package com.music.bitchord.data.navidrome

import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord

/** Converts OpenSubsonic structuredLyrics/cueLine TTML-equivalent data. */
internal object NavidromeLyricsParser {
    fun parse(list: NavidromeLyricsList): List<LyricLine>? {
        val entry = list.structuredLyrics.firstOrNull { it.kind.equals("main", ignoreCase = true) }
            ?: list.structuredLyrics.firstOrNull { it.synced == true }
            ?: return null
        val offset = entry.offset ?: 0L
        val fallbackLines = entry.line
        val cues = entry.cueLine
        if (entry.synced != true || cues.isEmpty()) {
            return fallbackLines.mapIndexedNotNull { index, line ->
                val words = line.cue.mapNotNull { cue ->
                    cue.value.takeIf(String::isNotBlank)?.let { value ->
                        LyricWord(
                            startMs = (cue.start ?: line.start ?: 0L) + offset,
                            endMs = (cue.end ?: cue.start ?: line.end ?: 0L) + offset,
                        text = value,
                        )
                    }
                }
                val text = line.value.ifBlank { words.joinToString(" ") { it.text } }
                text.takeIf(String::isNotBlank)?.let {
                    LyricLine(
                        timeMs = (line.start ?: words.firstOrNull()?.startMs?.minus(offset) ?: index.toLong())
                            .plus(offset).coerceAtLeast(0L),
                        text = it,
                        words = words,
                        sungUntilMs = line.end?.plus(offset),
                    )
                }
            }.sortedBy(LyricLine::timeMs).takeIf(List<LyricLine>::isNotEmpty)
        }

        val roles = entry.agents.associate { it.id to it.role.lowercase() }
        val voices = roles.keys.filter { roles[it] != "bg" }
        val leads = cues.groupBy { it.index }.toSortedMap().mapNotNull { (_, layers) ->
            val lead = layers.firstOrNull { roles[it.agentId] == "main" }
                ?: layers.firstOrNull { roles[it.agentId] != "bg" }
                ?: layers.firstOrNull()
                ?: return@mapNotNull null
            val words = lead.cue.mapNotNull { cue ->
                val text = cue.value.trim()
                if (text.isEmpty()) null else LyricWord(
                    startMs = (cue.start ?: lead.start ?: 0L) + offset,
                    endMs = (cue.end ?: cue.start ?: lead.end ?: 0L) + offset,
                    text = text,
                )
            }
            val text = lead.value.ifBlank {
                fallbackLines.getOrNull(lead.index)?.value.orEmpty().ifBlank {
                    words.joinToString(" ") { it.text }
                }
            }
            if (text.isBlank()) return@mapNotNull null
            val background = layers.firstOrNull { roles[it.agentId] == "bg" }?.let { bg ->
                LyricLine(
                    timeMs = (bg.start ?: 0L) + offset,
                    text = bg.value,
                    words = bg.cue.mapNotNull { cue ->
                        cue.value.takeIf(String::isNotBlank)?.let { value ->
                            LyricWord((cue.start ?: 0L) + offset, (cue.end ?: cue.start ?: 0L) + offset, value)
                        }
                    },
                )
            }
            LyricLine(
                timeMs = (lead.start ?: words.firstOrNull()?.startMs ?: 0L) + offset,
                text = text,
                words = words,
                sungUntilMs = lead.end?.plus(offset),
                background = background,
                alignment = if (voices.indexOf(lead.agentId) > 0) LyricAlignment.End else LyricAlignment.Start,
            )
        }
        return leads.takeIf(List<LyricLine>::isNotEmpty)
    }
}
