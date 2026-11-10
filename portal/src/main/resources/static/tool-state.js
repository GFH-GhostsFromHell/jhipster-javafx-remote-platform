(function(g){
 'use strict';
 var prefix='ewb.toolPrefs.v1.';
 var known=new Set((g.EWBTaskCatalog||[]).map(function(x){return x.id;}));
 var username=null, records={favourites:[],recent:[]};
 function clean(value){return Array.isArray(value)?[...new Set(value.filter(function(id){return typeof id==='string'&&known.has(id);}))].slice(0,24):[];}
 function key(){return username?prefix+encodeURIComponent(username):null;}
 function load(){
  records={favourites:[],recent:[]};
  if(!key())return snapshot();
  try{var old=JSON.parse(localStorage.getItem(key())||'{}');records={favourites:clean(old.favourites),recent:clean(old.recent)};}catch(_){}
  return snapshot();
 }
 function snapshot(){return{favourites:records.favourites.slice(),recent:records.recent.slice(),ready:!!username};}
 function notify(){g.dispatchEvent(new Event('ewb-tool-state-change'));}
 function persist(){if(!key())return false;try{localStorage.setItem(key(),JSON.stringify(records));notify();return true;}catch(_){return false;}}
 g.EWBToolState=Object.freeze({
  init:function(user){username=(typeof user==='string'&&user.trim())?user.trim():null;return load();},
  snapshot:snapshot,
  favourite:function(id){if(!username||!known.has(id))return false;records.favourites=records.favourites.includes(id)?records.favourites.filter(function(v){return v!==id;}):[id].concat(records.favourites).slice(0,24);return persist();},
  visit:function(id){if(!username||!known.has(id))return false;records.recent=[id].concat(records.recent.filter(function(v){return v!==id;})).slice(0,12);return persist();},
  clearRecent:function(){if(!username)return false;records.recent=[];return persist();}
 });
})(window);
