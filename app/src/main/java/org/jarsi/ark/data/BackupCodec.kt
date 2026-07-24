package org.jarsi.ark.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Varmuuskopion sisältö: oppimisdata, kiinnitetyt tekstileikkeet ja asetukset. */
data class Backup(
    val words: List<WordEntity>,
    val bigrams: List<BigramEntity>,
    val trigrams: List<TrigramEntity>,
    val clips: List<ClipEntity>,
    val settings: Map<String, Any> = emptyMap(),
)

/**
 * Varmuuskopion luku, kirjoitus ja yhdistäminen laitteen dataan.
 * Yhdistäminen on idempotenttia: saman tiedoston tuonti kahdesti ei
 * muuta mitään, joten tuonti on aina turvallista.
 */
object BackupCodec {

    const val FORMAT = 2

    /**
     * Nämä eivät kuulu varmuuskopioon: API-avaimet ovat Android Keystorella
     * laitekohtaisesti salattuja eivätkä toimisi toisella laitteella, ja
     * esittelyn kuittaus pidetään laitekohtaisena, jotta uusi puhelin
     * opastaa näppäimistön käyttöönoton.
     */
    private val EXCLUDED_SETTINGS = buildSet {
        ApiKeyStore.Slot.entries.forEach {
            add(it.plainPref)
            add(it.encryptedPref)
        }
        add("esittely_nahty")
    }

    fun encode(backup: Backup): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put(
            "words",
            JSONArray().apply {
                backup.words.forEach { w ->
                    put(
                        JSONObject()
                            .put("key", w.key)
                            .put("word", w.word)
                            .put("count", w.count)
                            .put("lastUsed", w.lastUsed)
                            .put("blocked", w.blocked)
                            .put("created", w.created)
                            .put("acceptedCount", w.acceptedCount)
                            .put("ignoredCount", w.ignoredCount)
                            .put("pinned", w.pinned)
                    )
                }
            },
        )
        root.put(
            "bigrams",
            JSONArray().apply {
                backup.bigrams.forEach { b ->
                    put(
                        JSONObject()
                            .put("previous", b.previous)
                            .put("next", b.next)
                            .put("count", b.count)
                            .put("lastUsed", b.lastUsed)
                    )
                }
            },
        )
        root.put(
            "trigrams",
            JSONArray().apply {
                backup.trigrams.forEach { t ->
                    put(
                        JSONObject()
                            .put("first", t.first)
                            .put("second", t.second)
                            .put("next", t.next)
                            .put("count", t.count)
                            .put("lastUsed", t.lastUsed)
                    )
                }
            },
        )
        root.put(
            "clips",
            JSONArray().apply {
                backup.clips.forEach { c ->
                    put(JSONObject().put("text", c.text).put("created", c.created))
                }
            },
        )
        root.put(
            "settings",
            JSONArray().apply {
                backup.settings.forEach { (key, value) ->
                    // Tyyppi kirjataan talteen, jotta tuonti palauttaa arvon
                    // samana prefs-tyyppinä (JSON ei erota Int/Long).
                    val type = when (value) {
                        is Boolean -> "b"
                        is Int -> "i"
                        is Long -> "l"
                        is String -> "s"
                        else -> return@forEach
                    }
                    put(JSONObject().put("k", key).put("t", type).put("v", value))
                }
            },
        )
        return root.toString()
    }

    /** Varmuuskopioon kelpaavat asetukset laitteen kaikista asetuksista. */
    fun exportableSettings(all: Map<String, *>): Map<String, Any> =
        all.filterKeys { it !in EXCLUDED_SETTINGS }
            .filterValues { it is Boolean || it is Int || it is Long || it is String }
            .mapValues { it.value as Any }

    /** @throws IllegalArgumentException jos tiedosto ei ole tuettu varmuuskopio. */
    fun decode(json: String): Backup = try {
        val root = JSONObject(json)
        // Muoto 1 kelpaa yhä: siitä puuttuvat vain asetukset.
        require(root.optInt("format") in 1..FORMAT) { "Tuntematon varmuuskopion muoto" }
        Backup(
            words = root.optJSONArray("words").mapObjects { w ->
                WordEntity(
                    key = w.getString("key"),
                    word = w.getString("word"),
                    count = w.getInt("count"),
                    lastUsed = w.getLong("lastUsed"),
                    blocked = w.getBoolean("blocked"),
                    created = w.getLong("created"),
                    acceptedCount = w.getInt("acceptedCount"),
                    ignoredCount = w.getInt("ignoredCount"),
                    pinned = w.getBoolean("pinned"),
                )
            },
            bigrams = root.optJSONArray("bigrams").mapObjects { b ->
                BigramEntity(
                    previous = b.getString("previous"),
                    next = b.getString("next"),
                    count = b.getInt("count"),
                    lastUsed = b.getLong("lastUsed"),
                )
            },
            trigrams = root.optJSONArray("trigrams").mapObjects { t ->
                TrigramEntity(
                    first = t.getString("first"),
                    second = t.getString("second"),
                    next = t.getString("next"),
                    count = t.getInt("count"),
                    lastUsed = t.getLong("lastUsed"),
                )
            },
            clips = root.optJSONArray("clips").mapObjects { c ->
                ClipEntity(
                    id = 0L,
                    text = c.getString("text"),
                    imagePath = null,
                    created = c.getLong("created"),
                    pinned = true,
                )
            },
            settings = buildMap {
                root.optJSONArray("settings").mapObjects { s ->
                    val key = s.getString("k")
                    // Kiellettyjä avaimia ei tuoda käsin muokatustakaan
                    // tiedostosta.
                    if (key in EXCLUDED_SETTINGS) return@mapObjects
                    when (s.getString("t")) {
                        "b" -> put(key, s.getBoolean("v"))
                        "i" -> put(key, s.getInt("v"))
                        "l" -> put(key, s.getLong("v"))
                        "s" -> put(key, s.getString("v"))
                    }
                }
            },
        )
    } catch (e: JSONException) {
        throw IllegalArgumentException("Varmuuskopion luku epäonnistui", e)
    }

    private fun <T> JSONArray?.mapObjects(convert: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { convert(getJSONObject(it)) }
    }

    fun mergeWords(
        existing: List<WordEntity>,
        imported: List<WordEntity>,
    ): List<WordEntity> {
        val byKey = existing.associateBy { it.key }
        return imported.map { other ->
            val local = byKey[other.key] ?: return@map other
            WordEntity(
                key = local.key,
                // Tuoreempi kirjoitusasu voittaa.
                word = if (other.lastUsed > local.lastUsed) other.word else local.word,
                count = maxOf(local.count, other.count),
                lastUsed = maxOf(local.lastUsed, other.lastUsed),
                blocked = local.blocked || other.blocked,
                created = minOf(local.created, other.created),
                acceptedCount = maxOf(local.acceptedCount, other.acceptedCount),
                // Ohitussakko on laitekohtainen; yhdistäessä annetaan anteeksi.
                ignoredCount = minOf(local.ignoredCount, other.ignoredCount),
                pinned = local.pinned || other.pinned,
            )
        }
    }

    fun mergeBigrams(
        existing: List<BigramEntity>,
        imported: List<BigramEntity>,
    ): List<BigramEntity> {
        val byKey = existing.associateBy { it.previous to it.next }
        return imported.map { other ->
            val local = byKey[other.previous to other.next] ?: return@map other
            local.copy(
                count = maxOf(local.count, other.count),
                lastUsed = maxOf(local.lastUsed, other.lastUsed),
            )
        }
    }

    fun mergeTrigrams(
        existing: List<TrigramEntity>,
        imported: List<TrigramEntity>,
    ): List<TrigramEntity> {
        val byKey = existing.associateBy { Triple(it.first, it.second, it.next) }
        return imported.map { other ->
            val local = byKey[Triple(other.first, other.second, other.next)] ?: return@map other
            local.copy(
                count = maxOf(local.count, other.count),
                lastUsed = maxOf(local.lastUsed, other.lastUsed),
            )
        }
    }

    /** Uudet tuotavat leikkeet: laitteelta jo löytyvä teksti ohitetaan. */
    fun newClips(
        existing: List<ClipEntity>,
        imported: List<ClipEntity>,
        idBase: Long,
    ): List<ClipEntity> {
        val texts = existing.mapNotNull { it.text }.toSet()
        var nextId = maxOf(idBase, (existing.maxOfOrNull { it.id } ?: 0L) + 1)
        return imported
            .filter { it.text != null && it.text !in texts }
            .map { it.copy(id = nextId++) }
    }
}
