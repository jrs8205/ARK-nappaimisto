package org.jarsi.ark.engine

import java.util.Locale
import java.util.PriorityQueue

/**
 * Yleinen sanasto: lataa taajuuslistan muistiin ja hakee täydennykset
 * etuliitteelle binäärihaulla. Lataus tehdään kutsujan taustasäikeessä;
 * [suggest] ja [topWords] palauttavat tyhjän kunnes lataus on valmis.
 */
class DictionaryEngine {

    private class Entry(val word: String, val key: String, val freq: Long)

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var common: List<String> = emptyList()
    @Volatile private var maxFreq = 0L

    private val fiLocale = Locale.forLanguageTag("fi")

    val isLoaded: Boolean get() = entries.isNotEmpty()

    /** Lataa rivit muodossa "sana taajuus". Virheelliset rivit ohitetaan. */
    fun load(lines: Sequence<String>, topWordCount: Int = 8) {
        val parsed = ArrayList<Entry>(100_000)
        for (line in lines) {
            val sep = line.lastIndexOf(' ')
            if (sep <= 0) continue
            val freq = line.substring(sep + 1).toLongOrNull() ?: continue
            val word = line.substring(0, sep).trim()
            if (word.isEmpty()) continue
            parsed += Entry(word, word.lowercase(fiLocale), freq)
        }
        parsed.sortBy { it.key }
        common = parsed.sortedByDescending { it.freq }
            .take(topWordCount)
            .map { it.word }
        maxFreq = parsed.maxOfOrNull { it.freq } ?: 0L
        entries = parsed
    }

    /** Suurin listalla esiintyvä taajuus pisteiden normalisointia varten. */
    fun maxFrequency(): Long = maxFreq

    /** Sanan taajuus tai 0, jos sana ei ole listalla. */
    fun frequencyOf(word: String): Long {
        val list = entries
        if (list.isEmpty()) return 0L
        val key = word.lowercase(fiLocale)
        var low = 0
        var high = list.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (list[mid].key < key) low = mid + 1 else high = mid
        }
        return if (low < list.size && list[low].key == key) list[low].freq else 0L
    }

    /** Yleisimmät sanat tyhjälle syötteelle. */
    fun topWords(): List<String> = common

    /** Etuliitteellä alkavat sanat yleisyysjärjestyksessä, enintään [max] kpl. */
    fun suggest(prefix: String, max: Int = 8): List<String> {
        val list = entries
        if (list.isEmpty() || prefix.isEmpty() || max <= 0) return emptyList()
        val key = prefix.lowercase(fiLocale)
        // Ensimmäinen indeksi, jonka avain >= haettu etuliite.
        var low = 0
        var high = list.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (list[mid].key < key) low = mid + 1 else high = mid
        }
        // Pienin taajuus kärjessä: kärki putoaa pois kun parempi löytyy.
        val best = PriorityQueue<Entry>(max, compareBy { it.freq })
        var i = low
        while (i < list.size && list[i].key.startsWith(key)) {
            val entry = list[i]
            if (best.size < max) {
                best.add(entry)
            } else if (best.peek().freq < entry.freq) {
                best.poll()
                best.add(entry)
            }
            i++
        }
        return best.sortedByDescending { it.freq }.map { it.word }
    }
}
