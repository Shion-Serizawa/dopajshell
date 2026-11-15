# dopajshell

キー入力、評価、結果、例外のすべてに派手なターミナル演出がつく JShell です。
[dopairb](https://github.com/ko1/dopairb)（IRB 版）を Java に移植したもので、JDK 標準の `jdk.jshell` 公開 API と JLine 3 の上に作っています。

```
dopajshell> IntStream.rangeClosed(1, 100).sum()   COMBO 03  SCORE 1,420  [⣿⣿⣀⣀⣀⣀⣀⣀] x1.5
                    ⠂⠌⠟⠃        <- キーを打つたびに火花・発光・HUD
=> ✦ +350  COMBO 3                <- 必要なら 1 行のバッジを残す
$1 ==> 5050                       <- JShell の通常の出力はそのまま
```

## ビルドと起動

JDK は [mise](https://mise.jdx.dev/) で固定しています（`mise.toml`: Corretto 25）。このディレクトリでは mise が使う JDK を切り替えます。

### PowerShell

```powershell
mise install                  # 初回のみ: JDK をインストール
.\gradlew.bat fatJar          # build\libs\dopajshell-0.1.0-all.jar ができる
.\bin\dopajshell.cmd          # 起動
```

mise のシム（`%LOCALAPPDATA%\mise\shims`）が PATH にあれば、`JAVA_HOME` を設定しなくても動きます。
`mise activate` を使っている場合は、このディレクトリに入ると `JAVA_HOME` も自動で設定されます。

### macOS / Linux / Git Bash

```console
$ mise install
$ ./gradlew fatJar
$ bin/dopajshell
```

mise を使わない場合は JDK 25 を入れて `JAVA_HOME` を設定してください（実行だけなら JDK 21 以上で動きます）。
Windows Terminal での利用をおすすめします（24 ビットカラー対応）。

## 使い方

```powershell
.\bin\dopajshell.cmd                     # 全開・サウンドあり
.\bin\dopajshell.cmd --calm              # 控えめ・フラッシュなし・短め
.\bin\dopajshell.cmd --party             # 最大 + 全画面フラッシュ
.\bin\dopajshell.cmd --no-sound --no-flash
.\bin\dopajshell.cmd -cp lib\foo.jar     # クラスパスに追加
$env:DOPAJSHELL = "intensity=low,flash=off"; .\bin\dopajshell.cmd
```

セッション内では JShell で書けるもの（式、文、メソッド、クラス、record、複数行入力、Tab 補完）がそのまま使えます。コマンドは次のとおりです。

```
/list /vars /methods /types /imports /drop /open /save /history /reset /env -class-path /! /help /exit
/dopa                  設定とセッションの統計を表示
/dopa on | off | low | normal | max | calm | party
/dopa demo             全演出を 1 回ずつ再生（スコアは変わらない）
/dopa flash=off duration=0.5 ...
```

設定項目（`intensity`、`motion`、`flash`、`sound`、`hud`、`duration`、`color`、`trail`、`charge`、`intro`、`keys`）の意味は dopairb と同じです。`/dopa help` で一覧が出ます。

## 演出

ゲームの仕組みは dopairb と同じです。COMBO / CHARGE / SCORE、タイピング連続数の節目（`8-BIT RUSH!` 〜 `1KiB TYPING LEGEND!!!`）、括弧を閉じると `NICE!`、`}` でブロックを閉じると `SEALED!`、補完で `<< LOCK ON`、履歴呼び出しで `<< REWIND`、継続行で `CHARGE LV2`、貼り付けで `PASTE x120`。ほかにクリティカル、ジャックポット、FEVER、`FIXED!` / `COMEBACK!`、記録更新、節目の演出があり、`/exit` するとランク付きの全画面リザルトが出ます。

Java の結果と演出の対応:

| 結果 | 演出 |
| --- | --- |
| `void` / `null` | 空振りと煙 |
| `true` / `false` | `YES!` / `NO!` のスタンプ |
| 10,000 以上の数 | 巨大な数字のカウントアップと衝撃波 |
| 長い `String` | 文字が流れ込む |
| 配列、`List`/`Set`、`Map` | 要素が順に現れる |
| メソッド / クラス / interface / enum / record の定義 | `NEW ABILITY`、`NEW CLASS` など |
| 20 行以上の出力 | `OUTPUT RAIN` |
| 構文エラー | エラー位置で入力行にひびが入る（`SYNTAX BREAK`） |
| `cannot find symbol` | その名前にスポットライト（`UNKNOWN SYMBOL`） |
| メソッドが見つからない、`NullPointerException` | つながりが切れる（`NO METHOD` / `NULL POINTER`） |
| 型の不一致、`ClassCastException`、引数違い、`NumberFormatException` | `TYPE CLASH` / `ARGUMENT CLASH` |
| その他の例外 | 赤いフラッシュとグリッチする `FAILED` |
| 長い評価中の Ctrl-C | `INTERRUPTED` |

サウンドは実行時に合成し、`javax.sound` でプロセス内から直接鳴らします（音声ファイルも外部プレイヤーも不要）。
経験値・レベル・ベストスコア・連続日数は、Windows では `%LOCALAPPDATA%\dopajshell\profile.json`、それ以外では `~/.local/state/dopajshell/profile.json` に保存します。`DOPAJSHELL_PROFILE=off` で保存しません。

## 読みやすさは壊さない

- 評価と結果の報告は JShell のとおりです。演出のあとに jshell 標準の出力（`$1 ==> 5050`、`|  created method ...`、キャレット付きのエラー、スタックトレース付きの例外）を表示します。
- 演出は一時的な領域に描いて消します。スクロールバックに残るのは入力、プログラムの出力、結果（と任意のバッジ 1 行）だけです。
- 評価中にプログラムが出力すると、演出はすぐに引っ込みます。
- 演出中にキーを押すと演出はすぐ終わり、そのキーは行エディタに渡ります。
- パイプやリダイレクトでの実行、`TERM=dumb` では制御シーケンスを一切出さず、jshell 形式の出力だけを出します。
- 演出の中で起きた例外は REPL に影響しません。`DOPAJSHELL_DEBUG=パス` でログを出せます。`DOPAJSHELL_SEED=n` で運の要素を再現可能に、`DOPAJSHELL_SOUND=off` で音を無効にできます。

## 実装

- `Repl`: JShell の起動と管理（リモート実行エンジン。Ctrl-C は `JShell.stop()` で実行中の snippet を止める）、評価ループ、コマンド、`SourceCodeAnalysis` による補完。
- `DopaLineReader`: JLine 内部への依存はすべてここ。`getWidget`（キーごとの前後比較）、`getDisplayedBufferWithPrompts`（火花の軌跡と下段のエフェクト帯）、`redisplay`（右プロンプトとしての HUD）、`cleanup`（確定した入力のスナップショット）。
- `Probe` / `Feedback`: snippet イベントの分類と、jshell 形式の出力テキスト。
- `Game`、`InputFx`、`Director`、`scenes/*`、`Canvas`、`Stage`、`Sound`: dopairb からの移植。
- 移植していないもの: 名画 100 点のギャラリー（レベルアップ時は代わりに大きな `LEVEL UP!` バナー）と `dopacc`。

```powershell
.\gradlew.bat test     # 全シーンを複数の幅・色数で描画するテストと、全サウンドの合成テスト
```

## ライセンス

[MIT License](LICENSE)

## Special Thanks

- [ko1 (Koichi Sasada)](https://github.com/ko1) さん — 元になった [dopairb](https://github.com/ko1/dopairb) の作者です。ゲームの仕組み、演出、サウンドの設計はすべて dopairb に由来します。素敵なツールを MIT ライセンスで公開してくださったことに感謝します。
