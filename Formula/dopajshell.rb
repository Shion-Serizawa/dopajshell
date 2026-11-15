class Dopajshell < Formula
  desc "JShell with flashy terminal effects on every keystroke, result and exception"
  homepage "https://github.com/Shion-Serizawa/dopajshell"
  url "https://github.com/Shion-Serizawa/dopajshell/releases/download/v0.1.0/dopajshell.jar"
  version "0.1.0"
  sha256 "739f025947f844b7545c0d1b8f1b828d406907dbce44190968de160366edafe2"
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
