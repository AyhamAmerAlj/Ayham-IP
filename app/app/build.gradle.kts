name: Build Ayham Player APK
on:
  push:
    branches: [main, master]
  workflow_dispatch:
permissions:
  contents: write
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Unzip project
        run: unzip -q -o AyhamPlayer.zip
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.9"
      - name: Build release APK
        working-directory: AyhamPlayer
        run: gradle assembleRelease --no-daemon --stacktrace
      - name: Rename APK
        run: cp AyhamPlayer/app/build/outputs/apk/release/app-release.apk AyhamPlayer.apk
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: latest
          name: Ayham Player (latest)
          files: AyhamPlayer.apk
          make_latest: true
