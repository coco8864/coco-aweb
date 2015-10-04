# アプリケーションサーバとして利用する場合 #
Phantom Serverはアプリケーションサーバとして利用する事ができます。

> ## facebook authentication ##
> facebookを認証に利用する設定

> ## server module ##
> serverモジュールの配置とmapping定義を説明します。
> > ### Linklet application ###
> > naru.aweb.link.api.Linkletインタフェースを実装してアプリケーションを作成
> > ### Handler application ###
> > naru.aweb.handler.WebHandlerクラスを継承してアプリケーションを作成


> ## client module ##
> client資源の配置とjavascriptAPIについて説明します。
> > ### publis/subscribe appliction ###
> > websocket(利用できない場合はシミュレート）を用いたインタラクティブなapplicationを実現
> > ### storage application ###
> > offline機能を利用しながら安全に個人情報をブラウザに保持