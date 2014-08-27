Phantom Proxy 1.3.0

Phantom Proxy は、javaで動作するWeb調査ツールです。
詳細は以下を参照してください。
http://code.google.com/p/coco-aweb/

１．インストール方法
 事前準備
Phantom Proxyは、Java SE 6以上のjava環境上で動作します。以下等を参照しjava実行環境を準備してください。
http://java.sun.com/javase/ja/6/download.html

1)以下よりパッケージをダウンロードしてください。
http://coco-aweb.googlecode.com/files/phantom-1.3.0.zip

2)ダウンロードしたファイルを任意のディレクトリに解凍、展開してください。
コマンド例）
 >jar xvf phantom.1.3.0.zip
 以降、展開したディレクトリを${phantom}とします。

3)起動シェルを編集してください。
 起動シェルは以下にあります。それぞれ以下の値を設定してください。
 Windowsの場合
  ${phantom}\bin\run.bat
  set JAVA_HOME=javaインストールディレクトリ
 Unix系の場合
  ${phantom}/bin/run.sh
  JAVA_HOME=javaインストールディレクトリ
  
  以下コマンドで実行権を付加します。
   #chmod +x ${phantom}/bin/run.sh

4)以下定義ファイルにネットワーク環境を記述してください。
 ${phantom}/ph/setting/ph.env.properties
 インストールしたサーバの情報を設定してください。
---------
 #This setting is enabled when the cleanup and initial startup.Referred to by ph.ini.properties
 #Information from the browsers to point to this server
 phantom.selfDomain=ブラウザからこのサーバを呼ぶためのipもしくはhost名
 phantom.selfPort=サーバを動作させるポート番号
---------
 以降${selfDomain},${selfPort}とします。ローカルプロキシーとして使用する場合は、編集の必要はありません。
(${selfDomain}:127.0.0.1,${selfPort}:1280)
 このサーバからwebアクセスする際に使用するproxyサーバの情報を設定してください。
---------
 #This setting is enabled when the cleanup and initial startup.Referred to by ph.ini.properties
 #Proxy information used by this server
 phantom.pacUrl=自動構成スクリプトによりproxyを動的に設定している場合
 phantom.proxyServer=http proxyのipまたはホスト名
 phantom.sslProxyServer=https proxyのipまたはホスト名
 phantom.exceptProxyDomains=proxyを使わないホスト一覧を設定してください。
 phantom.pacUrlが指定された場合、以降は無視されます。いずれも設定しない場合、proxyサーバを利用しません。
---------

２．起動方法
1)起動シェルを実行してください。
 Windowsの場合
    >${phantom}\bin\run.bat
 Unix系の場合
    #${phantom}\bin\run.sh
 以下のメッセージがコンソールに出力されます。
---------
 create CONFIG table（初回のみ）
 generate key cn:127.0.0.1 exitValue:0（初回のみ）
 mainHost listen start address:0.0.0.0/0.0.0.0:1280
---------
 起動シェルに"cleanup"引数を付加した場合、既存の設定内容をクリアして起動します。
2)以下がブラウザから表示できるようになります。
 Phntom Proxy Home
   http://${selfDomain}:${selfPort}/
 初期値:http://127.0.0.1:1280/
 Phntom Proxy Console（以降Console)
   http://${selfDomain}:${selfPort}/admin
 初期値:http://127.0.0.1:1280/admin 初期ログイン情報:admin/admin


