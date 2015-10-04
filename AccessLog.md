# accessLog #
> accessLogタブの使用方法を説明します。

> トレースで採取されたaccessLogは、DBに格納されています。一覧に表示されるエントリは、検索条件を入力後、更新ボタン押下で条件に一致したエントリ列を表示します。
> 使用頻度が高い検索条件を検索サンプルとして選択できます。検索条件はJDOQLに沿って自由に記述できるため、柔軟にaccessLogを調査することができます。JDOQLについては、以下を参考にしてください。


> [Java Data Object](http://db.apache.org/jdo/jdoql.html)

> ## ボタンとリンク ##
    * ID番号リンク
> > > 選択したエントリの詳細情報をtraceタブで表示します。
    * txtアイコン
> > > レスポンス内容をtextで表示します。圧縮形式(gzip)やコード系は手動で調整してください。
    * imgアイコン
> > > レスポンス内容をイメージで表示します
    * 更新ボタン
> > > 検索条件に一致するエントリを表示します。
    * 全ボタン
> > > 表示中の全エントリに、チェックもしくはチェック解除します。
    * 削除ボタン
> > > チェックの付いたエントリを削除します。
    * 移出ボタン
> > > チェックの付いたエントリをダウンロードします。
    * stressボタン
> > > チェックの付いたエントリでstressオプションダイアログを表示します。当該エントリで負荷テストを実施することができます。
    * 前ページボタン
> > > 同一条件で前のエントリ列を表示します。
    * 次ページボタン
> > > 同一条件で次のエントリ列を表示します。

> ## settingタブのaccessLog操作 ##
> > accessLog全体に冠する操作は、settingタブから行います。
    * 移入ボタン
> > > 指定した移出ファイルを移入します。
    * 削除ボタン
> > > 全accessLogを削除します。
    * 移出ボタン
> > > 全accessLogを移出します。

> ## accessLogのカラム ##
> > accessLogには表示されていないカラムも含めて多数の情報を保持しています。保持しているカラム群を以下に列挙します。JDOQLの検索条件に使用できます。
    * id
    * startTime
    * localIp
    * userId
    * requestLine
    * requestHeaderLength
    * statusCode
    * responseHeaderLength
    * responseLength
    * processTime
    * requestHeaderTime
    * requestBodyTime
    * responseHeaderTime
    * responseBodyTime
    * requestHeaderDigest
    * requestBodyDigest
    * responseHeaderDigest
    * responseBodyDigest
    * plainResponseLength
    * contentEncoding
    * transferEncoding
    * contentType
    * channelId
    * originalLogId
    * sourceType
      * 'w' : PLAIN\_WEB
      * 'W' : SSL\_WEB
      * 'p' : PLAIN\_PROXY
      * 'P' : SSL\_PROXY
      * 's' : SIMULATE
      * 'E' : EDIT
    * realHost
    * destinationType
      * 'H' : HTTP
      * 'S' : HTTPS
      * 'F' : FILE
      * 'A' : HANDLER
      * 'R' : REPLAY
      * 'E' : EDIT
    * resolveOrigin
    * resolveDigest