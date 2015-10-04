# replay機能 #

> replay機能では、proxy対象のサーバにアクセスする前に、保存してあるaccessLogやアップロードしたコンテンツからレスポンス検索します。proxy対象が実際には存在しない場合の表示画面の再現やコンテンツを変更したい場合に利用することができます。


> ブラウザから任意のドメインに"re."を付加してリクエストすると利用できます。
> > replay urlの例)
```
 http://example.com/path -> http://re.example.com/path
 https://example.com/path -> https://re.example.com/path
```
> > 具体的な使用例を[コンテンツ置換](ContentsReplace.md)に記載しました。


> ## コンテンツの検索順番 ##
> > replay機能経由で返却するコンテンツは、リクエストされたurlをキーとして、以下の順番で検索されます。
    1. settingタブ Replay File登録でアップロードされたコンテンツ
> > > アップロード時に指定したpathから返却コンテンツを検索します。
    1. accessLogで保存されているコンテンツ
> > > accessLogに記録されたurlを検索します。複数見つかった場合は、id番号が一番大きいコンテンツが選ばれます。
    1. サーバからのレスポンス
> > > 上記に存在しなかった場合、proxy動作を行い実サーバからレスポンスを返却します。