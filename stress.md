# stress機能 #
> stress機能では、あらかじめ保存した通信リクエストを繰り返してリクエストすることで簡単に負荷テストを行うことができます。
    1. accessLogタグのでテスト対象のリクエストを選択、stressボタンを押下します。
    1. 表示されたダイアログから２つの方法でstressオプションを指定します。
      * 直接入力<br />ダイアログから以下項目が入力できます。
        * test name:テスト名を指定します。
        * blowser count:シミュレートするブラウザ数
        * loop count:総計でリクエストする回数
        * keepAlive:keepAlive通信をするか否か
        * accessLog:accessLogを記録するか否か
        * headerTrace:responseHeaderを記録するか否か
        * bodyTrace:responseBodyを記録するか否か
      * JSON入力<br />以下のようなファイルからダイアログで入力できる項目が指定できます。
```
[
{"name":"test1","browserCount":"10","loopCount":"1000","isCallerKeepAlive":"true",thinkingTime:1000},
{"name":"test2","browserCount":"20","loopCount":"1000","isCallerKeepAlive":"true",thinkingTime:1000}
]
```
    1. stressタブでstress進捗を確認
    1. stressタブで負荷結果確認
> > 以下の項目が確認できます。
      * name:テスト名を指定します。
      * start:テスト開始時刻
      * total(ave):テスト時間と1リクエストあたりの処理時間
      * browser:シミュレートしたブラウザの数
      * requestLine:シミュレートしたリクエスト行
      * status:HTTPステータス複数ある場合は、空白
      * len:レスポンス長
      * processTime:1リクエストの平均処理時間と分散
      * requestHeaderTime:リクエスト開始からrequestHeaderを送信し終わるまでの処理時間と分散
      * requestBodyTime:リクエスト開始からrequestBodyを送信し終わるまでの処理時間と分散
      * responseHeaderTime:リクエスト開始からresponseHeaderを受信し終わるまでの処理時間と分散
      * responseBodyTime:リクエスト開始からresponseBodyを受信し終わるまでの処理時間と分散
