# README 用のデモ GIF を録画するスクリプト（https://github.com/charmbracelet/vhs）
#
#   ./gradlew fatJar
#   vhs docs/demo.tape        # -> docs/demo.gif
#
# VHS を入れずに Docker で録画する場合（docs/demo.Dockerfile に JDK 25 入りの環境があります）:
#   docker build -t dopajshell-vhs -f docs/demo.Dockerfile docs
#   docker run --rm -v "$PWD:/vhs" dopajshell-vhs docs/demo.tape
#
# GIF には音が入らないので --no-sound で録画します。

Output docs/demo.gif

Set Shell "bash"
Set FontSize 18
Set Width 1280
Set Height 720
Set Padding 20
Set Theme "Dracula"
Set TypingSpeed 70ms
Set PlaybackSpeed 1.0
Set WaitTimeout 60s

Hide
Type "clear && java -jar build/libs/dopajshell-all.jar --no-sound"
Enter
Show
Wait+Screen /Welcome to dopajshell/
Sleep 1s

# 入力中の火花と COMBO、大きな数のカウントアップ
Type "IntStream.rangeClosed(1, 100).sum()"
Sleep 400ms
Enter
Wait+Screen /5050/
Sleep 1.5s

# メソッド定義 -> NEW ABILITY
Type "int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }"
Sleep 400ms
Enter
Wait+Screen /created method fib/
Sleep 1.5s

Type "fib(30)"
Enter
Wait+Screen /832040/
Sleep 1.5s

# コレクション -> 要素が順に現れる
Type "List.of(1, 1, 2, 3, 5, 8)"
Enter
Wait+Screen /\[1, 1, 2, 3, 5, 8\]/
Sleep 1.5s

# true / false -> YES! / NO!
Type "fib(10) == 55"
Enter
Wait+Screen /\$\d+ ==> true/
Sleep 1.5s

# エラー -> UNKNOWN SYMBOL / NO METHOD
Type '"dopa".lenght()'
Enter
Wait+Screen /\^-+\^/
Sleep 1.5s

# 終了時のリザルト画面
Type "/exit"
Enter
Sleep 6s
