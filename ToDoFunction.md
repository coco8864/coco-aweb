  * websockteの負荷ツール
  * SPDYクライアント&負荷ツール
  * Linkletのjavascript化
  * ~~SPDY3.1対応~~
  * クライアント機能でCookie管理
  * 各種設定の失敗が通知されない
  * 認証proxy対応,
    * 案1）固定のuser/passを設定させてPhantom Serverを経由する場合はすべてそれを利用
    * 案2）クライアントにuser/passを問い合わせてセション毎に別々の認証ができるようにする。
  * ~~複数stressを一気に実施する機能~~ 1.1.0
    * ~~jsonでリクエストをアップロードして実施~~ 1.1.0
    * ~~stress対象の選択方法がポイント~~ 1.1.0 チェックボックスでアップロードを選択できるようにした。
  * ~~stress結果のcsv出力~~ 1.1.0
  * ~~ログのダウンロード~~
    * ~~ph.logだけで十分、リンクを何処に置くか？~~=>statusタブ
    * ~~任意にダウンロードできるようにするとセキュリティ的に心配~~
    * ~~履歴が指定できるようにしたい~~
  * trace時に削除するヘッダを変更可能にする
    * 現状、ifmodifyedsinceとetag?固定であるため、304が絶対レスポンスされない
    * CookieのphIdは、traceだけではなく、どのような状況でも削除される、（~~そもそも、phIdは変更可能にするべき~~）
  * ~~trace対象に初めてリクエストしたとき認証のトレースが記録される~~
  * ~~統計情報をlogに定期的に出力~~
  * ~~負荷試験時、統計情報の配信をとめる機能要？~~
  * ~~ブラウザから再起動できるといいな~~ 1.1.0
    * ~~負荷試験後メモリ不足から不安定になることがある~~1.1.0
    *~~案１）監視プロセスが生存監視＝意図的に再起動する際も利用~~1.1.0こちらを採用
    * ~~案２）再起動リクエスト実施時、別プロセスを起動して再起動~~1.1.0
    *~~いずれにしろqueueletの層で実装したい~~1.1.0
  * ~~ph.log~~
    * ~~WebSocketが使えない端末から/adminを開くと大量に/queueが出力される~~
      * ~~mappingのオプションにより抑止したい~~ =>skipPhlog:true
    * ~~あまり重要でないカラムが毎回出力されるのは無駄~~ =>shortFormatLog:true
    * ~~userIdが出力されないのはまずい~~
  * storeを手動でcompressしたい
  * filter機能
    * 開発中
    * squidguardの定義を貰ってblackListを作る
    * roleごとにblackList,whiteListを設定できるようにする
  * portal機能
    * 開発中
    * 完全に独立したアプリであるため配備手順の後、使用できるようにしたい
  * ~~WebSocketをサポートするがwss://で失敗すると、getAuthIdリクエストが定期的に飛ぶ~~ => 1回もopenに失敗しない場合は、自動でpolling方式に切り替える
  * replay機能で同一urlのレコードが複数あった場合の動作指定
    * 現状：uploadされたファイルで一致するもの -> AccessLog -> AccessLogに同一のurlがあった場合、現状最新(idが一番大きい)
    * 何が使われたかわかるようにする=>AccessLogタブにカラム追加
    * 古いほうから順次使われるロジックも選択したい（逆もあり）=>replay機能の返却履歴をセションで管理
  * ~~traceタブからのrequestで元のresolveOriginとは違うところにリクエストしたい。~~
    * ~~0からtraceデータを生成する操作がしたい~~自由に編集できるようにした
  *~~text編集、表示ダイアログのchunk,gzipは自動でデフォルト値を設定したい。~~1.1.0
    *~~Encodingもできればよいが...MS932,euc\_jp,iso2022\_jp等プルダウンがあるとよい~~1.1.0プルダウンはないが、content-typeから類推
    * ~~Encodingについてはブラウザによって動作が違う気がする...xml宣言に指定があるとがあるとブラウザ側で変更してくれるのもある、調査要(chromeはそう)~~1.1.0,ブラウザで変換すりょうにロジックを変更
  * ~~AuthSessionが認証単位になっているが、認証毎×SecondaryId毎であるべき。例）tokenは、アプリ毎に別々の値を採番しないといけない。~~1.1.0
    * ~~現状tokenを使うのがConsoleだけだから問題ないが、複数でてくると同じ値ではセキュリティ的に不都合~~1.1.0
    *~~AuthSessionの持ち方、ライフサイクルを変更する必要がある。~~1.1.0
  *~~認証時のpostMessageセキュリティ（ドメインを制限）~~1.1.0
    * jsonpのルートを設定で動かなくする事も要
  *~~html5機能を使用、未使用を強制的に設定できるほうがよい~~1.1.0
    * ~~safariが落ちる件が救えない（WebSocketを使わなければ問題ないので）~~1.1.0
  * 監視機能の機能強化
    * freeMemoryが少なくなったら（少ない状態を複数回観測したら）自動再起動
    * hungupを検出したらThreadDumpを採取した後、再起動
    * 定期的にthread dumpを採取
  * ~~WebSocket,draft-ietf-hybi-thewebsocketprotocol-10対応~~RFC 6455対応
    *~~いつの間にか!ChromeでWebSocketが使えなくなった~~trace|check
    * ~~現状実装は、draft-ietf-hybi-thewebsocketprotocol-00~~
  * ~~任意のリクエストを送信する機能~~
  * ~~restartを3回繰り返すと終わってしまう問題対応~~
  * ~~コード系の自動判別~~
    * ~~metaやxml宣言のコードはコンテンツの先頭付近にあるのでそれを利用~~
  * coneoleの完全WebSocket化
    * WebSocketリクエストの方がhttpでのリクエストより柔軟性がある
  * ~~WebSocketアプリ向けフレームワークが必要~~PhantomLink
    * file upload/download
  * 性能基礎データの採取,pahntom proxy|apache|nginx
    * グラフ表示
  *~~SPDYサポートhttp://dev.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3~~> > SPDYにJava7以上が必要、Java6の場合SPDYは使えない理由は2つ
      * java.util.zip.Deflater#deflateメソッドの第4パラメタDeflater.FULL\_FLUSH
      * NPNライブラリとしてname.ben.murphy,ssl\_npnを利用
  *~~一括してSSL運用にする機能、またその逆~~必要なし常にSSL
  * spdyクライアント
  * ~~chatをカレントにしてロードしたとき未接続となる~~
  * 基本認証時に長時間放置すると元に戻れなくなる
  * Storeの参照数がいいかげん
  * ~~Json-libのmybejason問題の修正~~
  * ~~accesslogの出力形式を標準にあわせる~~
  * ~~SPDY,websocketのaccesslogの出力形式を含めて、ドキュメントに説明する~~


できそうだけど基本やらない方針
  * Java5対応
    * javascriptの実行は、rhinoでできるはず
  * Java4対応
    * queueletのリコンパイルが必要（テスト用のクラスがjava5依存している）
    * datanucleusのアノテーションをxml定義に

レスポンス基準
  * 画面遷移を伴うもの1s以内
  * 考え中の表示がでるもの5s以内
  * 進捗がでるもの、進捗が妥当であること