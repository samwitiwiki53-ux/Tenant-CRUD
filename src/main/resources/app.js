const columns = {
 tenants:{name:"Tenant",unitNumber:"Unit",propertyName:"Property",monthlyRent:"Monthly rent (UGX)",status:"Status"},
 properties:{propertyName:"Property",area:"Area",unitNumber:"Unit",monthlyRent:"Monthly rent (UGX)"},
 payments:{tenantName:"Tenant",unitNumber:"Unit",amountPaid:"Paid (UGX)",method:"Method",period:"Period",receiptNumber:"Receipt"},
 receipts:{receiptNumber:"Receipt",tenantName:"Tenant",amountPaid:"Paid (UGX)",balance:"Balance (UGX)",status:"Status"}
};
const $=s=>document.querySelector(s), form=$("form");
let section="tenants", records=[], editing=null, revision=0;
function button(label,fn){const b=document.createElement("button");b.textContent=label;b.type="button";b.onclick=()=>Promise.resolve(fn()).catch(err=>$("#msg").textContent=err.message);return b;}
function reset(){form.reset();editing=null;$("#formTitle").textContent="Add a demo tenant";}
function cell(row,tag,value){const c=document.createElement(tag);c.textContent=value;row.append(c);return c;}
function draw(){
 const head=document.createElement("tr");Object.values(columns[section]).forEach(l=>cell(head,"th",l));if(section==="tenants")cell(head,"th","Actions");$("thead").replaceChildren(head);
 const body=$("tbody");body.replaceChildren();
 for(const r of records){
  const fields=Object.keys(columns[section]);if(!fields.some(k=>String(r[k]).toLowerCase().includes($("#search").value.toLowerCase())))continue;
  const tr=document.createElement("tr");fields.forEach(k=>cell(tr,"td",typeof r[k]==="number"?r[k].toLocaleString("en-UG"):r[k]));
  if(section==="tenants"){const a=cell(tr,"td",r.editable?"":"Example");if(r.editable)a.append(button("Edit",()=>{editing=r._id;fields.forEach(k=>form.elements[k].value=r[k]);$("#formTitle").textContent="Edit demo tenant";form.elements.name.focus();}),button("Delete",async()=>{if(confirm("Delete this demo tenant?"))await save({action:"delete",id:r._id});}));}
  body.append(tr);
 }
}
async function load(){
 const v=++revision;records=[];draw();
 const res=await fetch("/api/"+section);if(!res.ok)throw Error(await res.text());
 const list=await res.json();if(v!==revision)return;records=list;draw();
}
async function save(data){
 const res=await fetch("/api/tenants",{method:"POST",headers:{"Content-Type":"application/json","X-Tenant-Action":"1"},body:JSON.stringify(data)});
 if(!res.ok)throw Error(await res.text());reset();await load();$("#msg").textContent="Changes saved.";
}
form.onsubmit=async e=>{e.preventDefault();const b=form.querySelector(".primary");b.disabled=true;try{await save({...Object.fromEntries(new FormData(form)),action:editing?"edit":"add",id:editing});}catch(err){$("#msg").textContent=err.message;}finally{b.disabled=false;}};
$("#clear").onclick=reset;$("#search").oninput=draw;
for(const key of Object.keys(columns)){const label=key[0].toUpperCase()+key.slice(1);const b=button(label,async()=>{section=key;reset();$("#msg").textContent="";$("#title").textContent=label;$("#search").value="";$("#entry").hidden=key!=="tenants";for(const n of $("nav").children)n.setAttribute("aria-current",String(n===b));await load();});$("nav").append(b);}
$("nav").firstChild.click();
