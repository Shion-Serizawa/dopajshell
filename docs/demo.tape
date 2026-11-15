# README 用のデモ GIF を録画するスクリプト（https://github.com/charmbracelet/vhs）
#
#   ./gradlew fatJar
#   vhs docs/demo.tape        # -> docs/demo.gif
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

Hide
Type "clear && java -jar build/libs/dopajshell-all.jar --no-sound"
Enter
Show
Sleep 4s

# 入力中の火花と COMBO、大きな数のカウントアップ
Type "IntStream.rangeClosed(1, 100).sum()"
Sleep 400ms
Enter
Sleep 3s

# メソッド定義 -> NEW ABILITY
Type "int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }"
Sleep 400ms
Enter
Sleep 3s

Type "fib(30)"
Enter
Sleep 4s

# コレクション -> 要素が順に現れる
Type "List.of(1, 1, 2, 3, 5, 8)"
Enter
Sleep 3s

# true / false -> YES! / NO!
Type "fib(10) == 55"
Enter
Sleep 2.5s

# エラー -> UNKNOWN SYMBOL / NO METHOD
Type '"dopa".lenght()'
Enter
Sleep 3.5s

# 終了時のリザルト画面
Type "/exit"
Enter
Sleep 6s
