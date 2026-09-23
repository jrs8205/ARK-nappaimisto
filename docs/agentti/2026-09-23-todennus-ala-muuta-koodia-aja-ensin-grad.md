# Agentti: ARK-nappaimisto – agentti/todennus-ala-muuta-koodia-aja-ensin-grad

Luotu 23.9.2026 agenttid-palvelulla. Jokainen vuoro lisää osion alle. Kotona: `git fetch && git checkout agentti/todennus-ala-muuta-koodia-aja-ensin-grad`.

## Vuoro 1 – 23.9.2026 19:19 – claude/fable

Ohje: Todennus, älä muuta koodia. Aja ensin ./gradlew :app:testDebugUnitTest --tests org.jarsi.ark.keyboard.LayoutsTest ja sen jälkeen ./gradlew :app:assembleDebug. Kerro loppuraportissa kummankin komennon tulos (onnistuiko, montako testiä, syntyikö APK ja mihin polkuun) ja mainitse, jos jokin komento hylättiin luvan puutteen takia.

Loppuviesti:

Molemmat komennot onnistuivat, eikä yhtään komentoa hylätty luvan puutteen takia.

**Mitä löysin**

- Testit: `./gradlew :app:testDebugUnitTest --tests org.jarsi.ark.keyboard.LayoutsTest` päättyi BUILD SUCCESSFUL. Tulosraportin mukaan LayoutsTest sisältää 4 testiä, joista 4 läpi, 0 epäonnistui, 0 virhettä, 0 ohitettu. Ajo kesti noin 6 minuuttia, koska Gradle 8.14.3 ladattiin ensin ja koko sovellus käännettiin.
- Käännös: `./gradlew :app:assembleDebug` päättyi BUILD SUCCESSFUL noin 2 min 40 s:ssa. APK syntyi polkuun `app/build/outputs/apk/debug/app-debug.apk`.
- Kääntäjä antoi vain varoituksia (vanhentunut Locale-konstruktori, WebSocketListener-parametrien nimet, DictionaryEngine.kt:98 nullable-vastaanotin). Gradle varoitti myös, että daemonin Metaspace loppui ja että kahta natiivikirjastoa ei voitu stripata; kumpikaan ei estänyt onnistumista.

**Mitä muutin**

- En muuttanut koodia. Työpuussa oli jo ennen ajoa muokattu `gradlew`, ja se jäi sellaisenaan.

**Mitä pitää tarkistaa kotona**

- Että LayoutsTestin 4 testiä vastaa odotettua määrää.
- Gradlen Metaspace-varoitus viittaa siihen, että `org.gradle.jvmargs`-asetus gradle.propertiesissa voi kaivata suurempaa metaspacea, jos ajot hidastuvat.
- Halutessa `DictionaryEngine.kt:98` -varoitus nullable-vastaanottimesta, joka voi olla oikea vika.

Muutokset:

```
.../2026-09-23-todennus-ala-muuta-koodia-aja-ensin-grad.md       | 28 ++++++++++++++++++++++++++++
 gradlew                                                          |  0
 2 files changed, 28 insertions(+)
```
