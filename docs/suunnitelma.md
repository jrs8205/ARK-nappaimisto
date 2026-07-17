# ARK-näppäimistö – kokonaissuunnitelma

Tämä dokumentti kokoaa projektin suunnitelman: tavoitteen, tekniset ratkaisut,
oppimisjärjestelmän, yksityisyysperiaatteet ja kehitysvaiheet.

## 1. Tavoite

Androidin järjestelmätason näppäimistö (IME), joka oppii käyttäjän oman
kirjoitustavan tavallisia näppäimistöjä tehokkaammin: usein käytetyt sanat,
omat erikoissanat, sanaparit, lauseiden alut, sovelluskohtaisen sanaston,
hyväksytyt ja hylätyt ehdotukset sekä korjausten peruutukset. Näppäimistö
näyttää tavallista enemmän ehdotuksia ja antaa käyttäjälle enemmän hallintaa.

## 2. Tekninen perusta

Pääpalvelu perii `InputMethodService`-luokan ja syöttää tekstin
`currentInputConnection.commitText(...)`-kutsulla. `EditorInfo`-rakenteesta
tunnistetaan kenttätyyppi (teksti, sähköposti, URL, puhelin, numero, salasana,
monirivinen, haku) ja asettelu mukautuu kenttään: sähköpostikentässä näkyy @,
osoitekentässä /, numerokentässä numeronäppäimistö ja hakukentässä Enterin
tilalla hakupainike.

## 3. Käyttöliittymä

Androidin vanhat `Keyboard`- ja `KeyboardView`-luokat ovat vanhentuneita, joten
käyttöliittymä on rakennettu itse: yksi mukautettu View piirtää kaikki näppäimet
Canvasille ilman alinäkymiä. Tavoitteet: erittäin pieni viive, nopeat
kosketusreaktiot, pieni muistinkulutus, vakaa toiminta myös vanhoilla laitteilla.

Komponenttirakenne:

```text
KeyboardService
├── KeyboardLayout / Layouts
├── KeyboardView (näppäimet, kosketus, pitkä painallus)
├── SuggestionBar
├── SuggestionEngine
├── LearningEngine
├── DictionaryEngine
├── NGramEngine
├── CorrectionEngine
├── ClipboardManager
├── ThemeManager
├── PrivacyManager
├── AppContextManager
├── BackupManager
└── SettingsActivity
```

## 4. Perusominaisuudet

Suomalainen QWERTY (Å, Ä, Ö), numerorivi aina näkyvissä, Shift ja Caps Lock,
pitkä painallus erikoismerkeille, kaksi symbolisivua, numeronäppäimistö,
tumma/vaalea/AMOLED-teema, säädettävä korkeus, näppäinäänet, värinä,
esikatselukupla, välilyönnin pyyhkäisy kursorin siirtoon. Myöhemmin: emojinäkymä,
leikepöytä, tekstipohjat, yhden käden tila, tablettinäkymä.

## 5–16. Henkilökohtainen oppiminen

Projektin tärkein ominaisuus. Oppiminen huomioi:

1. sanan käyttömäärän ja viimeaikaisen käytön (decay-malli:
   `recentScore = usageScore × timeDecay`)
2. sanaparit (bigrammit), trigrammit ja 4-grammit — mitä pidempi tunnettu
   konteksti, sitä suurempi paino
3. kokonaiset usein käytetyt fraasit
4. hyväksytyt ehdotukset (paino nousee) ja jatkuvasti ohitetut ehdotukset
   (paino laskee hieman)
5. automaattikorjauksen peruutukset: palautettu sana lisätään omaan sanakirjaan,
   sen paino nousee voimakkaasti ja sama virheellinen korjaus estetään
6. käsin kirjoitetut sanat vaiheittain: 1 kerta = väliaikainen,
   2–3 kertaa = ehdokas, 4+ kertaa = vahvistettu
7. sanan poisto ja esto pitkäpainallusvalikosta (Kiinnitä, Poista opittu sana,
   Älä ehdota tätä, Unohda tässä sovelluksessa, Lisää sanakirjaan, Näytä tiedot)
8. sovelluskohtaiset mallit: kokonaispisteet = yleisen mallin pisteet +
   sovelluskohtaiset pisteet

## 17–22. Ehdotukset

* vaakasuunnassa vieritettävä ehdotusrivi, 5–8 ehdotusta näytön koon mukaan
* ehdotuksen leveys sanan pituuden mukaan
* laajennettava paneeli: 10–20 sanaa, fraaseja, leikepöytä, emojit
* monipuolisuus: ehdotuspaikat eri tarkoituksiin (oma sana, seuraavan sanan
  ennuste, täydennys, korjaus, vaihtoehto, sovelluskohtainen, fraasi)
* pisteytysmalli ilman raskasta tekoälymallia:

```text
score =
    wordFrequency * 1.0 +
    recentUsage * 2.0 +
    previousWordMatch * 4.0 +
    previousTwoWordsMatch * 7.0 +
    previousThreeWordsMatch * 10.0 +
    appSpecificMatch * 2.5 +
    userAcceptedSuggestion * 3.0 +
    manuallyTypedWord * 2.5 +
    pinnedWord * 20.0 -
    rejectedSuggestion * 5.0 -
    autoCorrectionUndo * 8.0
```

* keskeneräisen sanan täydennys ("sovel" → sovellus, sovelluksen…; "Obta" →
  Obtainium, vaikka sanaa ei ole yleissanakirjassa)
* seuraavan sanan ennustus n-grammeista

## 23–26. Suomen kieli ja korjaus

* taivutusmuodot tallennetaan aluksi omina sanoinaan; morfologinen analyysi
  lisätään vasta myöhemmin
* kirjoitusvirheiden tunnistus: Levenshtein-etäisyys, vierekkäiset näppäimet,
  puuttuva/ylimääräinen kirjain, väärä ääkkönen
* automaattikorjaus varovaiseksi: ei korjata omia sanoja, nimiä, teknisiä
  termejä, verkkotunnuksia eikä numeroita sisältäviä sanoja; voimakkuus
  säädettävissä (Pois / Varovainen / Normaali / Voimakas)
* kielen tunnistus suomi/englanti ilman jatkuvaa manuaalista vaihtoa

## 27. Paikallinen kielimalli (myöhemmin)

Pieni laitteella toimiva malli voi myöhemmin järjestää ehdotusmoottorin
tulokset uudelleen ja ymmärtää pidempää lauseyhteyttä. Ei ensimmäiseen versioon.

```text
Yleinen sanakirja → omat sanat → n-gram → sovelluskohtainen malli →
virheenkorjaus → (kielimalli) → lopullinen pisteytys → 5–8 ehdotusta
```

## 28–30. Tietokanta ja suorituskyky

Room + SQLite. Taulut: WordEntity, BigramEntity, TrigramEntity, PhraseEntity,
AppWordEntity, CorrectionEntity, SuggestionFeedbackEntity.

Oppimistapahtumat kerätään ensin muistiin, yhdistetään ja tallennetaan erissä
taustasäikeessä (näppäimistön sulkeutuessa ja sovelluksen vaihtuessa) — ei
tietokantakirjoitusta jokaisella painalluksella.

Suorituskyky: yleisimmät sanat muistivälimuistissa, indeksoitu n-gram-haku,
ehdotukset taustasäikeessä, vanhan haun peruutus uuden kirjaimen tullessa.

## 31–33. Yksityisyys, leikepöytä, tekstipohjat

* kaikki oppiminen paikallisesti, ei internet-oikeutta, ei analytiikkaa,
  ei mainoksia
* ei oppimista salasana- eikä maksukorttikentissä; incognito-tila;
  sovelluskohtainen esto; historian tyhjennys; salattu varmuuskopio
* leikepöytänäkymä: viimeksi kopioidut, kiinnitetyt, automaattinen
  vanheneminen, arkaluonteisten suodatus
* tekstipohjat pikakomennoilla (esim. `@@kiitos` → "Kiitos viestistä.")

## 34–35. Lisäominaisuudet

Myöhemmin mm. pyyhkäisykirjoitus, puhekirjoitus, kieliopin korjaus, emojihaku,
Markdown-tila, kelluva näppäimistö. Pyyhkäisykirjoitus vasta kun perusasiat
toimivat hyvin.

## 36–38. Asetukset ja datan hallinta

Asetusosiot: Ulkoasu, Kirjoittaminen, Oppiminen, Ehdotukset, Yksityisyys.
Oppimisdatan hallintanäkymä näyttää opitut sanat käyttömäärineen; sanoja voi
hakea, poistaa, kiinnittää ja estää. Salattu varmuuskopio sisältää sanat,
n-grammit, fraasit, tekstipohjat, estolistat ja asetukset.

## 39. Kehitysvaiheet

1. Toimiva perusnäppäimistö
2. Ehdotusrivi ja yleinen sanalista
3. Henkilökohtaiset sanat
4. Sanaparit ja trigrammit
5. Palautteesta oppiminen
6. Sovelluskohtainen oppiminen
7. Oikeinkirjoitus ja automaattikorjaus
8. Fraasit ja tekstipohjat
9. Paikallinen kielimalli
10. Edistyneet ominaisuudet

## 40. Ensimmäisen julkaisun rajaus

Suomalainen QWERTY, numerorivi, kolme teemaa, säädettävä korkeus, pitkä
painallus, vieritettävä 5–8 sanan ehdotusrivi, yleinen suomen sanakirja, omien
sanojen ja n-grammien oppiminen, hyväksynnöistä ja korjausten peruutuksista
oppiminen, sanan poisto ja esto, täysin paikallinen toiminta, historian
tyhjennys sekä sanaston vienti ja tuonti.

## 41. Vaikeimmat osat

Pieni kirjoitusviive, kosketustunnistus, suomenkielinen automaattikorjaus,
taivutusmuodot, nopea n-gram-haku, pisteytys, oppimisen ja yksityisyyden
tasapaino, laitekirjo, pyyhkäisykirjoitus ja kielimallin suorituskyky.

## 42–43. Erottautuminen

Näppäimistö, joka oppii käyttäjän omat sanat, sanayhteydet ja kirjoitustavan
nopeasti sekä näyttää enemmän hyödyllisiä ehdotuksia — täysin paikallisesti,
ilman että kirjoitettua tekstiä lähetetään minnekään.
