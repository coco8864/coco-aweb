#-------------------Envelope-------------------
class Envelope
  BLOB_VALUE_NAME_PREFIX:'__plBlobValue'
  DATE_VALUE_NAME_PREFIX:'__plDateValue'
  mainObj:null
  constructor:->
    @blobs=[]
    @blobMetas=[]
    @dates=[]
    @asyncBlobCount=0
  meta:->
    if @datas==undefined || @datas.length==0
      if @blobMetas==undefined ||@blobMetas.length==0
        return null
      else
        return {blobs:@blobMetas}
    else
      if @blobMetas==undefined || @blobMetas.length==0
        return {dates:@dates}
    return {dates:@dates,blobs:@blobMetas}
  serialize:(obj)->
    if ph.jQuery.isArray(obj)
      result=[]
      size=obj.length
      if size<=0
        return result
      for i in [0..(size-1)]
        result[i]=@serialize(obj[i])
      return result
    else if ph.useBlob && obj instanceof Uint8Array
      idx=@blobs.length
      key = @BLOB_VALUE_NAME_PREFIX+idx
      @blobs[idx]=obj
      size=obj.length*obj.BYTES_PER_ELEMENT
      @blobMetas[idx]={size:size,jsType:'ArrayBufferView'}
      return key
    else if ph.useBlob && obj instanceof ArrayBuffer
      idx=@blobs.length
      key = @BLOB_VALUE_NAME_PREFIX+idx
      @blobs[idx]=new Uint8Array(obj)
      @blobMetas[idx]={size:obj.byteLength,jsType:'ArrayBuffer'}
      return key
    else if ph.useBlob && obj instanceof Blob
      idx=@blobs.length
      key = @BLOB_VALUE_NAME_PREFIX+idx
#      fileReader=new FileReader()
#      @asyncBlobCount++
#      fileReader.onload=(e)=>
#        @blobs[idx]=e.target.result
#        @asyncBlobCount--
#        if @asyncBlobCount==0 && @blobDfd
#          @blobDfd.resolve()
#      fileReader.readAsArrayBuffer(obj)
      @blobs[idx]=obj
      meta={size:obj.size,type:obj.type,jsType:'Blob'}
      if obj.name
        meta.name=obj.name
      if obj.lastModifiedDate
        meta.lastModifiedDate=obj.lastModifiedDate.getTime()
      @blobMetas[idx]=meta
      return key
    else if obj instanceof Date
      idx=@dates.length
      key = @DATE_VALUE_NAME_PREFIX+idx
      @dates[idx]=obj.getTime()
      return key
    else if ph.jQuery.isPlainObject(obj)
      result={}
      for key,value of obj
        result[key]=@serialize(obj[key])
      return result
    else if window.FileList && obj instanceof FileList
      result=[]
      size=obj.length
      if size<=0
        return result
      for i in [0..(size-1)]
        result[i]=@serialize(obj[i])
      return result
    return obj
  deserialize:(obj)->
    if ph.jQuery.isArray(obj)
      result=[]
      size=obj.length
      if size<=0
        return result
      for i in [0..(size-1)]
        result[i]=@deserialize(obj[i])
      return result
    else if ph.jQuery.isPlainObject(obj)
      result={}
      for key,value of obj
        result[key]=@deserialize(obj[key])
      return result
    else if typeof obj =='string'
      if obj.lastIndexOf(@BLOB_VALUE_NAME_PREFIX,0)==0
        idx=parseInt(obj.substring(@BLOB_VALUE_NAME_PREFIX.length),10)
        return @blobs[idx]
      else if obj.lastIndexOf(@DATE_VALUE_NAME_PREFIX,0)==0
        idx=parseInt(obj.substring(@DATE_VALUE_NAME_PREFIX.length),10)
        return new Date(@dates[idx])
    obj
  #bin protocol data読み込み完了時
  onDoneBinPtc:(onPacked)=>
    headerText=ph.JSON.stringify(@mainObj)
    headerTextBuf=ph.stringToArrayBuffer(headerText)
    #bb=ph.createBlobBuilder()
    headerLenBuf=new ArrayBuffer(4)
    #header長をbigEndianにして代入
    headerLenArray=new DataView(headerLenBuf)
    wkLen=headerTextBuf.byteLength
    headerLenArray.setUint32(0,wkLen,false)
    blobData=[]
#    blobData.push(headerLenArray)
    blobData.push(new Uint8Array(headerLenBuf))
    blobData.push(headerTextBuf)
    for blob in @blobs
      blobData.push(blob)
    onPacked(ph.createBlob(blobData))
  pack:(obj,onPacked)->
    @mainObj=@serialize(obj)
    meta=@meta()
    if meta
      @mainObj.meta=meta
    if !onPacked
      return @mainObj
    if @blobs.length==0
      onPacked(ph.JSON.stringify(@mainObj))
    else if @asyncBlobCount==0
      @onDoneBinPtc(onPacked)
    else
      @blobDfd=ph.jQuery.Deferred()
      @blobDfd.done(=>@onDoneBinPtc(onPacked))
  unpack:(data,cb)->
    if !ph.useBlob || !(data instanceof Blob)
      if typeof data == 'string'
        data=ph.JSON.parse(data)
      @dates=data.meta?.dates ? []
      obj=@deserialize(data)
      cb(obj)
      return
    blob=data
    fr=new FileReader()
    mode='headerLen'
    fr.onload=(e)=>
      if mode=='headerLen'
        headerLenView=new DataView(e.target.result)
        headerLength=headerLenView.getUint32(0,false)
        ph.log('headerLength:'+headerLength)
        headerBlob=ph.blobSlice(blob,offset,offset+headerLength)
        offset+=headerLength
        mode='header'
        fr.readAsText(headerBlob)
      else if mode=='header'
        ph.log('header:'+e.target.result)
        header=ph.JSON.parse(e.target.result)
        meta=header.meta
        @dates=meta?.dates ? []
        for blobMeta in meta.blobs
          size=blobMeta.size
          blob=ph.blobSlice(blob,offset,offset+size,blobMeta.type)
          offset+=size
          blob.type=blobMeta.type
          if blobMeta.name
            blob.name=blobMeta.name
          if blobMeta.lastModifiedDate
            blob.lastModifiedDate=blobMeta.lastModifiedDate
          @blobs.push(blob)
        obj=@deserialize(header)
        cb(obj)
    offset=4
    headerLengthBlob=ph.blobSlice(blob,0,offset)
    fr.readAsArrayBuffer(headerLengthBlob)
