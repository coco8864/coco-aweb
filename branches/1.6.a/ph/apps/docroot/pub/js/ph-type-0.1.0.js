(function(){

var type={
 typedefs:{},
 createType:function(name,attrs,conv,clazz){
  ph.type.typedefs[name]={};
  ph.type.typedefs[name].attrs=attrs;
  if(clazz){
   ph.type.typedefs[name].Class=clazz;
  }else{
   ph.type.typedefs[name].Class=function(){};
  }
  ph.type.typedefs[name].Class.prototype['phTypeName']=name;
  if(attrs && attrs.length){
   for(var i=0;i<attrs.length;i++){
    ph.type.typedefs[name].Class.prototype[attrs[i]]=null;
   }
  }
  if(!conv){
   conv=new ph.type.Converter(name);
  }
  ph.type.typedefs[name].conv=conv;
 },
 newObj:function(name){
  if(ph.type.typedefs[name]){
   return new ph.type.typedefs[name].Class;
  }
  return null;
 },
 _getConverter:function(obj){
  if(!obj){
   return null;
  }
  var name=obj.phTypeName;
  if(!name){
   return null;
  }
  if(!ph.type.typedefs[name]){
   return null;
  }
  return ph.type.typedefs[name].conv;
 },
 _replacer:function(key,value){
  var conv=ph.type._getConverter(this[key]);
  if(conv){
   value={};
   value[conv.name]=conv.serialize(this[key]);
  }
  return value;
 },
 _reviver:function(key,value){
  var name,i=0;
  for(name in value){
   if(i>0){//not only one attribute
    return value;
   }
   i++;
  }
  if(!ph.type.typedefs[name]||!ph.type.typedefs[name].conv){
    return value;
  }
  var conv=ph.type.typedefs[name].conv;
  return conv.deserialize(value[name]);
 },
 toJsonString:function(obj){
  var text=ph.JSON.stringify(obj,this._replacer);
  return text;
 },
 toObj:function(jsonString){
  var obj=ph.JSON.parse(jsonString,this._reviver);
  return obj;
 }
};
type.Converter=function(name){this.name=name;};
type.Converter.prototype.serialize=function(obj){
 var sirializeObj={};
 for(var i in obj){
  if(i=='phTypeName'){
   continue;
  }
  sirializeObj[i]=obj[i];
 }
 return sirializeObj;
};
type.Converter.prototype.deserialize=function(obj){
 var typedObj=ph.type.newObj(this.name);
 for(var i in typedObj){
  if(i=='phTypeName'){
   continue;
  }
  typedObj[i]=obj[i];
 }
 return typedObj;
};
ph.type=type;
var dateConv=new type.Converter('$Date$');
dateConv.serialize=function(obj){return obj.getTime();};
dateConv.deserialize=function(t){return new Date(t);};
type.createType('$Date$',null,dateConv,Date);


;})();
