# PhantomLinkプログラミングガイド #

  * html5アプリケーションフレームワーク
> > PhantomLinkは、以下のようなhtml5技術を利用したapplication frameworkです。
    * websocket
    * Cross-document messaging
    * webstorage
    * AppCache
> > これらのhtml5技術は、単体では実際にアプリケーションに適用できないものがあります。たとえば、websocketの認証は、websocketの技術だけでは実装できず、Cross-document messagingを利用したAPI認証の仕組みが必要です。また、webstorageに保存したデータは誰でも参照可能な状態になるため、端末のマルチユーザ利用を想定した場合AppCache機能を利用したoffline認証の仕組みが必要となります。
> > PhantomLinkは、phantom serverの機能を利用し、html5技術をラッピングしてapplicationの作成を助けるframeworkを提供します。<br />
> > Phantom Serverには、PhantomLinkのデモが含まれています。利用するためには、以下のmappingを有効にしてください。
      * link1 for PhantomLink test
      * link2 for PhantomLink test
      * link1 for PhantomLink test(ws)
      * link2 for PhantomLink test(ws)
> > [デモサイト](https://phapp.coco.0t0.jp/link2/)でも同じコンテンツを公開しています。

  * アプリケーションモデル
> > PhantomLinkは、publish/subscribe方式のメッセージ通信と各種スコープを持つstorageサービスから構成されます。
> > offline時にも、offline認証する事で、ブラウザスコープのstorageは、安全に利用することができます。
> > > 例)publish/subscribe APIでメッセージを送受信する
```
var link=ph.link(url);
var sub=link.subscribe('qname','subname');
/* 送信 */
sub.publish(msg);

/* 受信 */
sub.onMsg(function(msg){});
```
> > > 例)storage APIでブラウザに情報を保存、参照する
```
var link=ph.link(url);
var storage=link.storage(ph.SCOPE.SESSION_PRIVATE);
/* 保存 */
storage.setItem(key,value);

/* 参照 */
storage.getItem(key,function(value){...});
//storage.on(key,function(data){data.value...});/*値の変化を監視する場合*/
```


> 特徴
  * シンプルなAPI
  * Phantom Serverの多様な認証機能を利用
  * WebSocketsの利用可否にかかわらずリアルタイムな通信を実現
  * bookmarkletなど3ed pertyのサイトからも安全に利用可能
  * offline時でもブラウザにスコープを持つstorageを利用可能
  * ブラウザに保持するデータは暗号化されており、offline認証後そのユーザだけが参照可能となる
  * ClientAPI
> > 詳細は、[ClientAPI](https://phapp.coco.0t0.jp/api/coffee/)を参照ください。
    * エントリーポイントは、[ph.link](https://phapp.coco.0t0.jp/api/coffee/phLink.coffee.html#ph.link)
    * pub/sub通信インタフェースは、link.subscribeの復帰オブジェクト[Subscription](https://phapp.coco.0t0.jp/api/coffee/phLinkSubscription.coffee.html)です。
    * storageインタフェースは、link.srorageの復帰オブジェクト[Storage](https://phapp.coco.0t0.jp/api/coffee/phLinkSessionStorage.coffee.html)です。
    * 参考[storageTest](https://phapp.coco.0t0.jp/storageTest.html),[pubsubTest](https://phapp.coco.0t0.jp/pubsubTest.html)


  * ServerAPI
> > 基本的なpub/sub通信のためにサーバ側のアプリケーションは必要ありませ。しかし複雑な処理をするためには、Javaによりサーバアプリケーションを記述することができます。詳細は、[ServerAPI](https://phapp.coco.0t0.jp/api/java/)を参照してください。
    * clientからのpublish/subscribe通信を送受信するには、[Linkletインタフェース](https://phapp.coco.0t0.jp/api/java/naru/aweb/link/api/Linklet.html)を実装してください。
    * 作成したクラスは、以下の書式にそってmapping定義に追加してください。
> > > link:{@LinkName:"Managerの固有名",@MaxSubscribe:セションに保持できる最大subscribe数(16),@Storage:true/false,"qname":{"subname":"linklet継承クラス名".....}}