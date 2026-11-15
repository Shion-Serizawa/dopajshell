#!/bin/sh
# Rewrite the Scoop and Homebrew manifests for a release.
#   packaging/update-manifests.sh 0.1.0 <sha256 of dopajshell.jar>
# The release workflow runs this after publishing and commits the result to main.
set -eu
version=$1
sha=$2
repo=Shion-Serizawa/dopajshell
url="https://github.com/$repo/releases/download/v$version/dopajshell.jar"
desc="JShell with flashy terminal effects on every keystroke, result and exception"
root=$(cd "$(dirname "$0")/.." && pwd)

cat > "$root/bucket/dopajshell.json" <<JSON
{
    "version": "$version",
    "description": "$desc",
    "homepage": "https://github.com/$repo",
    "license": "MIT",
    "notes": "dopajshell needs a JDK 21 or later on PATH (a JRE is not enough).",
    "suggest": {
        "JDK": [
            "java/temurin-lts-jdk"
        ]
    },
    "url": "$url",
    "hash": "$sha",
    "bin": "dopajshell.jar",
    "checkver": "github",
    "autoupdate": {
        "url": "https://github.com/$repo/releases/download/v\$version/dopajshell.jar"
    }
}
JSON

cat > "$root/Formula/dopajshell.rb" <<RUBY
class Dopajshell < Formula
  desc "$desc"
  homepage "https://github.com/$repo"
  url "$url"
  version "$version"
  sha256 "$sha"
  license "MIT"

  depends_on "openjdk"

  def install
    libexec.install "dopajshell.jar"
    bin.write_jar_script libexec/"dopajshell.jar", "dopajshell"
  end

  test do
    assert_match version.to_s, shell_output("#{bin}/dopajshell --version")
  end
end
RUBY
