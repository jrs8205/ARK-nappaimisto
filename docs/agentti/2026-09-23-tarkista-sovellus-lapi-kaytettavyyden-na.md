# Agentti: ARK-nappaimisto – agentti/tarkista-sovellus-lapi-kaytettavyyden-na

Luotu 23.9.2026 agenttid-palvelulla. Jokainen vuoro lisää osion alle. Kotona: `git fetch && git checkout agentti/tarkista-sovellus-lapi-kaytettavyyden-na`.

## Vuoro 1 – 23.9.2026 11:57 – claude/fable

Ohje: Tarkista sovellus läpi käytettävyyden näkökulmasta puhelimessa.

Loppuviesti:

Kävin läpi näppäimistön kaikki näkymät (näppäimet, työkalurivi, ehdotusrivi, leikepöytä, emojit, käännösnäkymä), palvelun syötelogiikan sekä asetus- ja esittelysivut puhelinkäytön näkökulmasta.

\## Mitä löysin

**Korjasin kolme selvää kosketuskäytön puutetta:**

- **Pitkän painalluksen valikko antoi irrotettaessa perusmerkin.** Valinta oli aina indeksissä 0 eli itse näppäimen merkissä, joten pitkä painallus q-näppäimeen ilman numeroriviä ja irrotus tuotti "q":n, ei "1":tä, vaikka vihje ja README lupaavat numeron. Lisäksi valikko oli keskitetty näppäimeen niin, että kahden solun valikossa sormi lepäsi täsmälleen solujen rajalla, ja pieninkin tärinä vaihtoi valintaa. Pisteen valikko (9 solua × 46 dp = 414 dp) ei myöskään mahtunut 360 dp leveälle näytölle, joten viimeiset merkit olivat ulottumattomissa.
- **Työkalurivin napit olivat vain 32 dp korkeita ja osuma-alue tarkalleen napin kokoinen.** Rivin ylä- ja alareunassa sekä nappien välissä olevat napautukset katosivat.
- **Numerokentässä ei saanut kaksoispistettä tai kauttaviivaa.** Kellonaika- ja päivämääräkentät avaavat numeronäppäimistön, jossa ":" ja "/" puuttuivat kokonaan.

**Havaintoja, joita en muuttanut (suunnittelupäätöksiä tai isompia töitä):**

- Shift-kierto on tarkoituksellinen (commit a278b1d), mutta lauseen alussa automaattisesti päällä oleva shift muuttuu yhdellä napautuksella CAPS-lukoksi. Pienen alkukirjaimen saa vasta kahdella napautuksella.
- Vaakasuunnassa numerorivi, työkalurivi, ehdotusrivi ja 5 riviä × 44 dp vievät noin 304 dp, eli lähes koko ruudun. Koko ruudun muokkaustila on estetty, ja käännösnäkymä ahtautuu vaakasuunnassa tekstialueiden minimiin.
- Askelpalautin siirtyy 400 ms:n jälkeen heti sanapoistoon (4 sanaa/s). Hieman liian pitkä painallus vie kokonaisen sanan. Dokumentoitu ominaisuus.
- Itse piirretyillä näkymillä (KeyboardView, ToolbarView, SuggestionBarView) ei ole lainkaan saavutettavuussolmuja, joten TalkBack ei näe näppäimiä.
- Värinä käyttää `performHapticFeedback`-kutsua, joka noudattaa järjestelmän kosketuspalautteen asetusta. Jos käyttäjä on sammuttanut sen, näppäimistön värinä-asetus ei tee mitään.
- Emojipaneelin askelpalautin ei toista pohjassa pidettäessä.

\## Mitä muutin

- `app/src/main/java/org/jarsi/ark/view/KeyboardView.kt`: ensimmäinen lisämerkki on valmiiksi valittu ja sijoitetaan sormen kohdalle, valinta vaihtuu vasta kosketuskynnyksen ylittävän liikkeen jälkeen, ja solut kapenevat niin että rivi mahtuu näytölle.
- `app/src/main/java/org/jarsi/ark/view/ToolbarView.kt`: osuma-alue kattaa rivin koko korkeuden ja puolet nappien välistä.
- `app/src/main/java/org/jarsi/ark/keyboard/Layouts.kt`: numeronäppäimistön pisteen pitkä painallus antaa ":" ja "/".
- `app/src/test/java/org/jarsi/ark/keyboard/LayoutsTest.kt`: uusi testi numeronäppäimistön lisämerkeille.

\## Mitä pitää tarkistaa kotona

- **Käännöstä ja testejä ei ajettu.** Repon `gradlew` on committattu ilman suoritusoikeutta (tila 100644), eikä sh- tai java-kierto ollut sallittu. Aja `./gradlew :app:testDebugUnitTest` kotona. Suosittelen x-bitin lisäämistä komennolla:

```
git update-index --chmod=+x gradlew
```

- Kokeile puhelimella pitkää painallusta: numerorivi piilotettuna q:n pitkä painallus ja irrotus pitäisi antaa "1", liu'utus vasemmalle "q". Pisteen pitkä painallus 360 dp:n näytöllä pitäisi näyttää kaikki 9 merkkiä.
- Tarkista, että työkalurivin napautus rivin reunasta osuu nappiin eikä liikaa "vuoda" naapurinappiin.
- Päätä, haluatko muuttaa shift-käytöstä (automaattinen shift → napautus → pienet) ja vaakasuunnan korkeutta; ne ovat suunnittelupäätöksiä, joihin en koskenut.

Muutokset:

```
app/src/main/java/org/jarsi/ark/keyboard/Layouts.kt             |  4 ++-
 app/src/main/java/org/jarsi/ark/view/KeyboardView.kt            | 34 +++++++++++++++++---
 app/src/main/java/org/jarsi/ark/view/ToolbarView.kt             | 12 ++++++--
 app/src/test/java/org/jarsi/ark/keyboard/LayoutsTest.kt         |  7 +++++
 .../2026-09-23-tarkista-sovellus-lapi-kaytettavyyden-na.md      | 48 +++++++++++++++++++++++++++++
 5 files changed, 97 insertions(+), 8 deletions(-)
```
