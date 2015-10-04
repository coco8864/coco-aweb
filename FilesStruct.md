# ファイル構成 #

> Phantom Proxyのファイル構成について説明します。
```
phantom
├─bin 起動シェル格納ディレクトリ
├─system queuelet container system
├─common queuelet container common
├─conf phantom.xml
├─log queuelet container logディレクトリ
└─ph
    ├─admin Consoleコンテンツ
    │  ├─auth 認証コンテンツ
    │  └─fileSystem ファイルリスト
    ├─classes
    ├─db DB格納ディレクトリ
    ├─docroot 公開ディレクトリ
    │  └─pub
    │      ├─css
    │      │  └─images
    │      ├─images
    │      └─js
    ├─injection 内部ディレクトリ
    ├─portal 内部ディレクトリ
    ├─lib 依存ファイルディレクトリ
    ├─log aweb/async出力ディレクトリ
    ├─security SSL証明書格納ディレクトリ
    ├─setting 設定ファイル
    ├─store storeディレクトリ
    └─tmp 一時ファイル格納ディレクトリ
```