# 構成 #
> Phantom Serverの構成を説明します。

> ## モジュール構成 ##
> > Phantom Serverは、３つのモジュールからできています。
    1. [Queuelet](http://code.google.com/p/coco-queuelet/)
> > > java軽量container、処理をqueue単位で実行するフレームワーク
    1. [naru.async](http://code.google.com/p/coco-async/)
> > > スケーラビリティ性能を志向したqueuelet上の通信ライブラリ
    1. [naru.aweb](http://code.google.com/p/coco-aweb/)（このプロジェクト）
> > > Phantom Server本体、naru.asyncを利用したプロトコルハンドリングとアプリケーション処理


> ## ファイル構成 ##
> Phantom Serverのファイル構成について説明します。
```
${phantom}
├─bin 起動シェル
├─system queuelet container system
├─common queuelet container common
├─conf phantom.xml...queuelet containerの定義
├─log queuelet container logディレクトリ
├─watch 監視処理ディレクトリ
└─ph
    ├─apps
    │  ├─admin Phantom Server Console資源
    │  └─auth 認証用資源
    ├─classes  クラスファイル
    ├─lib 依存ライブラリ
    ├─db DB格納ディレクトリ
    ├─docroot 公開ディレクトリ
    │  └─pub
    ├─log log出力ディレクトリ
    ├─security SSL証明書格納ディレクトリ
    │  └─CA openSSL CA認証局
    ├─setting 設定ファイル郡
    ├─store 通信バッファファイルディレクトリ
    └─tmp uploadファイル,accessLogのアーカイブ、定期的に消してよい
```
  * Phantom Serverを初期化起動したい場合は、${phantom}/ph/db配下、${phantom}/ph/store配下を削除して起動すればよい。（起動シェルに"cleanup"パラメタを指定するのとほぼ同じ）
  * ${phantom}/ph/securityには、java キーストアが格納されます。ファイル名は、"cacerts\_ドメイン名"です。ここに信頼済みの証明書を格納すれば、ブラウザで証明書登録しなくてもSSLの警告がでなくなります。キーストアの操作は、[keytool](http://java.sun.com/j2se/1.5.0/ja/docs/ja/tooldocs/solaris/keytool.html)で行うことができます。格納されるキーストアの初期パスワードは、"changeit"です。

  * 画面コンテンツの作成には、Velocityテンプレートを利用しています。拡張子の意味は、以下
    * .vsp
> > > Velocity Server Page...HTMLタグを含むhtml文書、ブラウザに返却される
    * .vsf
> > > Velocity Server Fragment...ajax経由で要求される、html文書の一部、ブラウザに返却される
    * .vpf
> > > Velocity Parse Fragment...vspやvspからparseされて利用されるテンプレート、直接ブラウザに返却されることはない