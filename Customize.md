# カスタマイズ #
> Phantom Serverのカスタマイズ方法を説明します。
> Phatom Serverの設定はkey=valueの形で保持しています。一旦取り込まれたものは、永続管理されているため、再起動しても有効です。Consoleから設定できない項目については、設定ファイルから実施することができます。

> ## カスタマイズファイル ##
> > 設定ファイルはその設定値の種類、設定タイミングに応じて以下のファイルで行うことができます。
      1. 初回起動後、設定変更ができない設定項目
> > > > 初回起動時および起動シェルにcleanupオプションを指定した後、設定変更できない項目については、以下のファイルで設定します。
> > > > > ${phantom}/ph/classes/phantom.properties

> > > > 以降編集しないでください。
      1. 初回起動時に一回だけ取り込む設定項目（その後実行時に設定変更可能）
> > > > 初回起動時および起動シェルにcleanupオプションを指定した場合に１回だけ取り込む項目については、以下のファイルで設定します。
> > > > > ${phantom}/ph/setteing/ph.init.properties

> > > > この設定ファイルは、以降の起動では取り込まれません。
      1. 起動時に取り込みたい設定項目
> > > > 任意の起動時に取り込みたい設定項目は、以下のファイルで指定します。
> > > > > ${phantom}/ph/setteing配下、拡張子.propertiesのファイル

> > > > 取り込み後、取り込み日付を含むファイル名に変名されるため次回起動時には反映されません。

> ## 設定項目 ##
> > ここではConsoleからは設定できない項目を説明します。
      * trustStorePassword
> > > > キーストアのパスワード。初期値は"changeit"です。phantom.propertiesで指定してください。
      * authenticateRealm
> > > > ph認証のBasic,Digest認証およびmapping認証のDigest認証で使われるrealm。初期値は"phantomProxyRealm"です。phantom.propertiesで指定してください。
      * limitRequestFieldSize
> > > > リクエストヘッダの最大サイズ。デフォルト値は8192です。
      * sessionCookieKey
> > > > セション管理に使われるCookieのキー。デフォルト値は"phId"です。
      * phantomServerHeader
> > > > Phantom Proxyがwebサーバとして動作する際のServerヘッダの値。指定がない場合は、Serverヘッダを付加しません。初期値は、"phantomProxy/${version}"です。