package org.jarsi.ark.clipboard

/** Leike: tekstisisältö tai kuvatiedoston polku. */
data class Clip(
    val id: Long,
    val text: String?,
    val imagePath: String?,
    val created: Long,
    val pinned: Boolean,
)

/** Siivouksen tulos: tietokannasta ja levyltä poistettavat. */
data class ClipPrune(
    val removedIds: List<Long>,
    val removedImagePaths: List<String>,
)

/**
 * Leikepöydän muistivarasto: kiinnitetyt säilyvät, kiinnittämättömät
 * vanhenevat tunnissa ja niitä pidetään enintään [MAX_UNPINNED].
 * Kutsut päälangalta; pysyvyys on kutsujan vastuulla.
 */
class ClipStore(private val clock: () -> Long = System::currentTimeMillis) {

    private val clips = LinkedHashMap<Long, Clip>()
    private var nextId = 0L

    fun load(loaded: List<Clip>) {
        clips.clear()
        for (clip in loaded) {
            clips[clip.id] = clip
            if (clip.id >= nextId) nextId = clip.id + 1
        }
    }

    private fun newId(): Long {
        val id = maxOf(nextId, clock())
        nextId = id + 1
        return id
    }

    /**
     * Lisää tekstileikkeen. Sama teksti kuin jollain olemassa olevalla
     * leikkeellä ei tallennu uudestaan — se vain nousee tuoreimmaksi.
     * Palauttaa tallennettavan leikkeen tai null, jos teksti on tyhjä.
     */
    fun addText(text: String): Clip? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val existing = clips.values.firstOrNull { it.text == trimmed }
        val clip = if (existing != null) {
            existing.copy(created = clock())
        } else {
            Clip(newId(), trimmed, null, clock(), pinned = false)
        }
        clips[clip.id] = clip
        return clip
    }

    /** Lisää kuvaleikkeen; [path] on sovelluksen omaan tallennustilaan kopioitu tiedosto. */
    fun addImage(path: String): Clip {
        val clip = Clip(newId(), null, path, clock(), pinned = false)
        clips[clip.id] = clip
        return clip
    }

    /** Kiinnitetyt ensin (uusin ylin), sitten muut uusin ylin; vanhentuneet pois. */
    fun all(): List<Clip> {
        val now = clock()
        val valid = clips.values.filter { it.pinned || now - it.created < EXPIRY_MS }
        return valid.filter { it.pinned }.sortedByDescending { it.created } +
            valid.filter { !it.pinned }.sortedByDescending { it.created }
    }

    fun setPinned(id: Long, pinned: Boolean): Clip? {
        val clip = clips[id] ?: return null
        val updated = clip.copy(pinned = pinned)
        clips[id] = updated
        return updated
    }

    fun remove(id: Long): Clip? = clips.remove(id)

    /** Poistaa vanhentuneet ja ylimääräiset kiinnittämättömät leikkeet. */
    fun prune(): ClipPrune {
        val now = clock()
        val removed = mutableListOf<Clip>()
        val unpinned = clips.values.filter { !it.pinned }.sortedByDescending { it.created }
        unpinned.forEachIndexed { index, clip ->
            if (index >= MAX_UNPINNED || now - clip.created >= EXPIRY_MS) {
                removed += clip
            }
        }
        removed.forEach { clips.remove(it.id) }
        return ClipPrune(
            removedIds = removed.map { it.id },
            removedImagePaths = removed.mapNotNull { it.imagePath },
        )
    }

    private companion object {
        const val EXPIRY_MS = 60L * 60 * 1000
        const val MAX_UNPINNED = 20
    }
}
