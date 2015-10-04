# realHost #
> Phantom Serverは、realHostからリクエストの接続待ちを行います。同時に複数のrealHostで運用できます。運用中に追加、削除、編集、bind(起動)およびunbind(停止)することができます(mainHostは例外)。

> realHostは、Consoleの以下から設定できます。
```
mappingタブ RealHost設定
```

> realHostで受け付けたリクエストは、受信したデータからプロトコルを判定します。同一のrealHostで全てのプロトコル(web/proxy,http/https,WebSocket)に対応できます。

> ## mainHost ##
> > nameが"mainHost"のrealHostは、認証やConsoleを動作させるため特別なrealHostとなっています。停止することができず、編集した場合、次回Phantom Server起動時に有効となります。


> ## realHostの設定値 ##
> > realHostの定義で設定できる属性について説明します。
    * init
> > > Phantom Server起動時に同時にbind(起動)するか否かを設定します。
    * bindHost
> > > リクエストを接続待ちするホスト名もしくはipです。複数ipを持つ環境で運用する場合、接続待ちするipを特定する場合に指定してください。その環境にあるすべてのipからリクエストを受け付ける場合は、`*`が指定できます。
    * backlog
> > > リクエストの接続待ちキューの最大数を指定します。
    * sslCommonName
> > > このrealHostに直接SSL接続した場合に返却するSSL証明書のcommon nameを指定します。(ssl proxy接続する場合には、接続時に明示されるのでこの値は使われない)
    * whiteIpPattern
> > > リクエストを許可するipの正規表現を指定します。blackIpPatternより先に参照され優先されます。
> > > > 指定例)
      * 127.0.0.1だけ許可する
```
127\.0\.0\.1
```

> > > 正規表現の記述内容については、以下を参考にしてください。
> > > > http://java.sun.com/javase/ja/6/docs/ja/api/java/util/regex/Pattern.html
    * blackIpPattern

> > > リクエストを拒否するipの正規表現を指定します。whiteIpPatternに一致しない場合評価され、この定義にも一致しないipは、接続が許可されます。
> > > > 指定例)
      * すべてのipを拒否する
```
.*
```