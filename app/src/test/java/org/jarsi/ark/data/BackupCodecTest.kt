package org.jarsi.ark.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val word = WordEntity("chrome", "chrome", 3, 2000L, false, 1000L, 1, 0, false)
    private val bigram = BigramEntity("jako", "20", 2, 2000L)
    private val trigram = TrigramEntity("prx4", "jako", "20", 1, 2000L)
    private val clip = ClipEntity(0L, "moikka pohja", null, 1500L, true)

    @Test
    fun `koodaus ja purku sailyttavat sisallon`() {
        val backup = Backup(listOf(word), listOf(bigram), listOf(trigram), listOf(clip))
        val decoded = BackupCodec.decode(BackupCodec.encode(backup))
        assertEquals(backup.words, decoded.words)
        assertEquals(backup.bigrams, decoded.bigrams)
        assertEquals(backup.trigrams, decoded.trigrams)
        assertEquals(listOf("moikka pohja"), decoded.clips.map { it.text })
        assertTrue(decoded.clips.all { it.pinned })
    }

    @Test
    fun `tuntematon muoto ja rikkinainen tiedosto hylataan`() {
        try {
            BackupCodec.decode("""{"format":99}""")
            fail("tuntemattoman muodon olisi pitänyt heittää")
        } catch (e: IllegalArgumentException) {
        }
        try {
            BackupCodec.decode("ei jsonia")
            fail("rikkinäisen tiedoston olisi pitänyt heittää")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun `mergeWords ottaa vahvimmat signaalit ja on idempotentti`() {
        val local = word.copy(
            count = 3, acceptedCount = 1, ignoredCount = 2, lastUsed = 2000L, created = 1000L,
        )
        val other = word.copy(
            word = "Chrome", count = 5, acceptedCount = 0, ignoredCount = 4,
            lastUsed = 3000L, created = 500L, pinned = true,
        )
        val merged = BackupCodec.mergeWords(listOf(local), listOf(other)).single()
        assertEquals("Chrome", merged.word)
        assertEquals(5, merged.count)
        assertEquals(1, merged.acceptedCount)
        assertEquals(2, merged.ignoredCount)
        assertEquals(3000L, merged.lastUsed)
        assertEquals(500L, merged.created)
        assertTrue(merged.pinned)
        assertEquals(merged, BackupCodec.mergeWords(listOf(merged), listOf(other)).single())
    }

    @Test
    fun `mergeWords tuo uuden sanan sellaisenaan`() {
        assertEquals(listOf(word), BackupCodec.mergeWords(emptyList(), listOf(word)))
    }

    @Test
    fun `esto sailyy kummaltakin puolelta`() {
        val blocked = word.copy(blocked = true)
        assertTrue(BackupCodec.mergeWords(listOf(blocked), listOf(word)).single().blocked)
        assertTrue(BackupCodec.mergeWords(listOf(word), listOf(blocked)).single().blocked)
    }

    @Test
    fun `sanaparit ja ketjut ottavat suuremman maaran ja tuoreemman ajan`() {
        val local = bigram.copy(count = 2, lastUsed = 2000L)
        val other = bigram.copy(count = 7, lastUsed = 1000L)
        val merged = BackupCodec.mergeBigrams(listOf(local), listOf(other)).single()
        assertEquals(7, merged.count)
        assertEquals(2000L, merged.lastUsed)
        val t = BackupCodec.mergeTrigrams(listOf(trigram), listOf(trigram.copy(count = 5))).single()
        assertEquals(5, t.count)
    }

    @Test
    fun `newClips ohittaa loytyvan tekstin ja antaa vapaat idt`() {
        val existing = ClipEntity(5000L, "moikka pohja", null, 1L, true)
        val imported = listOf(clip, ClipEntity(0L, "uusi pohja", null, 2L, true))
        val result = BackupCodec.newClips(listOf(existing), imported, idBase = 100L)
        assertEquals(listOf("uusi pohja"), result.map { it.text })
        assertTrue(result.single().id > 5000L)
        assertTrue(result.single().pinned)
    }
}
