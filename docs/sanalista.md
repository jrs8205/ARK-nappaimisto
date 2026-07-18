# Yleinen sanalista

Näppäimistön yleinen suomen sanalista (`app/src/main/assets/sanalista.txt`) on
muodostettu Kotimaisten kielten keskuksen (Kotus) julkaisemasta
Parole-taajuuslistasta, joka on poimittu suomen kielen Parole-korpuksesta
(noin 17 miljoonaa sanetta kirjoitettua suomea).

* Aineisto: Suomen sanomalehtikielen taajuuslista (Parole),
  <https://kaino.kotus.fi/sanat/taajuuslista/parole.php>
* Julkaisija: Kotimaisten kielten keskus (Kotus), aineisto listattu myös
  Kielipankissa
* Lisenssi: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)
  (Kotuksen avointen aineistojen ensisijainen lisenssi)
* Lähtötiedosto: `parole_frek_3.zip` (sanamuodot, jotka esiintyvät korpuksessa
  vähintään kolmesti; 326 514 muotoa)

## Käsittely

Lista on muodostettu työkalulla [`tools/sanalista.py`](../tools/sanalista.py):

1. sanamuodot pienennetään ja saman muodon taajuudet yhdistetään
2. mukaan otetaan vain suomen kirjaimista (a–z, å, ä, ö) ja yhdysmerkeistä
   koostuvat muodot; numerot, välimerkit ja muut siivotaan pois
3. alle viidesti esiintyvät muodot karsitaan
4. jäljelle jäävistä otetaan 80 000 yleisintä

Tulostiedoston muoto: yksi rivi per sana, `sananmuoto taajuus`,
yleisyysjärjestyksessä, UTF-8.

Myöhempi parannus: Kotuksen nykysuomen sanalistan yhdistäminen aineistoon,
jotta harvinaisemmat perusmuodot tulevat mukaan.
