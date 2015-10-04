# Websocket stress #
> stressタブにあるWebsocket stressでは、countに数字を入力し、connetボタンを押下することで自サーバに多数のWebsocket接続が実行されることをシミュレートする事ができます。この機能を利用する場合、以下のmappingを有効にしてください。
    * connect for websocket stress

# 負荷テスト(web) #

> 採取したトレース情報を連続的にサーバに送信することでサーバの性能を測定することができます。対象サーバには負荷かかります。本機能は十分注意して使ってください。

> 以下、サンプルサイトは、テスト用に作ったサイトであり、負荷をかけても問題ありません。このサイトを対象に説明します。
```
 http://ph-sample.appspot.com
```

> ## 手順 ##
    1. 以下urlにリクエストしてトレースを採取してください。
> > > http://ph.ph-sample.appspot.com/
    1. ConsoleにログインしてaccessLogタグを表示してください。
> > > 更新ボタンを押下して最新のリストにしてください。
    1. 先頭付近にある以下２つのエントリを選択してください。
      * `GET http://ph.ph-sample.appspot.com/images/3.jpg HTTP/1.1`
      * `GET http://ph.ph-sample.appspot.com/ HTTP/1.1`
> > > 前者は、採取フェーズで最後に見た画像となっておりタイミングよって番号が違います。
    1. stressボタンを押下してstressオプション入力ダイアログを表示します。
    1. ダイアログに以下を設定した後、okボタンを押下してテストを開始、ダイアログを閉じます。
      * test name : 任意ここでは"test"とします。
      * browser count : シミュレートするブラウザの数 ここでは10とします。
      * loop count : 選択した１エントリにリクエストする総数、ここでは100とします。
> > > シミュレートしたブラウザ１つあたりが平均10回ループするという意味です。
      * thinking time : ループする際に待ち合わせる時間 ここでは、1000(1秒)とします。
      * keepAlive : 負荷テスト中にkeepAliveを行うか否か、ここでははチェックします。
      * accessLog : 負荷テスト中にaccessLogを採取するか否か、ここでははチェックします。
> > > Phantom Server側の処理が増えます。後で１つ１つのリクエストを確認する必要がないのであれば、チェックは必要ありません。
      * responseHeaderTrace : 負荷テスト中にresponseHeaderをトレースするか否か、ここでははチェックします。
> > > accessLogをチェックした場合に有効になります。後で１つ１つのresponseHeaderを確認する必要がないのであれば、チェックは必要ありません。
      * responseBodyTrace : 負荷テスト中にresponseHeaderをトレースするか否か、ここでははチェックします。
> > > accessLogをチェックした場合に有効になります。後で１つ１つのresponseBodyTraceを確認する必要がないのであれば、チェックは必要ありません。
> > > 負荷オプションは、以下のようなjsonファイルから指定する事もできます。繰り返し、一括してテストする場合に有効です。
```
[
{"name":"test1","browserCount":"10","loopCount":"1000","isCallerKeepAlive":"true",thinkingTime:1000},
{"name":"test2","browserCount":"20","loopCount":"1000","isCallerKeepAlive":"true",thinkingTime:1000}
]
```
    1. 進捗は、stressタブのプログレスバーで確認できます。
    1. statusタブのchannelの項目が10＋数個となり、10ブラウザのシミュレーションである事が確認できます。
    1. stressタブを開くとリクエスト性能の一覧が表示されます。集計項目は以下です。
> > > 以下の項目が確認できます。
        * name:テスト名
        * start:テスト開始時刻
        * total(ave):テスト時間と1リクエストあたりの処理時間
        * browser:シミュレートしたブラウザの数
        * requestLine:シミュレートしたリクエスト行
        * status:HTTPステータス複数ある場合は、空白
        * len:レスポンス長
        * processTime:1リクエストの平均処理時間と分散
        * requestHeaderTime:リクエスト開始からrequestHeaderを送信し終わるまでの処理時間(ms)と標準偏差（ばらつき）
        * requestBodyTime:リクエスト開始からrequestBodyを送信し終わるまでの処理時間(ms)と標準偏差（ばらつき）
        * responseHeaderTime:リクエスト開始からresponseHeaderを受信し終わるまでの処理時間(ms)と標準偏差（ばらつき）
        * responseBodyTime:リクエスト開始からresponseBodyを受信し終わるまでの処理時間(ms)と標準偏差（ばらつき）
> > > 負荷により、一部のリクエストが"500"でレスポンスされている事が確認できます。
    1. 今回は、accessLogも採取したので、accessLogタブで１つ１つのリクエストも確認できます。