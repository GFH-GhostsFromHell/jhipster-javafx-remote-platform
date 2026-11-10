(function(){
 'use strict';
 if(!window.EWBTaskCatalog||!window.EWBToolState)return;
 var favorites=document.getElementById('dashboardFavorites'),recents=document.getElementById('dashboardRecent');
 if(!favorites||!recents)return;
 function renderList(node,ids){
  node.replaceChildren();
  ids.slice(0,4).forEach(function(id){
   var item=window.EWBTaskCatalog.find(function(t){return t.id===id;});if(!item)return;
   var a=document.createElement('a');a.href=item.path;a.textContent=item.name;
   a.addEventListener('click',function(){window.EWBToolState.visit(item.id);});
   node.append(a);
  });
  if(!node.children.length){var span=document.createElement('span');span.textContent='No tools yet — choose one from Find a Tool.';node.append(span);}
 }
 fetch('/api/profile',{credentials:'same-origin'}).then(function(r){if(!r.ok)throw Error('Profile unavailable');return r.json();}).then(function(p){
  window.EWBToolState.init(p.username);
  function update(){var s=window.EWBToolState.snapshot();renderList(favorites,s.favourites);renderList(recents,s.recent);}
  update();window.addEventListener('ewb-tool-state-change',update);
 }).catch(function(){renderList(favorites,[]);renderList(recents,[]);});
})();
