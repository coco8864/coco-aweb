# セキュア設定 #

> Phantom Proxyをよりセキュアに運用ための設定を記載します。状況に応じて設定すればセキュリティレベルが向上します。

> ## クライアントipの制限 ##
> > [reaHost](RealHost.md)で受け付けるクライアントipを制限します。
```
 settingタグ RealHost設定 エントリの設定ボタン
 whiteIpPattern:接続を許可するipの正規表現、例）127\.0\.0\.1
 blackIpPattern:".*"（whiteIpPatternに一致しないものは許可しない）
```

> ## 通信の暗号化 ##
> > 認証およびConsoleでの通信を暗号化(SSL)することができます。
> > mappingより"Phantom Proxy Home"(/),"Phantom Proxy Console"(/admin)および"auth handler"(/auth)のsecureTypeをsslに変更してください。
```
 mappingタグ notesが"Phantom Proxy Home"のidをclick secureTypeにSSLを選んでsaveボタン
 mappingタグ notesが"Phantom Proxy Console"のidをclick secureTypeにSSLを選んでsaveボタン
 mappingタグ notesが"auth handler"のidをclick secureTypeにSSLを選んでsaveボタン
 mappingタグ 反映ボタン
```
> > この後、一旦homeに戻ってhomeにあるConsoleへのリンクを押下してください。
> > Consoleのアドレスは、以下となります
```
  https://${selfDomain}:${selfPort}/admin
```
> > 証明書は独自に発行したものであるため、信頼できない証明書として警告が表示されます。
> > そのまま利用してもかまいませんが、以下の方法で対応することもできます。
      * ${phantom}/ph/security配下に信頼済みの証明書を格納する。
      * 以下の手順で「信頼されたルート証明機関」に証明書をインポートする。
```
アドレスバー右、証明書エラー,鍵マーク等 > 証明書の表示 > 証明書のインストール > 次へ > 
証明書をすべて次のストアに配置する > 参照 > 
信頼されたルート証明機関 > OK > 次へ > 完了
```
> > 注）ブラウザにより操作が異なります。chromeは「証明書のインストール」がでない

> ## 認証方法 ##
> > パスワード情報が直接ネットワークに流れない認証方法を選択します。
    * [ph認証](Authentication.md)の場合Digestもしくは、formDigestを選択します。
```
 settingタグ 認証設定
```
    * [mapping認証](Authentication.md)の場合Digestを選択します。
```
 mappingタグ options 例）auth:{scheme:"digest",roles:"role1,role2"}
```

> ## 初期ユーザ名を利用しない ##
> > 初期のユーザ名(admin)、パスワード(admin)は公開されています。rolesに"admin"を含めばユーザ名に依存せずすべての処理が許可されます。
> > 初期のユーザを削除するとともに、"admin"roleを持つユーザを作成しそのユーザで操作してください。
```
 userタグ 新規ボタン userId passwordに任意の文字列を指定 rolesに"admin"を設定 saveボタン
 userタグ adminを選択 deleteボタン
```