> 動作確認ブラウザと動作時のメモを記載します。

> # <img src='http://www.google.com/images/icons/product/chrome-16.png'>Googole Chrome</h1>
    * 10.0.648.204
    * 12.0.725.0 dev
    * 16.0.899.0 dev-m
    * 26.0.1410.64 m
    * 30.0.1599.101 m
    * 31.0.1650.57 m
> > > chromeのappCacheは、コンテンツを置き換えても反映されない事がある。 以下をアドレスバーに入れると明示的に削除できる。
```
chrome://appcache-internals/
```


> # <img src='http://windows.microsoft.com/favicon.ico?dmy.png' height='16' width='16'>Internat Explorer</h1>
    * 8.0.6001.18702
    * 8.0.6001.19019
    * 9.0.8080.16413
> > 上記は、websocketがサポートされていません。
    * 10.0.9200.16540
    * 10.0.9200.16721


> # <img src='http://devimages.apple.com/assets/elements/safari/devcenter/safari-logo.png' height='20' width='20'>Safari(Windows)</h1>
    * 5.0(7533.16)
      * ~~wssのWebSocketが動作しない~~ 証明書をインポートすると動作した
> > > > http://stackoverflow.com/questions/4014055/how-to-debug-safari-silently-failing-to-connect-to-a-secure-websocket
> > > > 証明書の問題らしい
    * 5.0.5(7533.21.1)
      * **vistaでloginできるがadminを開くと落ちる**
> > > > WebSocketのnewで落ちている。
    * 5.1(7534.50)
      * 5.0と同様「信頼されたルート証明機関」に証明書をインポートするとwss://がつかえる。


> # <img src='http://mozilla.jp/img/firefox/favicon.ico?dmy.png' height='16' width='16'>Firefox</h1>
    * 3.6.3
    * 3.6.16
    * 4.0b12
      * version4からWebSocketが使える。有効にするには、以下の設定。
        1. アドレスバーに “about:config” と入力
        1. network.websocket.override-security-block の値を"true"に変更
    * 7.0
    * 20.0.1
    * 25.0

> # <img src='http://jp.opera.com/favicon.ico?dmy.png' height='16' width='16'>Opera</h1>
    * 11.10 Build 2092
      * WebSocketを有効にするには、以下の設定。
        1. アドレスバーに “about:config” と入力
        1. User Prefs > Enable WebSockets チェック
    * 11.51 Build 11.51