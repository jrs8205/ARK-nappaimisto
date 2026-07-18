package org.jarsi.ark.data

/**
 * Oppimisdatan muutosleima. Hallintanäkymä kasvattaa leimaa muutoksissa,
 * ja näppäimistöpalvelu lataa datan uudelleen huomatessaan eron.
 * Toimii, koska aktiviteetti ja palvelu ovat samassa prosessissa.
 */
object LearnedDataStamp {
    @Volatile var stamp = 0L

    fun bump() {
        stamp++
    }
}
