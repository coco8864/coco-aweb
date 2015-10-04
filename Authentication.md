# 認証 #
> Phantom Serverは、２つの認証方法を提供しています。１つは、ph認証で、リクエストの種類に依存せず、複数の[mapping](Mapping.md)を一括して認証することができます。もう一つは、mapping認証で、単一の[mapping](Mapping.md)に閉じて認証することができます。

> ## ph認証 ##
> > 複数のmappingをプロトコル、通信方式に依存せずに一括して認証する仕組みです。ブラウザを対象に認証を行います。認証方法は以下の４つから選択することができます。
    * Basic認証
    * Digest認証
    * FormBasic認証
    * FormDigest認証
    * Internet認証
      * facebook oauth
      * twitter oauth
      * google oauth
      * openId


> loginしたUserのrolesとそのリクエストで選択された[mapping](Mapping.md)に付加されたrolesに一致があれば、そのリクエスト処理が許可されます。一致がない場合は、認証に成功しても処理が許可されません。

> cookieベースでの認証ですが、mapping毎、proxy動作の場合ドメイン毎に異なるidを採番します。

> ### ph認証の設定方法 ###
    1. Consoleから認証設定ができます。
```
 settingタブ 認証設定
```
      * 認証方法 上記4つの認証方法から選んでください。
      * sessionTimeout アクセスがなくなってから自動的にlogoutされるまでの時間を設定します。
      * logoutUrl logout時に表示するurlを設定します。
    1. [mapping](Mapping.md)のrolesは、以下で確認、設定ができます。
```
 mappingタブ roles列
 mappingタブ 対象のidをclick rolesを設定後 save or save newボタン
```
    1. userのrolesは、以下で確認、設定ができます。
```
 userタブ roles列
 userタブ 対象のidをclick rolesを設定後 saveボタン
 userタブ 新規追加ボタン rolesを設定後 saveボタン
```

> ## mapping認証 ##
> > 単一のmappingに閉じて認証する仕組みです。ph認証は、認証処理中にjavascriptを使用するため、ブラウザ以外のクライアントには使用できません。たとえばmavenが発行する通信では認証することができません。このような場合には、mapping認証を利用してください。認証方法は以下の２つから選択することができます。
    * Basic認証
    * Digest認証
> > loginしたUserのrolesとそのリクエストで選択された[mapping](Mapping.md)のoptions,authに設定されたrolesに一致があれば、そのリクエスト処理が許可されます。一致がない場合は、認証に成功しても処理が許可されません。
> > #### 注）WebSocketではmapping認証を利用できません。 ####



> ### mapping認証の設定方法 ###
    1. Consoleのmappingタブで設定できます。
```
 mappingタブ 対象のidをclick rolesを空白 optionsにauth定義を設定 save or save newボタン
```
    1. authの設定内容は以下です。
      * schema "basic"（Basic認証) or "digest"（Digest認証)
      * realm Basic認証で使われるrealm、（Digest認証の場合は無効)
      * roles 利用可能なUserが持つrole列
> > options 設定例)
```
{auth:{scheme:"basic",realm:"sampleRealm",roles:"role1,role2"}}
```