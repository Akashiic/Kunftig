#!/usr/bin/env bash
# Builds the Bukkit 1.7.10 API jar that Thermos/Crucible ship, from Crucible's bukkit/ sources at a pinned commit.
# No public Maven repository hosts this API; the jar feeds the binary-compatibility gate:
#
#   scripts/build-legacy-bukkit-api.sh build/legacy-bukkit/bukkit-1.7.10.jar
#   ./gradlew :bukkit:checkLegacyBukkitApi -PlegacyBukkitApi=build/legacy-bukkit/bukkit-1.7.10.jar
#
# Requires git, curl and a JDK (javac/jar) 9 or newer.
set -euo pipefail

OUT="${1:-build/legacy-bukkit/bukkit-1.7.10.jar}"
CRUCIBLE_REPO="https://github.com/CrucibleMC/Crucible"
CRUCIBLE_COMMIT="667a28e2b5c57f6f1fa21d8fdfd1722e174581ac"
MAVEN="https://repo.maven.apache.org/maven2"
# Dependencies of Crucible's bukkit/ module (versions as close to its pom.xml as Maven Central still serves).
DEPS=(
  "org/yaml/snakeyaml/1.9/snakeyaml-1.9.jar"
  "com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"
  "org/avaje/ebean/2.8.1/ebean-2.8.1.jar"
  "javax/persistence/persistence-api/1.0.2/persistence-api-1.0.2.jar"
  "com/google/guava/guava/10.0.1/guava-10.0.1.jar"
  "commons-lang/commons-lang/2.3/commons-lang-2.3.jar"
  "org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar"
  "org/jetbrains/annotations/24.1.0/annotations-24.1.0.jar"
  "com/google/code/gson/gson/2.2.4/gson-2.2.4.jar"
)

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git -C "$WORK" init -q crucible
git -C "$WORK/crucible" remote add origin "$CRUCIBLE_REPO"
git -C "$WORK/crucible" fetch -q --depth 1 origin "$CRUCIBLE_COMMIT"
git -C "$WORK/crucible" checkout -q FETCH_HEAD -- bukkit/src/main/java

mkdir -p "$WORK/lib" "$WORK/classes"
for dep in "${DEPS[@]}"; do
  # Maven Central rate-limits bursts (HTTP 429); retry with backoff.
  for attempt in 1 2 3 4 5; do
    if curl -sSf -o "$WORK/lib/$(basename "$dep")" "$MAVEN/$dep"; then break; fi
    [ "$attempt" = 5 ] && { echo "Could not download $dep" >&2; exit 1; }
    sleep $((attempt * 2))
  done
done

# BoundingBox is a Crucible backport that depends on a class outside the API module; nothing here uses it.
find "$WORK/crucible/bukkit/src/main/java" -name '*.java' ! -path '*/org/bukkit/util/BoundingBox.java' > "$WORK/sources.txt"
javac --release 8 -nowarn -proc:none -encoding UTF-8 \
  -cp "$(find "$WORK/lib" -name '*.jar' | tr '\n' ':')" -d "$WORK/classes" @"$WORK/sources.txt" 2>&1 \
  | grep -v '^Note:' || true
[ -f "$WORK/classes/org/bukkit/Bukkit.class" ] || { echo "Compilation failed" >&2; exit 1; }

mkdir -p "$(dirname "$OUT")"
jar cf "$OUT" -C "$WORK/classes" .
echo "Wrote $OUT ($(find "$WORK/classes" -name '*.class' | wc -l) classes, Crucible $CRUCIBLE_COMMIT)"
