# Vaihe 3 – Henkilökohtaisten sanojen oppiminen

Tämä dokumentti tarkentaa kokonaissuunnitelman ([suunnitelma.md](suunnitelma.md))
kohdat 5–16 ja 28–30 Vaiheen 3 osalta: käsin kirjoitetut sanat opitaan
paikalliseen tietokantaan ja ne nousevat ehdotuksiin yleissanaston rinnalle.
Lisäksi kerätään sanaketjut valmiiksi Vaiheen 4 ennustusta varten.

## 1. Oppiminen (LearningEngine)

Uusi `LearningEngine` pitää opitut sanat muistissa: sana → käyttömäärä,
viimeksi käytetty, estetty.

* **Liipaisin**: kun käsin kirjoitettu sana päättyy (välilyönti, välimerkki
  tai enter), sen käyttömäärä kasvaa. Ehdotuksesta valittu sana kasvattaa
  määrää vain, jos sana on jo omassa sanastossa — pelkkä yleissanan valinta
  ei luo omaa sanaa (hyväksynnöistä oppiminen kuuluu Vaiheeseen 5).
* **Kelpoisuus**: sanassa saa olla kirjaimia, numeroita, yhdysmerkkejä ja
  sisäisiä pisteitä; pituus 2–32 merkkiä; vähintään yksi kirjain.
  Esimerkkejä: `prx4` ja `jarsi.org` opitaan, lauseen lopettava piste ei
  tartu sanaan. Pelkät numerot (`20`) eivät mene sanastoon täydennyksinä,
  mutta tallentuvat ketjuihin (kohta 5).
* **Myös yleissanaston sanat opitaan** — oma käyttö nostaa niiden sijoitusta.
* **Kirjainkoko**: sana tallennetaan siinä asussa, jossa se kirjoitettiin
  ensimmäisen kerran (`Jako`, `prx4`), ja täsmäys tehdään kirjainkoosta
  riippumatta. Ehdotus näytetään tallennetussa asussa; lauseen alussa
  ensimmäinen kirjain isonnetaan kuten muutenkin.
* **Tasot painoina**: 1× = väliaikainen (pieni paino), 2–3× = ehdokas,
  4+ = vahvistettu (täysi paino). Sana ehdotetaan heti ensimmäisestä
  kerrasta alkaen.
* **Eräkirjoitus**: muutokset kertyvät muistiin ja kirjoitetaan Roomiin
  taustasäikeessä näppäimistön sulkeutuessa sekä 50 muutoksen välein —
  ei koskaan painallus kerrallaan (kokonaissuunnitelman kohta 29).

## 2. Tallennus (Room)

* `WordEntity`: sana (avain), käyttömäärä, viimeksi käytetty, estetty, luotu.
* `BigramEntity`: edeltävä sana, seuraava sana, määrä, viimeksi käytetty
  (yhdistetty avain edeltävä+seuraava).
* DAO:t: kaikkien lataus, upsert-erä, poisto, esto.
* Koko sanasto ladataan palvelun käynnistyessä taustasäikeessä muistiin
  samaan tapaan kuin yleissanalista; haut eivät koske tietokantaa.
* Gradle: Room-riippuvuudet ja KSP-liitännäinen.

## 3. Ehdotusten yhdistäminen (SuggestionEngine)

Uusi `SuggestionEngine` kokoaa ehdotusrivin:

1. omat etuliitteeseen täsmäävät sanat pisteytettyinä kärkeen (enintään 3):
   pisteet = käyttömäärä × tuoreuskerroin (alle 7 pv ×1,0 / alle 30 pv ×0,7 /
   vanhempi ×0,4)
2. sitten yleissanaston täydennykset yleisyysjärjestyksessä
3. sama sana näytetään vain kerran; **estetyt sanat eivät koskaan näy** —
   esto toimii myös yleissanaston sanoille.

Tyhjän syötteen rivi pysyy ennallaan (yleisimmät sanat); Vaiheen 4 ennustus
korvaa sen ketjuihin perustuvalla seuraavan sanan ennustuksella.

Täysi pisteytysmalli (kokonaissuunnitelman kohta 22) tulee vasta Vaiheessa 5,
kun palautesignaalit ovat olemassa — rajapinta suunnitellaan niin, ettei sitä
tarvitse silloin rikkoa.

## 4. Poisto ja esto (pitkä painallus ehdotukseen)

Pitkä painallus ehdotusrivin sanaan avaa pienen valikon ehdotuksen
yläpuolelle:

* **Poista opittu sana** — näkyy vain omille sanoille; poistaa sanan
  tietokannasta ja muistista
* **Älä ehdota tätä** — näkyy kaikille sanoille; estää sanan pysyvästi

Valinta liu'uttamalla tai napauttamalla, värinäpalaute kuten muissa eleissä.
Rivi päivittyy heti valinnan jälkeen. Opittujen sanojen hallintanäkymä
asetuksiin tehdään myöhemmin (tehtävä kirjattu).

## 5. Sanaketjujen tallennus (pohja Vaiheen 4 ennustukselle)

`LearningEngine` pitää kirjaa edellisestä sanasta ja tallentaa jokaisen
peräkkäisen sanaparin `BigramEntity`-tauluun. **Rivinvaihto ei katkaise
ketjua** — esimerkiksi `prx4` ⏎ `Jako 20` ⏎ `nouto 6` tuottaa parit
prx4→jako, jako→20, 20→nouto, nouto→6. Ketju katkeaa, kun kenttä tai
sovellus vaihtuu tai kursori siirtyy muualle. Numerot kelpaavat ketjun
jäseniksi. Vaiheessa 3 dataa vain kerätään; Vaihe 4 rakentaa ennustuksen.

**Vaiheen 4 hyväksymistesti (kirjataan nyt):** kun käyttäjä on kerran
kirjoittanut `prx4` ⏎ `Jako 20` ⏎ `nouto 6`, näppäimistö ehdottaa
`prx4`:n jälkeen sanaa `Jako`, sen jälkeen `20`, ja niin edelleen.

## 6. Yksityisyys ja virhetilanteet

* Oppiminen on käytössä kaikissa kentissä **paitsi salasanakentissä** —
  siis myös sähköposti-, osoite- ja numerokentissä.
* Jos tietokanta ei aukea, oppiminen kytkeytyy hiljaisesti pois eikä
  näppäimistö kaadu; ehdotukset toimivat silti yleissanastolla.
* Incognito-tila ja sovelluskohtainen esto kuuluvat myöhempiin vaiheisiin.

## 7. Testaus

* Yksikkötestit: LearningEngine (tasot, tuoreuspaino, esto,
  kelpoisuussäännöt, ketjujen muodostus) ja SuggestionEngine (yhdistäminen,
  duplikaattien poisto, estojen suodatus) muistitoteutusta vasten.
* Room-osuus ja poisto/esto-valikko todetaan laitetestissä (Pixel 8a).
* Sanan poiminnan laajennus (numerot, sisäiset pisteet) päivitetään
  WordTools-testeihin.

## 8. Vaiheen ulkopuolelle jää

Seuraavan sanan ennustus (Vaihe 4), hyväksynnöistä ja hylkäyksistä
oppiminen (Vaihe 5), sovelluskohtaisuus (Vaihe 6), automaattikorjaus
(Vaihe 7), hallintanäkymä asetuksiin (oma tehtävä) sekä decay-mallin
täysi versio (nyt yksinkertaistettu kolmiportainen tuoreuskerroin).
