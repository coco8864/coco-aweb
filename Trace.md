# trace #

> traceタブの使用方法を説明します。

> traceタブでは、１つのaccessLogの情報を詳細に表示するとともに、その内容を変更することができます。
> 編集したデータでリクエストを送信したり、[replay機能](Replay.md)経由で利用するデータにできたりします。

> ## ボタンとリンク ##
    * id指定ボタン
> > > 表示内容をIDで指定したエントリに切り替えます。
    * parentボタン
> > > 編集後のデータが表示されている場合、編集前のエントリにジャンプします。
    * runボタン
> > > 画面上表示されているrequestデータ(HeaderとBody)でリクエストします。レスポンス受信後、そのエントリにジャンプします。
    * viewボタン
> > > 画面上表示されているresponseデータを新たなブラウザで表示します。responseがどのようなコンテンツかを確認することができます。
    * checkボタン
> > > 任意のURL(初期値は画面上のURL)にリクエストを投げ代表的なレスポンス情報を表示します。Webサーバの種類や利用しているProxyサーバのアドレス等、サーバ側の動作を簡単に確認することができます。
    * save newボタン
> > > 画面上表示されているエントリで新たにエントリを作成します。データ編集を行った場合にアクティブになります。
    * Request Headerボタン
> > > Request Headerの編集ダイアログが表示されます。saveした場合、画面上のデータが更新されます。
    * Request Bodyボタン
> > > Request Bodyの編集ダイアログが表示されます。saveした場合、画面上のデータが更新されます。
    * Request Bodyボタン右のDigestリンク
> > > 表示されている文字列は、Request BodyデータのDigest値です。Request Bodyデータをダウンロードします。右クリックで使ってください。
    * Response Headerボタン
> > > Response Headerの編集ダイアログが表示されます。saveした場合、画面上のデータが更新されます。
    * Response Bodyボタン
> > > Response Bodyがimageの場合、そのイメージを表示します。その他のコンテンツの場合Response Bodyの編集ダイアログが表示されます。saveした場合、画面上のデータが更新されます。
    * Response Bodyボタン右のDigestリンク
> > > 表示されている文字列は、Response BodyデータのDigest値です。Response Bodyデータをダウンロードします。右クリックで使ってください。