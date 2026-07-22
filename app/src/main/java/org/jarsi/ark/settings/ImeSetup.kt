package org.jarsi.ark.settings

/** Käyttöönoton tilan päättely esittelyä ja asetuksia varten. */
object ImeSetup {

    /**
     * Onko järjestelmän oletusnäppäimistö tämän sovelluksen oma.
     * [defaultIme] on Settings.Secure.DEFAULT_INPUT_METHOD -arvo
     * muodossa "paketti/palveluluokka".
     */
    fun isOwnIme(defaultIme: String?, packageName: String): Boolean =
        defaultIme != null &&
            defaultIme.contains('/') &&
            defaultIme.substringBefore('/') == packageName
}
