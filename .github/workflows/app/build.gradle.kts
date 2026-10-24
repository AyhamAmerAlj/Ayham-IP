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
      - name: Prepare project
        run: |
          if [ -f AyhamPlayer.zip ]; then unzip -q -o AyhamPlayer.zip; fi
          DIR=$(dirname "$(find . -name settings.gradle.kts -not -path './.git/*' | head -1)")
          echo "DIR=$DIR" >> "$GITHUB_ENV"
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.9"
      - name: Build release APK
        run: cd "$DIR" && gradle assembleRelease --no-daemon --stacktrace
      - name: Copy APK
        run: cp "$DIR"/app/build/outputs/apk/release/app-release.apk AyhamPlayer.apk
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: latest
          name: Ayham Player (latest)
          files: AyhamPlayer.apk
          make_latest: true
