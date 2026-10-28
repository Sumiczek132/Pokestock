# Pokestock

An offline Pokémon card collection app for Android: portfolio, full set lists in English and Japanese, a Pokédex binder tracker, wanted lists and graded slabs, with Cardmarket prices in £ refreshed every morning.

- `app/` is the Android app (Kotlin, Jetpack Compose).
- `scripts/` builds the card list and daily prices; `.github/workflows/data.yml` runs them every morning and publishes to the `data` branch.
- Your collection never leaves your phones and your own Google Drive file.

Card data: [TCGdex cards-database](https://github.com/tcgdex/cards-database). Prices: Cardmarket's public price guide, converted with the ECB's daily rate.
