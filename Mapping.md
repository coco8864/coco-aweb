# mapping #

> Phantom Serverは、realHostで受け付けた各種のリクエストをmapping定義に従って適切なレスポンスコンポーネントに振り分けます。リクエストを"source"と表し、レスポンスを"destination"と表現しています。
> sourceからdestinationを決定するのが、mappingの役割です。定義によりさまざまなサーバとして動作させることができます。[汎用サーバ](Servers.md)参照

> mappingは、Consoleの以下から設定できます。
```
mappingタブ ID番号clickで表示されるダイアログ
```

> ## mappingの設定値 ##
    * enabled
> > > mapping定義の有効(true)か無効(false)を現します。
    * notes
> > > 説明文です。
    * realHostName
> > > リクエストを受け付けたrealHost名です。省略した場合受け付けた全てのリクエストが対象となります。
    * sourceType
> > > リクエストが以下いずれのリクエストプロトコルかを現します。（ブラウザがPhantom Proxyをどの種類のサーバと判断しているか？）
      * PROXY : proxyリクエストの場合
      * WEB : webリクエストの場合
      * WS : WebSocketリクエストの場合
    * secureType
> > > リクエスト電文の通信セキュリティ属性を現します。
      * PLAIN : 平文リクエストの場合
      * SSL : SSLリクエストの場合
    * sourceServer
      * sourceTypeがPROXYの場合
> > > > リクエスト先のサーバを現します。プレフィクスで指定することができます。
      * sourceTypeがWEB,WSの場合
> > > > ブラウザが指定した当該サーバのホスト名を表します。バーチャルホスト指定として利用できます。省略した場合、全てのホスト名に一致します。
    * sourcePath

> > > リクエストpathを現します。
    * destinationType
> > > レスポンスするコンポーネントを以下いずれで指定します。
      * HTTP
> > > > httpサーバからのレスポンスを返却します。
      * HTTPS
> > > > httpsサーバからのレスポンスを返却します。
      * FILE
> > > > ファイルを返却します。
      * HANDLER
> > > > プログラムからレスポンスを返却します。
    * destinationServer
      * destinationTypeがHTTP,HTTPSの場合
> > > > リクエスト先のサーバを現します。sourceServerに`*`が含まれた場合、それに該当する文字列を`$1`で参照できます。
      * destinationTypeがHANDLERの場合
> > > > レスポンスを返却するプログラムのクラス名を現します。
    * destinationPath

> > > リクエスト先に付加するpathを現します。destinationTypeがFILEの場合、ファイルpathを現します。相対パスの場合、${pahtom}/phからの相対となります。
    * roles
> > > [ph認証](Authentication.md)に利用されます。roleをコンマ区切で複数指定できます。認可処理で利用されユーザが所有するrolesとこのrolesに共通するものがあれば、このmappingを利用した処理が許可されます。
    * options
> > > 当該mappingの動作オプションをjson形式で指定します。代表的なものを以下に説明します。
      * logType
> > > > 採取するlog種別を指定します。
        * 指定なし:採取しない
        * access:accessLogだけを採取
        * request\_trace:accessLogとリクエスト通信データを採取
        * response\_trace:accessLogとレスポンス通信データをだけを採取
        * trace:accessLogをリクエスト、レスポンス通信データを採取
      * pac
> > > > 当該mapping情報をPhantom Proxyが配信するpacファイルに含めるか否かを指定します。単一のRealHostでのみ指定できます。
      * sessionUpdate
> > > > 当該リクエストをsessionTimeoutの更新に使用するか否かを指定します。
      * auth
> > > > [mapping認証](Authentication.md)の情報を指定します。
      * peek
> > > > sslプロキシとして動作する際にfalseを指定します。true（デフォルト）の場合、 Phantom Proxyが一旦SSLを終端させ、対象サーバには別のSSLセションでリクエストを送信します。


> ## mappingタブに表示されるボタン ##
    * 更新ボタン
> > > サーバに保存されている、mapping情報を取得し再描画します。編集後saveしてもサーバに保存されるだけで、実行時に使用されるmapping情報は更新されません。そのため、更新ボタンで表示されるmapping情報は現在適用中のmapping情報とは限りません。
    * 反映ボタン
> > > サーバに保存されているmapping情報を適用します。
    * debugTrace
> > > 全mappingのoptionsに`logType:"trace"`が設定されたとみなします。デバッグ目的で全リクエストをtraceしたい場合に有用です。