# Korkeuskatto ja inline-täyttö

Suunnitelma 23.9.2026. Tausta: käytettävyyskatselmus 8a:lla korkeudella 130 %.

## Mitattu lähtötilanne (Pixel 8a, 130 %, 3-nappinavigointi)

| Tilanne | ARK | Gboard |
|---|---|---|
| Pysty | 513 dp (56 % ruudusta) | 363 dp (40 %) |
| Vaaka | 387 dp → leikkautuu koko ikkunaan | 267 dp (65 %) |
| Selain, käyttäjätunnus | 513 dp + osoitepalkki 77 dp + lomakemuistin chipit 55 dp → kenttä piilossa | |

Salasanakentässä ehdotusrivi katoaa ja korkeus hyppää 57 dp. Holvi (salasana-sovellus)
on AutofillService, jonka ehdotus näkyy vain kentän viereisenä pudotusvalikkona, koska
ARK ei pyydä inline-ehdotuksia.

## 1. Korkeuskatto

Käyttäjän valitsema korkeus on pystysuunnan koko. Rivit kutistuvat vain, kun
työkalurivi + ehdotusrivi + 5 näppäinriviä + navigointipalkki ylittäisivät 65 %
ikkunan korkeudesta (Gboardin vaakasuhde).

- `KeyboardHeight.effectiveScale(prefScale, landscape, windowHeightDp, bottomInsetDp)`:
  puhdas funktio, yksikkötestattu. Perusrivi 56 dp pystyssä ja 44 dp vaa'assa
  (ennallaan), työkalurivi 40 dp, ehdotusrivi 44 dp, alatäyte 4 dp.
- KeyboardService laskee tehollisen skaalan `applyVisualSettings`-kohdassa ja antaa
  sen näppäimistölle, ehdotusriville ja chipiriville. Navigointipalkin varauksen
  muutos näppäimistönäkymässä laukaisee uuden laskennan.
- Pystyssä Pixel 8a:lla ja 10 Prolla 130 % pysyy täsmälleen ennallaan (katto 155 %
  ja 163 %). Vaa'assa 8a:lla skaala on noin 85 % ja kokonaiskorkeus 267 dp.

## 2. Inline-täyttö (Android 11+)

- `method.xml`: `android:supportsInlineSuggestions="true"`.
- `onCreateInlineSuggestionsRequest`: yksi `InlinePresentationSpec`, korkeus =
  ehdotusrivin korkeus, leveys 48 dp – näytön leveys, tyyli teeman väreistä
  (androidx.autofill `InlineSuggestionUi`). Enintään 5 ehdotusta.
- `onInlineSuggestionsResponse`: chipit täytetään `InlineContentView`-näkyminä
  `InlineChipsView`-riville (vaakarullaus), joka on ehdotusrivin paikalla.
  Kun chippejä on, ehdotusrivi väistyy; kun ei ole, ehdotusrivi palaa
  `suggestionsVisible`-tilan mukaan. Salasanakentässä rivi näkyy vain chippien kanssa.
- Kentän vaihto ja näkymän sulku tyhjentävät chipit.
- `InlineChips.rowState` ja `InlineChips.specSize` ovat puhtaita ja yksikkötestattuja.

## 3. Holvi

`HolviAutofillService.confirmationResponse` liittää datasetiin `InlinePresentation`-
esityksen, kun `FillRequest.inlineSuggestionsRequest` on mukana: otsikko
"Vahvista Holvissa", alaotsikko domain, Holvin kuvake. Salaisuuksia ei näytetä,
vahvistuspolku (`AutofillAuthActivity`, laitelukko) ja selaimen varmennus pysyvät
samoina. Ilman inline-pyyntöä vastaus on ennallaan.

## Todennus (23.9.2026)

- ARK: 323 yksikkötestiä, lint 0 virhettä. 8a:lla mitattu `dumpsys window`
  -kehyksestä: pysty 513 dp ennallaan, vaaka 387 dp → 267 dp (= Gboard).
- Holvi 1.1.3: 245 testiä, lintDebug/lintRelease 0 virhettä, allekirjoitus ennallaan.
- Chip: ensimmäinen build ei näyttänyt chipiä, koska `setChips` korvasi alustan
  antamat LayoutParams-mitat WRAP_CONTENT-leveydellä, joka mittautuu nollaksi
  (`InlineContentView` ei mittaa itseään). Korjattu `InlineChips.chipSize`-säännöllä.
  Omistaja todensi 10 Prolla oikealla tunnuksella: chip näkyy ja täyttö toimii.
