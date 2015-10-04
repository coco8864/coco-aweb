# 汎用サーバ #

> Phantom Serverは[mapping](Mapping.md)の定義により一般のサーバと同じ動作をさせることができます。ここでは、Phantom Serverを各種サーバにするための[mapping](Mapping.md)定義を説明します。
> 基本的には、以下２つの事項を考慮します。
    1. source: どのようなプロトコルで、どこからリクエストを受け付けるか？
    1. destination: 何からレスポンスを作成するか？
> [認証](Authentication.md)は、全ての定義で必要に応じて設定してください。

> ## webServer ##
> > Webサーバにするための定義を説明します。
> > sourceをwebリクエスト、destinationをFILEにすれば、Webサーバの動作となります。
    * realHost サービスを提供するホスト名やportに応じてrealHost名を指定
    * sourceType "WEB"を選択します
    * secureType httpでサービスする場合、"PLAIN",httpsでサービスする場合、"SSL"を選択します。
    * sourcePath サービスを提供するurlのpathに依存して指定してください。
    * destinationType "FILE"を選択します。
    * destinationPath コンテンツが格納されたトップディレクトリを絶対パスで指定

> ## reverseProxyServer ##
> > revers proxyサーバにするための定義を説明します。
> > sourceをwebリクエスト、destinationをHTTP/HTTPSにすれば、Reverse Proxyとなります。
    * realHost サービスを提供するホスト名やportに応じてrealHost名を指定
    * sourceType "WEB"を選択します
    * secureType httpでサービスする場合、"PLAIN",httpsでサービスする場合、"SSL"を選択します。
    * sourcePath サービスを提供するurlのpathに依存して指定してください。
    * destinationType 対象サーバに依り"HTTP","HTTPS"を選択します。
    * destinationServer 対象サーバのホスト名(port番号含む）を指定
    * destinationPath 対象サーバのpathを指定（全ての場合は、`"/"`)

> ## proxyServer ##
> > proxyサーバにするための定義を説明します。
> > sourceをproxyリクエスト、destinationをHTTP/HTTPSにすれば、proxyとなります。
    * realHost proxyサーバを立てたいホスト名やportに応じてrealHost名を指定
    * sourceType "PROXY"を選択します
    * secureType http proxyとする場合、"PLAIN",https proxyとする場合、"SSL"を選択します。
    * sourceServer `"*"`を指定します。
    * sourcePath `"/"`を指定します。
    * destinationType secureTypeを"PLAIN"とした場合"HTTP"、secureTypeを"SSL"とした場合、"HTTPS"を選択します。
    * destinationServer `"$1"`を指定します。
    * destinationPath `"/"`を指定します。
    * options secureTypeを"SSL"とした場合、`peek:false`}を追加

> ## virtualHost ##
> > virtual hostを行うための定義を説明します。
> > virtual hostは、webリクエストを受け付けた際の、source（どこからリクエストを受け付けるか）で表現できます。
    * sourceServer ブラウザから、Phantom Serverを呼び出す際のhost名を指定します。
    * sourceType "WEB"を選択します
> > [webサーバ](Servers#webServer.md)、[revers proxyサーバ](Servers#reverseProxyServer.md)のように、ブラウザがPhantom Serverをwebサーバと判断してリクエストする際に利用できます。
