# 起動・停止 #
Phantom Serverの起動方法、停止方法を説明します。起動はコマンドで実施し停止は、ブラウザからの操作となります。

## 起動方法 ##
  1. 起動シェルを実行してください。
    * Windowsの場合
```
>${phantom}\bin\run.bat
```
    * Unix系の場合
```
#${phantom}\bin\run.sh
```
> > 以下のメッセージがコンソールに出力されます。
```
WatchProcess:startChild:phantom
create CONFIG table（初回のみ）
generate key cn:127.0.0.1 exitValue:0（初回のみ）
mainHost listen start address:0.0.0.0/0.0.0.0:1280
selfDomain:${ホスト名}
adminUrl:https://${ホスト名}:1280/admin(ここにアクセスすればConsoleが開きます)
WatchDeamonQueuelet:start success:phantom Thu Nov 03 20:43:19 JST 2011 interval:60000（起動後１分後）
```
      * 起動シェルに"cleanup"引数を付加した場合、既存の設定内容をクリアして起動します。
  1. 以下がブラウザから表示できるようになります。
> > この時[SSL証明書を登録](HowToInstall.md)しない場合ブラウザが警告を表示します。
    * Phntom Server Home
```
http://${selfDomain}:${selfPort}/
https://${selfDomain}:${selfPort}/
```
> > > 初期値:https://IPアドレス:1280/
    * Phntom Server Console（以降Console)
```
https://${selfDomain}:${selfPort}/admin
```
> > > 初期値:https://IPアドレス:1280/admin
> > > 初期ログイン情報:admin/admin

## 停止方法 ##
  1. ブラウザよりConsoleにloginしてください。
  1. pahtom server停止ボタンを押下してください。

> 以下のようなメッセージがコンソールに出力され、起動シェルが終了します。
```
mainHost listen stop
phantom:exitValue:0
```