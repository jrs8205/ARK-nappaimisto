# Vaihe 4 – Seuraavan sanan ennustus sanaketjuista

Tämä dokumentti tarkentaa kokonaissuunnitelman ([suunnitelma.md](suunnitelma.md))
kohdan 39 vaiheen 4: sanaparit ja trigramit sekä seuraavan sanan ennustus.
Vaihe 3 keräsi sanaparit valmiiksi; nyt niistä rakennetaan ennustus ja
keräys laajenee trigrameihin.

## 1. Ennustusmoottori (LearningEngine laajenee)

* **Trigramien keräys**: ketjunseuranta pitää kaksi edellistä sanaa ja
  tallentaa kolmikot (ensimmäinen, toinen → seuraava) samalla logiikalla
  kuin sanaparit: rivinvaihto ei katkaise ketjua; nuolinäppäimet,
  kursoriliu'utus ja kentän tai sovelluksen vaihto katkaisevat.
* **`predictNext(edeltävät, max)`**: palauttaa todennäköisimmät seuraavat
  sanat. Trigramiosumat (kaksi edeltävää sanaa täsmäävät) pisteytetään
  kertoimella ×3 — pidempi tunnettu konteksti painaa enemmän
  (kokonaissuunnitelman kohta 64) — ja bigramiosumat täydentävät.
  Pisteet = määrä × tuoreuskerroin (alle 7 pv ×1,0 / alle 30 pv ×0,7 /
  muuten ×0,4). Estetyt sanat eivät koskaan ennustu.
* **Konteksti luetaan tekstistä**: `WordTools` poimii kursorin edeltä
  viimeiset 1–2 valmista sanaa. Ennustus toimii siksi myös silloin, kun
  kursori napautetaan vanhan tekstin perään — se ei riipu istunnon
  ketjutilasta.
* **Kirjoitusasu**: ennustus näytetään opitussa asussa (`Jako`, `prx4`);
  pelkkä numero näytetään sellaisenaan (`20`). Lauseen alussa ensimmäinen
  kirjain isonnetaan kuten muissakin ehdotuksissa.

## 2. Näkyminen ehdotusrivillä (SuggestionEngine)

* **Tyhjä syöte**: enintään 3 ennustusta rivin kärkeen, loput täytetään
  yleisimmillä sanoilla kuten ennenkin. Esimerkki: `prx4` ⏎ → rivillä
  `Jako | ja | on | …`.
* **Kesken sanan**: etuliitteeseen täsmäävät ennustukset ensin (enintään
  3), sitten omat sanat (enintään 3), sitten yleissanaston täydennykset. Sama sana
  näytetään vain kerran; estetyt suodattuvat pois.
* Ehdotusten hyväksyntä, poisto ja esto toimivat ennustuksille samoin
  kuin muillekin ehdotuksille.

## 3. Tallennus ja suorituskyky

* Uusi `TrigramEntity`-taulu (ensimmäinen, toinen, seuraava, määrä,
  viimeksi käytetty; yhdistetty avain kolmesta sanasta).
* Tietokannan versio nousee 1 → 2 **oikealla migraatiolla**: pelkkä uuden
  taulun luonti — olemassa oleva oppimisdata säilyy koskemattomana.
* Muistirakenteet järjestetään ennustushakua varten kontekstin mukaan
  (edeltävä sana → jatkot), joten haku on välitön eikä koske tietokantaa.
* Keräys ja kirjoitukset kulkevat samoissa erissä kuin vaiheessa 3.
* Vaiheen 3 keräämät sanaparit ovat laitteella valmiina, joten ennustus
  toimii heti asennuksen jälkeen; trigramit tarkentavat käytössä.

## 4. Yksityisyys ja virhetilanteet

* Salasanakentissä ei ennusteta eikä kerätä mitään (vaiheen 3 suojaukset
  kattavat tämän: ehdotusrivi piilossa ja oppiminen pois).
* Jos tietokanta ei aukea, ennustus ja oppiminen jäävät hiljaisesti pois
  eikä näppäimistö kaadu.

## 5. Testaus

* Yksikkötestit: trigram ensin + bigram-täydennys, ×3-paino,
  tuoreuskerroin, estettyjen suodatus, asun säilyminen, kontekstisanojen
  poiminta tekstistä, ehdotusrivin järjestys tyhjällä ja osittaisella
  syötteellä.
* **Hyväksymistesti laitteella** (kirjattu vaiheessa 3): kun käyttäjä on
  kerran kirjoittanut `prx4` ⏎ `Jako 20` ⏎ `nouto 6`, näppäimistö
  ehdottaa `prx4`:n jälkeen sanaa `Jako`, sen jälkeen `20`, sitten
  `nouto` ja lopuksi `6`.

## 6. Vaiheen ulkopuolelle jää

4-grammit ja fraasit (vaihe 8), hyväksynnöistä ja hylkäyksistä oppiminen
sekä täysi pisteytysmalli (vaihe 5), sovelluskohtaiset ketjut (vaihe 6)
ja ennustuksen yhdistäminen automaattikorjaukseen (vaihe 7).
