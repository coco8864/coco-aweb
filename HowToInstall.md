# インストール #

事前準備

> Phantom Serverは、Java SE 6以上のjava環境上で動作します。以下等を参照しjava実行環境を準備してください。(SPDYを有効にするには、Java SE 7以上が必要です)

> http://www.oracle.com/technetwork/java/javase/downloads/index.html

  1. 以下より最新のパッケージをダウンロードしてください。
> > http://code.google.com/p/coco-aweb/downloads/list
  1. ダウンロードしたファイルを任意のディレクトリに解凍、展開してください。
> > コマンド例）
```
>jar xvf phantom-1.6.1.zip
```
> > 以降、展開したディレクトリを${phantom}とします。
  1. 起動シェルを編集してください。
> > 起動シェルは以下にあります。それぞれ以下の値を設定してください。
    * Windowsの場合
> > > ${phantom}\bin\run.bat
```
set JAVA_HOME=javaインストールディレクトリ
```
    * Unix系の場合
> > > ${phantom}/bin/run.sh
```
JAVA_HOME=javaインストールディレクトリ
```
> > > 以下コマンドで実行権を付加します。
```
#chmod +x ${phantom}/bin/run.sh
```
  1. 必要な場合、以下定義ファイルにネットワーク環境を記述してください。

> > ローカルプロキシーとして使用し、ポート1280が使用可能で、webアクセスにproxyサーバの必要がない環境では、編集の必要はありません。
> > > ${phantom}/ph/setting/ph.env.properties
    * インストールしたサーバの情報を設定してください。
```
#This setting is enabled when the cleanup and initial startup.Referred to by ph.ini.properties
#Information from the browsers to point to this server
#phantom.selfDomain=ブラウザからこのサーバを呼ぶためのipもしくはhost名
phantom.selfPort=サーバを動作させるポート番号
```

> > 以降${selfDomain},${selfPort}とします。ローカルプロキシーとして使用する場合は、編集の必要はありません。(${selfDomain}:IPアドレス,${selfPort}:1280)
    * このサーバからwebアクセスする際に使用するproxyサーバの情報を設定してください。
```
#This setting is enabled when the cleanup and initial startup.Referred to by ph.ini.properties
#Proxy information used by this server
phantom.pacUrl=自動構成スクリプトによりproxyを動的に設定している場合
phantom.proxyServer=http proxyのipまたはホスト名:ポート番号
phantom.sslProxyServer=https proxyのipまたはホスト名:ポート番号
phantom.exceptProxyDomains=proxyを使わないホスト一覧を設定してください。
```
> > phantom.pacUrlが指定された場合、以降は無視されます。いずれも設定しない場合、proxyサーバを利用しません。
  1. クライアント環境にSSL証明書を登録してください。
> > SSL運用する場合には、クライアントに必要証明書を信頼されたルート証明機関に登録してください。
> > Phantom Serverは、動的にSSL証明書を作成します。作成されて証明書は以下Server上のファイルもしくはConsoleから取得できます。(インストール方法は、Windowsの場合ファイルのダブルクリックで登録できます）
    * Server上のファイル
```
${phantom}/ph/security/${ホスト名}.cer
```
    * Console
```
stautsタブ 証明書ダウンロード
```