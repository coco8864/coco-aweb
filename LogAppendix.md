# accessLog #

```
2013-11-11 00:31:16,806 153.131.140.251 - "GET /pub/images/login.gif HTTP/1.1" 200 883 3# 継続行
 mainHost,sslWeb,F,-,-,0,1,2,0,15,31|2|23|3
```
  * リクエスト受付時刻
  * ip
  * loginId
> > 認証未の場合-
  * requestLine
    * SPDYレポートの場合
> > 例
```
 "in[0:0 1:2 2:0 3:0 4:2 5:0 6:0 7:0 8:0 9:1]out[0:2 1:0 2:2 3:0 4:1 5:0 6:0 7:1 8:0 9:0]lastGoodStreamId:3"
```
      * 0 TYPE\_DATA\_FRAME
      * 1 TYPE\_SYN\_STREAM
      * 2 TYPE\_SYN\_REPLY
      * 3 TYPE\_RST\_STREAM
      * 4 TYPE\_SETTINGS
      * 6 TYPE\_PING
      * 7 TYPE\_GOAWAY
      * 8 TYPE\_HEADERS
      * 9 TYPE\_WINDOW\_UPDATE
  * statusCode
  * response length
  * 処理時間(ms)
  * #
  * realHost名
  * sourceType
    * plainWeb
    * sslWeb
    * plainProxy
    * sslProxy
    * ws
    * wss
    * wsHandshake
    * wsOnMessage
    * wsPostMessage
    * simulate
    * spdy
  * destinationType
  * contentEncoding
  * transferEncoding
  * requestHeaderTime
  * requestBodyTime
  * responseHeaderTime
  * responseBodyTime
  * channnelId(cid)
  * spdy情報
    * spdyVersion
    * channdelId
    * straemId
    * spdyPri

# Pool log #
Console stautsタブ 統計情報にあるPoolCheckボタンを押下した際ph.logに出力されるログ
```
... - naru.aweb.handler.FileSystemHandler():90:7:0:7:7:1000:0
... - java.nio.ByteBuffer(16384):15597:13:0:13:13:5120:0
... - java.nio.ByteBuffer[1]:10763:8:1:7:8:5120:0
```
  * 対象class名
    * ()内の数字は、java.nio.ByteBufferのバッファ長
    * [[.md](.md)]付の名前はそのクラスの配列を表す。[[.md](.md)]内は配列長
  * 要求数
  * 作成数
  * 使用数
  * pool数
  * max使用数
  * pool限界
  * gc数