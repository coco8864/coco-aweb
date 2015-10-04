# 新着情報 #

> Phatom Server(Proxy)の改版情報を提供します。
> ## 2013.11.14 Phatom Server1.6.1 公開 ##
  * 性能改善
  * SPDY3.1対応
  * PhantomLink storageにserver側のstorage APL\_USER,APL\_GLOBALを追加
> ## 2013.10.28 Phatom Server1.6.0 公開 ##
  * Application Frameworkを名称PhantomLinkに変更、strorage機能を追加
  * PhantomLinkのdocument&テストアプリを追加
  * appCache機能を利用
  * offline認証機能を追加
  * opneSSLを利用してCA認証局を同梱、SSLトレース時にセキュリティ警告が出ないように対応
  * 証明書のダウンロード機能
  * internet認証を追加
    * openID認証
    * oauth認証(facebook,twitter,google)
  * websocket負荷機能を強化

> ## 2013.4.15 Phatom Server1.5.0 公開 ##
  * 名称をPhantom Proxy -> Phantom Serverに変更
  * Phantom Application(PA:application frame work)サポート
  * Spdyサポート
  * 管理コンソールをPA Applicationに対応
  * 管理コンソールをデフォルトをSSL
  * バグ修正


> ## 2012.7.19 Phatom Proxy1.4.0 公開 ##
  * sessionStorage,Cross-document messagingを必須とした
  * クロスサイト認証を可能とした
  * WebSocketsフレームワークwsqを追加
  * mappingの簡易作成機能
  * 管理コンソールのタブの位置をhash対応、直接表示できるようにした
  * バグ修正


> ## 2012.1.14 Phatom Proxy1.3.0 公開 ##
  * 認証方式の見直し
  * welcome画面では、roleに従って利用可能なWeb mappingをリスト
  * accesslogをaccesslog.logに独立して採取
  * WebSocketのtraceを、onMessage,postMessage単位で採取
  * ダウンリカバリの強化(parsist store)
  * バグ修正
  * 性能改善
    1. poolサイズの最適化
    1. ファイルキャッシュの実装
    1. DBアクセスの削減


> ## 2011.11.03 Phatom Proxy1.2.0 公開 ##
  * WebSocket hybi-17対応 ie以外のブラウザでWebSocketが利用可能
  * traceタブに機能追加、任意のリクエストを可能とした
  * stressタブにprogressバーを表示
  * 性能改善
  * timeout値の見直し
> ## 2011.6.04 Phatom Proxy1.1.0 公開 ##
  * settingタブより、HTML5機能を使用選択
  * text表示、編集ダイアログのchunk,gzip,encodeを自動設定
  * stressダイアログにthinking timeを追加、リクエスト間隔を指定
  * stressダイアログにファイル指定を追加、複数テストの連続実施
```
ファイル形式:
[
{"name":"test1","browserCount":"10","loopCount":"1000","isCallerKeepAlive":"true",thinkingTime:1000},
...
]
```
  * statusタブから再起動指定、再起動時にjavaHeapSize,データ初期化を選択可
  * プロセス監視機能、異常終了時,ハングアップ時に自動で再起動
> ## 2011.4.23 Phatom Proxy1.0.0 初公開 ##