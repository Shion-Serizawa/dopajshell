class Dopajshell < Formula
  desc "JShell with flashy terminal effects on every keystroke, result and exception"
  homepage "https://github.com/Shion-Serizawa/dopajshell"
  url "https://github.com/Shion-Serizawa/dopajshell/releases/download/v0.1.1/dopajshell.jar"
  version "0.1.1"
  sha256 "4bfdef2ad0a2cc4ba2f52af4cba1f66fe97d559be90e77a65acd99fab6136bed"
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
