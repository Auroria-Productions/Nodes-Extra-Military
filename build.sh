#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
root="$PWD"
mkdir -p "$root/.cache" "$root/dist"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
commit=60464f53a5108e2b9a05137fffc193c6adf8d409
origin="https://raw.githubusercontent.com/Auroria-Productions/Auroria-Nodes/$commit"
fetch() {
  local relative="$1" target="$2" digest="$3"
  if [[ ! -f "$target" ]]; then
    curl --fail --location --retry 3 "$origin/$relative" -o "$tmp/download"
    echo "$digest  $tmp/download" | sha256sum --check --status
    mv "$tmp/download" "$target"
  fi
  echo "$digest  $target" | sha256sum --check --status
}
core="${NODES_JAR:-$root/.cache/milzzynodes.jar}"
deps="${NODES_COMPILE_LIBS:-$root/.cache/compile-libs.zip}"
if [[ -z "${NODES_JAR:-}" ]]; then fetch dist/milzzynodes.jar "$core" ff4b6f5c8addeb5ab7062c561aef0e2ce2a71062b721058cd8491271ebfd10b3; fi
if [[ -z "${NODES_COMPILE_LIBS:-}" && -z "${DEPS_DIR:-}" ]]; then fetch build-support/compile-libs.zip "$deps" 7cd720edf74901b72da726e1b25506da5bd140ed2d4f6ba366386016f5fae60e; fi
mkdir -p "$tmp/deps" "$tmp/classes" "$tmp/tests"
if [[ -z "${DEPS_DIR:-}" ]]; then
  unzip -q "$deps" -d "$tmp/deps"
  library_dir="$tmp/deps"
else
  library_dir="$DEPS_DIR"
fi
cp="$core:${PAPER_API:+$PAPER_API:}$library_dir/*"
find src/main/java -name '*.java' | sort > "$tmp/main.list"
javac --release 21 -encoding UTF-8 -cp "$cp" -d "$tmp/classes" @"$tmp/main.list"
find src/test/java -name '*.java' | sort > "$tmp/test.list"
javac --release 21 -encoding UTF-8 -cp "$tmp/classes:$cp" -d "$tmp/tests" @"$tmp/test.list"
java -ea -cp "$tmp/tests:$tmp/classes:$cp" com.auroria.nodesmilitary.MilitaryTests
cp -R src/main/resources/. "$tmp/classes/"
python3 - "$tmp/classes" "$tmp/Nodes-Military.jar" <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_STORED
import sys
root=Path(sys.argv[1])
with ZipFile(sys.argv[2],'w',ZIP_STORED) as z:
    for p in sorted(root.rglob('*')):
        if p.is_file():
            entry=ZipInfo(p.relative_to(root).as_posix(),(2026,11,28,0,0,0))
            entry.external_attr=0o100644 << 16
            z.writestr(entry,p.read_bytes())
PY
mv "$tmp/Nodes-Military.jar" "$root/dist/Nodes-Military.jar"
sha256sum "$root/dist/Nodes-Military.jar"
