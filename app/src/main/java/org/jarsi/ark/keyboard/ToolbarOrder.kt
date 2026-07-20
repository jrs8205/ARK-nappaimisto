package org.jarsi.ark.keyboard

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Työkalurivin napit tallennustunnisteineen; enumin järjestys on oletusjärjestys. */
enum class ToolbarTool(val id: String) {
    ARROWS("nuolet"),
    WEB("www"),
    MIC("mikrofoni"),
    EMOJI("emojit"),
    CLIPBOARD("leikepoyta"),
    CORRECTION("oikoluku"),
    TRANSLATE("kaannos"),
    UNDO("peruutus"),
    SETTINGS("asetukset");

    companion object {
        fun byId(id: String): ToolbarTool? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Työkalurivin nappien järjestys ja piilotukset. Käyttäjän valinta
 * sovitetaan aina nykyiseen nappivalikoimaan: tuntemattomat ja
 * kaksoiskappaleet siivotaan, uudet napit palaavat järjestyksen loppuun
 * näkyvinä, eikä asetusnappia voi piilottaa, jotta asetuksiin pääsee aina.
 */
object ToolbarOrder {

    const val PREF_KEY = "tyokalurivi"

    val default: List<ToolbarTool> = ToolbarTool.entries.toList()

    data class Config(val order: List<ToolbarTool>, val hidden: Set<ToolbarTool>) {
        val visible: List<ToolbarTool> get() = order.filter { it !in hidden }
    }

    fun serialize(order: List<ToolbarTool>, hidden: Set<ToolbarTool>): String =
        JSONObject()
            .put("jarjestys", JSONArray(order.map { it.id }))
            .put("piilotetut", JSONArray(hidden.map { it.id }))
            .toString()

    fun load(saved: String?): Config {
        if (saved == null) return Config(default, emptySet())
        val parsed = try {
            val json = JSONObject(saved)
            fun ids(name: String): List<String> {
                val array = json.optJSONArray(name) ?: return emptyList()
                return (0 until array.length()).map { array.getString(it) }
            }
            ids("jarjestys") to ids("piilotetut")
        } catch (e: JSONException) {
            return Config(default, emptySet())
        }
        val kept = parsed.first.mapNotNull(ToolbarTool::byId).distinct()
        val order = kept + default.filter { it !in kept }
        val hidden = parsed.second.mapNotNull(ToolbarTool::byId).toSet() - ToolbarTool.SETTINGS
        return Config(order, hidden)
    }
}
