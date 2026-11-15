(()=>{
  "use strict";
  const sections = Array.from(document.querySelectorAll(".stage"));
  const openAll = document.getElementById("expand-all");
  const closeAll = document.getElementById("collapse-all");
  const count = document.getElementById("open-count");
  function toggle(section, expand, focus=false) {
    const trigger=section.querySelector(".stage-header");
    const detail=section.querySelector(".detail");
    section.classList.toggle("active",expand);
    trigger.setAttribute("aria-expanded",String(expand));
    detail.hidden=!expand;
    if(focus) trigger.focus();
    updateCount();
  }
  function updateCount(){
    count.textContent=`${sections.filter(s=>s.classList.contains("active")).length} из ${sections.length} раскрыто`;
  }
  for(const section of sections){
    section.querySelector(".stage-header").addEventListener("click",()=>{
      toggle(section,!section.classList.contains("active"));
    });
    section.querySelector(".collapse").addEventListener("click",()=>{
      toggle(section,false,true);
    });
  }
  openAll.addEventListener("click",()=>sections.forEach(s=>toggle(s,true)));
  closeAll.addEventListener("click",()=>sections.forEach(s=>toggle(s,false)));
  function revealHash(){
    const id=decodeURIComponent(location.hash.slice(1));
    if(!id)return;
    const target=sections.find(s=>s.id===id);
    if(target){toggle(target,true);requestAnimationFrame(()=>target.scrollIntoView({block:"start",behavior:"auto"}));}
  }
  window.addEventListener("hashchange",revealHash);
  revealHash(); updateCount();
})();
