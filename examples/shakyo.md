# dopajshell 写経ドリル

上から順に、**コピペせず手で打って** Enter してください（貼り付けると `PASTE` 演出 1 回にまとまってしまいます）。
各行の下の「→」は、そこで出るはずの演出です。CRITICAL や JACKPOT は運次第でどこでも出ます。

> **節目は上書きされます**: COMBO の節目（5, 10, 15, 20, 25, 30…）や通算評価数の節目（10, 50, 100…）に当たった回は、その回の演出が節目のバナー（`COMBO 10!` や `10 EVALS` など）に置き換わります。期待した演出が出なかったら、同じ行をもう一度打ってみてください。

起動:

```powershell
.\bin\dopajshell.cmd
```

---

## ステージ 1: ウォームアップ（基本の着弾）

```java
1 + 2
```
→ 初回なので `FIRST HIT!`。2 回目以降の普通の値は、`=>` から彗星が飛んで `+点数` が着弾します。

```java
40 + 2
```
```java
"dopa" + "jshell"
```
```java
'A' + 1
```

## ステージ 2: 値の種類で変わる演出

```java
3 > 2
```
→ `YES!` スタンプ

```java
"abc".isEmpty()
```
→ `NO!` スタンプ

```java
String nothing = null
```
→ 空振りして煙（`null whiff...`）

```java
System.out.println("hello, dopamine")
```
→ void なので空振り（`void whiff...`）

```java
1L << 40
```
→ これまでの最大値を 2 倍以上更新したので全画面の `NEW RECORD`

```java
IntStream.rangeClosed(1, 1000).sum()
```
→ 1 万以上なので巨大な数字がカウントアップして衝撃波

```java
"dopamine ".repeat(10)
```
→ 長い文字列なので、文字が流れ込む

```java
IntStream.range(0, 20).toArray()
```
→ 配列の要素がポンポン並ぶ

```java
List.of("Java", "Ruby", "Go", "Rust")
```
```java
Map.of("combo", 10, "score", 9999)
```
→ `MAP x 2`

## ステージ 3: 打鍵の演出（ここは打ち方で遊ぶ）

**連打で 8 / 16 / 32 文字**を、間を空けずに一気に打ってください（0.6 秒以上止まると途切れます）。
→ `8-BIT RUSH!` → `16-BIT COMBO!` → `32-BIT BLAZE!!`

```java
String message = "the quick brown fox jumps over the lazy dog";
```

**括弧を閉じる**たびに対の括弧が光って `NICE!`。

```java
Math.max(Math.min(10, 20), Math.abs(-5))
```

**文字列を閉じる**と範囲がハイライト:

```java
"closing quotes light up the whole string"
```

**途中で Backspace** を何回か押してから打ち直す → 消した文字が砕け散り、次の 1 文字で `RECOVERY`

```java
int typo = 12345;
```

**←→ キー**でカーソルを動かすと光の筋。**↑キー**で前の入力を呼び出すと `<< REWIND`。

**補完**: `"dopa".toUpp` まで打って **Tab** → `<< LOCK ON`

```java
"dopa".toUpperCase()
```

**しばらく手を止めてから**（2 秒以上）打ち始めると → `CHARGE!`

## ステージ 4: 定義で新しい能力を解放

複数行は Enter で継続行になり、行が増えるたびに `CHARGE LV2`、`CHARGE LV3`…。
メソッドの `}` を閉じた瞬間に `SEALED!`、確定すると `NEW ABILITY`。

```java
int fib(int n) {
    if (n < 2) return n;
    return fib(n - 1) + fib(n - 2);
}
```

```java
fib(25)
```
→ 75025 なので巨大数カウントアップ

```java
record Point(int x, int y) {
    double dist() { return Math.sqrt(x * x + y * y); }
}
```
→ `NEW RECORD TYPE`（入らない幅なら `UNLOCKED`）

```java
new Point(3, 4).dist()
```

```java
enum Rank { S, A, B, C }
```
→ `NEW ENUM`

```java
interface Shouter { String shout(String s); }
```
→ `NEW INTERFACE`

```java
Shouter loud = s -> s.toUpperCase() + "!!!";
```

```java
loud.shout("dopamine")
```

## ステージ 5: 大量出力

```java
for (int i = 1; i <= 30; i++) System.out.println("line " + i);
```
→ 20 行以上出力したので `OUTPUT RAIN`

## ステージ 6: わざと失敗する（エラーの種類ごとに演出が違う）

ここまでの COMBO はここで切れます（3 以上なら `combo reset`、10 以上なら `FEVER over`）。

```java
int broken = ;
```
→ 構文エラー: 入力行のエラー位置にひびが入って `SYNTAX BREAK`

```java
undefinedThing + 1
```
→ 未定義の名前にスポットライト `UNKNOWN SYMBOL`

```java
"hello".shout()
```
→ レシーバとメソッドのつながりが切れる `NO METHOD`

```java
nothing.length()
```
→ `NULL POINTER`（ステージ 2 で作った `nothing` は null）

```java
int n = "1";
```
→ 両側が衝突して爆発 `TYPE CLASH`

```java
Object boxed = 42;
```
```java
String str = (String) boxed;
```
→ `ClassCastException` も `TYPE CLASH`

```java
Math.max(1)
```
→ `ARGUMENT CLASH`

```java
Integer.parseInt("dopamine")
```
→ `NumberFormatException` も `ARGUMENT CLASH`

```java
1 / 0
```
→ 赤いフラッシュとグリッチする `FAILED`

## ステージ 7: 復活

失敗が続いたあとに成功すると、全画面で `COMEBACK!`（失敗 1 回だけのあとなら `FIXED!`）。

```java
1 + 1
```

## ステージ 8: コンボを伸ばして FEVER

ステージ 7 の `1 + 1` から数えて 10 回連続で成功させてください。5 回目で `COMBO 5`、10 回目で全画面の `COMBO 10!` と `FEVER TIME!`（以降の得点が 2 倍、HUD が虹色）。

```java
fib(10)
```
```java
fib(11)
```
```java
fib(12)
```
```java
fib(13)
```
```java
fib(14)
```
```java
fib(15)
```
```java
fib(16)
```
```java
fib(17)
```
```java
fib(18)
```
```java
fib(19)
```

## ステージ 9: 長い処理と中断

```java
Thread.sleep(4000)
```
→ 0.25 秒後から `CHARGING` ゲージ、3 秒を過ぎると落ち着いた経過時間表示

```java
while (true) { }
```
→ 走らせたまま **Ctrl-C** → 溜めたチャージが散って `INTERRUPTED`

## ステージ 10: でかい数

```java
BigInteger.TWO.pow(64)
```
→ 最大値を大きく更新するので `NEW RECORD`

```java
BigInteger.TWO.pow(5000)
```
→ 4000 ビット超えなので `HUGE`（桁数つき）

## フィナーレ

```java
/dopa
```
→ 今回のセッションの成績と生涯レベルを確認

```java
/exit
```
→ 全画面リザルト: タイトル落下、成績が 1 行ずつ着地、ドラムロールで合計スコア、ランク（S/A/B/C）と称号、花火

---

### おまけ

- `/dopa demo` … 全演出を 1 回ずつ自動再生（スコアは変わりません）
- `/dopa party` … 全画面フラッシュあり。`/dopa calm` で控えめに戻せます
- `/dopa duration=2` … 演出をスローモーションで観察
- 下のブロックは**貼り付け専用**。まとめて貼ると `PASTE x◯◯` が 1 回だけ出ます

```java
var words = List.of("every", "keystroke", "counts");
words.stream().map(String::toUpperCase).collect(Collectors.joining(" ")) + "!"
```
