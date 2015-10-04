# Getting Started #

> Phanotm Serverの基本的な使い方を説明します。

> Phanotm Serverは、自端末にインストールしローカルプロキシとして利用する形態と、サーバにインストールして複数ユーザから使う形態があります。
> 既にサーバでPhantom Serverが運用されている場合には、[Proxy設定](ProxySetting.md)でブラウザの設定を行うと共に、Consoleへのログイン情報を入手してください。

> 以下にインストールから簡単な利用方法までの手順を説明します。
    * [インストール](HowToInstall.md)
    * [起動・停止](HowToStart.md)
    * [Proxy設定](ProxySetting.md)
    * [トレース採取](SampleTrace.md)

> この後、Phantom Server Console（以降Console)から各種操作、設定が可能となります。
```
  https://${selfDomain}:${selfPort}/admin
```
> > 初期値:https://127.0.0.1:1280/admin
> > 初期ログイン情報:admin/admin

> 以降の基本的な操作の流れは、以下となります。

  1. urlアドレスのドメイン名に"ph."を付加してブラウザからリクエストするとトレースの採取
> > トレース採取urlの例)
```
 http://example.com/path -> http://ph.example.com/path
 https://example.com/path -> https://ph.example.com/path
```
  1. ConsoleのaccessLogタブ、traceタブで採取データの調査、編集、再実行
  1. urlアドレスのドメイン名に"re."を付加してブラウザからリクエストすると、保存されている採取データから探してレスポンスをブラウザに返却。（replay機能）
> > replay urlの例)
```
 http://example.com/path -> http://re.example.com/path
 https://example.com/path -> https://re.example.com/path
```