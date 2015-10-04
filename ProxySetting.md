# Proxy設定 #
ブラウザのProxy設定を変更すると、Phantom Serverをより効果的に利用できます。自動構成スクリプトを利用することで、Phantom Serverが処理すべきリクエストだけをPhantom Serverに向けることができます。

## 自動構成スクリプトの設定箇所 ##
> 自動構成スクリプトの設定はブラウザ毎に異なります。
> Chrome,IE,Safariは、同一OS設定を共有して参照しています。
  * Googole Chromeの場合
> > Googole Chromeの設定（右上スパナアイコン）> オブション > 高度な設定 ネットワーク プロキシ設定の変更  接続タブ　LANの設定
  * Internat Explorerの場合
> > ツール > インターネットオプション 接続タブ　LANの設定
  * Safari(Windows)の場合
> > 右上歯車アイコン > 設定 > 詳細タブ プロキシ:設定を変更ボタン 接続タブ　LANの設定
  * Firefox3の場合
> > ツール >　オプション > ネットワークタブ > 接続設定 自動プロキシ設定スクリプトURL
  * Firefox4の場合
> > Firefox(左上） >　オプション > オプション ネットワークタブ > 接続設定 自動プロキシ設定スクリプトURL
  * Operaの場合
> > メニュー(左上） >　設定 > 設定 詳細タグ ネットワーク プロキシサーバーボタン  プロキシの自動設定を使用する


## 自動構成スクリプトの設定内容 ##

> 運用形態にあわせて、ブラウザの自動構成スクリプトに以下を指定してください。
  * ローカルプロキシとして動作させる場合
```
　file://${phantom}\ph\proxy.pac
```
> > 指定例:[file://c:\phantom\ph\proxy.pac](file://c:\phantom\ph\proxy.pac)
  * サーバで動作させる場合
```
　http://${selfDomain}:${selfPort}/proxy.pac
```
> > 初期値:http://127.0.0.1:1280/proxy.pac