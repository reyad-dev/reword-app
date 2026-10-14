'use strict';
let MP=null,VAULT=null,EDIT_SITE=null,EDIT_ACC=null,CUR_SITE=null,SYNC_ID=null,WORKER_URL=null;

/* ── CRYPTO (AES-256-GCM + PBKDF2×310k) ── */
const CV={
  b64e(a){let s='';for(let i=0;i<a.length;i++)s+=String.fromCharCode(a[i]);return btoa(s)},
  b64d(s){const b=atob(s),a=new Uint8Array(b.length);for(let i=0;i<b.length;i++)a[i]=b.charCodeAt(i);return a},
  async key(pwd,salt){
    const k=await crypto.subtle.importKey('raw',new TextEncoder().encode(pwd),'PBKDF2',false,['deriveKey']);
    return crypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:310000,hash:'SHA-256'},k,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
  },
  async enc(data,pwd){
    const s=crypto.getRandomValues(new Uint8Array(32)),iv=crypto.getRandomValues(new Uint8Array(12));
    const e=await crypto.subtle.encrypt({name:'AES-GCM',iv},await this.key(pwd,s),new TextEncoder().encode(JSON.stringify(data)));
    const o=new Uint8Array(44+e.byteLength);o.set(s);o.set(iv,32);o.set(new Uint8Array(e),44);
    return this.b64e(o);
  },
  async dec(b64,pwd){
    const b=this.b64d(b64),k=await this.key(pwd,b.slice(0,32));
    const d=await crypto.subtle.decrypt({name:'AES-GCM',iv:b.slice(32,44)},k,b.slice(44));
    return JSON.parse(new TextDecoder().decode(d));
  },
  async hashId(id){
    const buf=await crypto.subtle.digest('SHA-256',new TextEncoder().encode('reword:v1:'+id));
    return Array.from(new Uint8Array(buf)).map(b=>b.toString(16).padStart(2,'0')).join('');
  }
};

/* ── INDEXEDDB ── */
const DB={
  db:null,
  open(){return new Promise((r,j)=>{const q=indexedDB.open('reword',1);q.onupgradeneeded=e=>e.target.result.createObjectStore('kv');q.onsuccess=e=>{this.db=e.target.result;r()};q.onerror=j})},
  set(k,v){return new Promise((r,j)=>{const t=this.db.transaction('kv','readwrite');t.objectStore('kv').put(v,k);t.oncomplete=r;t.onerror=j})},
  get(k){return new Promise((r,j)=>{const q=this.db.transaction('kv','readonly').objectStore('kv').get(k);q.onsuccess=e=>r(e.target.result);q.onerror=j})},
  clear(){return new Promise((r,j)=>{const t=this.db.transaction('kv','readwrite');t.objectStore('kv').clear();t.oncomplete=r;t.onerror=j})}
};

/* ── SYNC ── */
function setSyncStatus(s){
  const el=document.getElementById('sync-dot');if(!el)return;
  el.className='sync-dot '+s;
  const labels={syncing:'جار المزامنة...',ok:'تمت المزامنة ✓',error:'فشلت المزامنة ✗',idle:''};
  el.title=labels[s]||'';
}

async function syncPush(enc){
  if(!SYNC_ID||!WORKER_URL)return;
  setSyncStatus('syncing');
  try{
    const id=await CV.hashId(SYNC_ID);
    const ts=Date.now();
    const res=await fetch(`${WORKER_URL}/vault`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id,data:enc,ts})});
    await DB.set('vault_ts',ts);
    setSyncStatus(res.ok||res.status===409?'ok':'error');
  }catch{setSyncStatus('error');}
}

async function syncPull(){
  if(!SYNC_ID||!WORKER_URL)return null;
  try{
    const id=await CV.hashId(SYNC_ID);
    const res=await fetch(`${WORKER_URL}/vault?id=${id}`);
    if(!res.ok)return null;
    return await res.json(); // {data, ts}
  }catch{return null;}
}

function loadSyncSettings(){
  SYNC_ID=localStorage.getItem('rw_sid')||null;
  const rawWurl=localStorage.getItem('rw_wurl')||null;
  WORKER_URL=rawWurl ? cleanWorkerUrl(rawWurl) : null;
  const dot=document.getElementById('sync-dot');
  if(dot){dot.classList.toggle('hidden',!SYNC_ID||!WORKER_URL);}
  const si=document.getElementById('settings-sync-info');
  if(si&&SYNC_ID)si.textContent='Sync ID: '+SYNC_ID;
}

function cleanWorkerUrl(url) {
  if (!url) return '';
  let u = url.trim();
  // إزالة جميع أنواع علامات الاقتباس (العادية، الذكية، المائلة، والشرطة الخلفية) من أي مكان في النص
  u = u.replace(/['"`‘’“”′‘]+/g, '');
  u = u.replace(/\/+$/, '').replace(/\/vault$/, '');
  u = u.trim().replace(/^\/+/, ''); // إزالة الشرطات المائلة من البداية
  if (u && !/^https?:\/\//i.test(u)) {
    u = 'https://' + u;
  }
  return u;
}

function saveSyncSettings(sid,wurl){
  const cleanWurl = cleanWorkerUrl(wurl);
  if(sid&&cleanWurl){SYNC_ID=sid;WORKER_URL=cleanWurl;localStorage.setItem('rw_sid',sid);localStorage.setItem('rw_wurl',cleanWurl);}
  else{SYNC_ID=null;WORKER_URL=null;localStorage.removeItem('rw_sid');localStorage.removeItem('rw_wurl');}
  loadSyncSettings();
}

/* ── SAVE VAULT ── */
async function saveVault(){
  const enc=await CV.enc(VAULT,MP);
  await DB.set('vault',enc);
  const s=VAULT.sites.length,a=VAULT.sites.reduce((x,w)=>x+w.accounts.length,0);
  document.getElementById('hdr-stats').textContent=`${s} موقع • ${a} حساب`;
  syncPush(enc); // non-blocking background sync
}

/* ── INIT ── */
async function init(){
  if('serviceWorker'in navigator)navigator.serviceWorker.register('./sw.js').catch(()=>{});
  await DB.open();
  const enc=await DB.get('vault'),meta=await DB.get('meta');
  loadSyncSettings();
  setTimeout(()=>{
    if(enc){
      // ✅ خزنة محلية موجودة → شاشة الدخول العادية
      if(meta?.hint)document.getElementById('login-hint').textContent='تلميح: '+meta.hint;
      showScreen('screen-login');
    } else if(SYNC_ID && WORKER_URL){
      // ☁️ لا خزنة محلية لكن إعدادات مزامنة موجودة → شاشة الربط
      document.getElementById('cn-sid').value = SYNC_ID;
      document.getElementById('cn-wurl').value = WORKER_URL;
      showScreen('screen-connect');
    } else {
      // 🆕 جهاز جديد بدون أي بيانات → خيار: ربط أو إنشاء
      showScreen('screen-connect');
    }
  },1400);
}

/* ── CONNECT EXISTING VAULT (new device) ── */
async function doConnect(){
  const sid  = document.getElementById('cn-sid').value.trim();
  const wurl = cleanWorkerUrl(document.getElementById('cn-wurl').value);
  const pass = document.getElementById('cn-pass').value;
  const err  = document.getElementById('cn-err');
  err.classList.remove('show');

  if(!sid)  { err.textContent='أدخل Sync ID';  err.classList.add('show'); return; }
  if(!wurl) { err.textContent='أدخل Worker URL'; err.classList.add('show'); return; }
  if(!pass) { err.textContent='أدخل كلمة المرور الرئيسية'; err.classList.add('show'); return; }

  const btn = document.getElementById('cn-btn');
  btn.innerHTML='<span class="spinner"></span> جاري جلب الخزنة...';
  btn.disabled=true;

  try {
    // حفظ إعدادات المزامنة مؤقتاً
    SYNC_ID = sid; WORKER_URL = wurl;

    // جلب الخزنة المشفرة من السحابة
    const id = await CV.hashId(sid);
    const targetUrl = `${wurl}/vault?id=${id}`;
    const res = await fetch(targetUrl);
    const text = await res.text();

    if(!res.ok) {
      let msg = '';
      try {
        if (text.trim().startsWith('<!DOCTYPE') || text.trim().startsWith('<html')) {
          msg = `Server returned HTML (crash/wrong URL): ${text.slice(0, 120)}...`;
        } else {
          const errJson = JSON.parse(text);
          msg = errJson.error || errJson.message || '';
        }
      } catch {
        msg = text.slice(0, 100);
      }
      if(res.status===404){
        err.textContent='لم يتم العثور على خزنة بهذا الـ Sync ID — تأكد من صحة البيانات';
      } else {
        err.textContent=msg ? `خطأ من السيرفر: ${msg}` : `فشل الاتصال بالسيرفر (${res.status})`;
      }
      err.classList.add('show');
      return;
    }
    let json;
    try {
      json = JSON.parse(text);
    } catch(e) {
      err.textContent = `خطأ في قراءة البيانات من [${targetUrl}]: ${text.slice(0, 80)}...`;
      err.classList.add('show');
      return;
    }
    if(!json.data){ err.textContent='الخزنة فارغة أو البيانات تالفة'; err.classList.add('show'); return; }

    // فك التشفير للتحقق من كلمة المرور
    const vault = await CV.dec(json.data, pass);

    // ✅ نجح — احفظ محلياً وافتح الخزنة
    MP = pass; VAULT = vault;
    await DB.set('vault', json.data);
    await DB.set('vault_ts', json.ts || Date.now());
    if(vault.meta?.hint) await DB.set('meta', {hint: vault.meta.hint, v:1});

    // حفظ إعدادات المزامنة نهائياً
    saveSyncSettings(sid, wurl);

    showScreen('screen-main');
    renderList();
    setSyncStatus('ok');
    toast('✅ تم ربط الخزنة بنجاح! مرحباً بك', 'success');

  } catch(e) {
    if(e.name==='OperationError' || e.message?.includes('decrypt')){
      err.textContent='كلمة المرور خاطئة';
    } else {
      err.textContent='خطأ: '+e.message;
    }
    err.classList.add('show');
  } finally {
    btn.innerHTML='<svg viewBox="0 0 24 24"><path d="M17 12h-5v5h5v-5zM16 1v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2h-1V1h-2zm3 18H5V8h14v11z"/></svg>جلب الخزنة من السحابة';
    btn.disabled=false;
  }
}


/* ── SETUP ── */
async function doSetup(){
  const p=document.getElementById('su-pass').value,c=document.getElementById('su-confirm').value,h=document.getElementById('su-hint').value.trim();
  const sid=document.getElementById('su-sid').value.trim(),wurl=cleanWorkerUrl(document.getElementById('su-wurl').value);
  const err=document.getElementById('su-err');err.classList.remove('show');
  if(p.length<8){err.textContent='8 أحرف على الأقل';err.classList.add('show');return;}
  if(p!==c){err.textContent='كلمات المرور غير متطابقة';err.classList.add('show');return;}
  const btn=document.getElementById('su-btn');btn.innerHTML='<span class="spinner"></span>';btn.disabled=true;
  try{
    saveSyncSettings(sid,wurl);
    MP=p;VAULT={meta:{hint:h,v:1},sites:[]};
    await DB.set('meta',{hint:h,v:1});await saveVault();
    showScreen('screen-main');renderList();toast('تم إنشاء الخزينة 🎉','success');
  }catch(e){err.textContent='خطأ: '+e.message;err.classList.add('show');}
  finally{btn.innerHTML='<svg viewBox="0 0 24 24"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>إنشاء الخزينة';btn.disabled=false;}
}

/* ── LOGIN ── */
async function doLogin(){
  const p=document.getElementById('ln-pass').value,err=document.getElementById('ln-err');
  err.classList.remove('show');if(!p){err.textContent='أدخل كلمة المرور';err.classList.add('show');return;}
  const btn=document.getElementById('ln-btn');btn.innerHTML='<span class="spinner"></span> جار الفتح...';btn.disabled=true;
  try{
    let useEnc=await DB.get('vault');
    // محاولة جلب النسخة الأحدث من الكلاود
    if(SYNC_ID&&WORKER_URL){
      btn.innerHTML='<span class="spinner"></span> جار المزامنة...';
      const cloud=await syncPull();
      if(cloud?.data){
        try{
          await CV.dec(cloud.data,p); // للتحقق فقط
          const localTs=await DB.get('vault_ts')||0;
          if(cloud.ts>localTs){useEnc=cloud.data;await DB.set('vault',cloud.data);await DB.set('vault_ts',cloud.ts);}
        }catch{} // السحابة مش صحيحة - استخدم المحلي
      }
    }
    if(!useEnc)throw new Error('no_vault');
    VAULT=await CV.dec(useEnc,p);MP=p;
    showScreen('screen-main');renderList();
    setSyncStatus(SYNC_ID?'ok':'idle');
    toast('تم فتح الخزينة ✓','success');
  }catch(e){
    err.textContent=e.message==='no_vault'?'لا توجد خزينة محلية، تأكد من الاتصال بالإنترنت':'كلمة مرور خاطئة';
    err.classList.add('show');document.getElementById('ln-pass').value='';
  }
  finally{btn.innerHTML='<svg viewBox="0 0 24 24"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"/></svg>فتح الخزينة';btn.disabled=false;}
}

function doLock(){MP=null;VAULT=null;CUR_SITE=null;document.getElementById('ln-pass').value='';document.getElementById('ln-err').classList.remove('show');setSyncStatus('idle');showList();showScreen('screen-login');}

function showReset(){
  closeModal('modal-settings');
  if(confirm('⚠️ سيتم حذف كل البيانات محلياً. هل أنت متأكد؟'))
    DB.clear().then(()=>{MP=null;VAULT=null;showScreen('screen-setup');toast('تم إعادة الضبط','info');});
}

/* ── EXPORT / IMPORT ── */
async function doExport(){
  const enc=await DB.get('vault');
  const blob=new Blob([JSON.stringify({format:'reword-vault',v:1,data:enc,exported:new Date().toISOString()})],{type:'application/json'});
  const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=`reword-${new Date().toISOString().slice(0,10)}.reword`;a.click();
  toast('تم التصدير ✓','success');
}

async function doImport(input){
  const f=input.files[0];if(!f)return;
  try{
    const t=await f.text(),parsed=JSON.parse(t),enc=parsed.data||parsed;
    VAULT=await CV.dec(enc,MP);await DB.set('vault',enc);await DB.set('vault_ts',Date.now());
    renderList();toast('تم الاستيراد ✓','success');closeModal('modal-settings');
    syncPush(enc);
  }catch{toast('فشل الاستيراد - تأكد من الملف وكلمة المرور','error');}
  input.value='';
}

/* ── CHANGE PASSWORD ── */
function showChangePwd(){const el=document.getElementById('change-pwd-section');el.style.display=el.style.display==='none'?'flex':'none';el.style.flexDirection='column';}

async function doChangePwd(){
  const np=document.getElementById('cp-new').value,nc=document.getElementById('cp-confirm').value;
  if(np.length<8){toast('8 أحرف على الأقل','error');return;}
  if(np!==nc){toast('كلمات المرور غير متطابقة','error');return;}
  try{MP=np;await saveVault();document.getElementById('change-pwd-section').style.display='none';document.getElementById('cp-new').value='';document.getElementById('cp-confirm').value='';toast('تم تغيير كلمة المرور ✓','success');}
  catch{toast('حدث خطأ','error');}
}

/* ── UPDATE SYNC SETTINGS ── */
function showSyncEdit(){const el=document.getElementById('sync-edit-section');el.style.display=el.style.display==='none'?'flex':'none';el.style.flexDirection='column';if(SYNC_ID)document.getElementById('set-sid').value=SYNC_ID;if(WORKER_URL)document.getElementById('set-wurl').value=WORKER_URL;}

async function doUpdateSync(){
  const sid=document.getElementById('set-sid').value.trim(),wurl=cleanWorkerUrl(document.getElementById('set-wurl').value);
  saveSyncSettings(sid,wurl);document.getElementById('sync-edit-section').style.display='none';
  if(sid&&wurl){
    toast('تم تحديث إعدادات المزامنة ✓','success');
    const enc = await DB.get('vault');
    if(enc) await syncPush(enc);
  }
  else toast('تم إيقاف المزامنة','info');
}

/* ── IMPORT CSV (Chrome / Edge / Firefox / Safari) ── */
async function doImportCSV(input) {
  const file = input.files[0];
  if (!file) return;
  try {
    const text = await file.text();
    const lines = text.split(/\r?\n/).filter(l => l.trim());
    if (lines.length < 2) { toast('الملف فارغ أو غير صحيح', 'error'); return; }

    // كشف الأعمدة تلقائياً (Chrome/Edge: name,url,username,password | Firefox: url,username,password,httpRealm,formActionOrigin,guid,timeCreated,timeLastUsed,timePasswordChanged)
    const header = lines[0].toLowerCase().split(',').map(h => h.trim().replace(/"/g,''));
    const idx = {
      name:     header.indexOf('name')    >= 0 ? header.indexOf('name')     : -1,
      url:      header.indexOf('url')     >= 0 ? header.indexOf('url')      : header.indexOf('origin'),
      user:     header.indexOf('username')>= 0 ? header.indexOf('username') : header.indexOf('login'),
      pass:     header.indexOf('password')>= 0 ? header.indexOf('password') : -1,
    };

    if (idx.url < 0 || idx.user < 0 || idx.pass < 0) {
      toast('تنسيق CSV غير مدعوم', 'error'); return;
    }

    let imported = 0, skipped = 0, sites = 0;
    const parseCSVLine = (line) => {
      const result = []; let cur = '', inQ = false;
      for (let i = 0; i < line.length; i++) {
        const c = line[i];
        if (c === '"') { inQ = !inQ; continue; }
        if (c === ',' && !inQ) { result.push(cur); cur = ''; continue; }
        cur += c;
      }
      result.push(cur);
      return result;
    };

    for (let i = 1; i < lines.length; i++) {
      const cols = parseCSVLine(lines[i]);
      const rawUrl  = (cols[idx.url]  || '').trim();
      const user    = (cols[idx.user] || '').trim();
      const pass    = (cols[idx.pass] || '').trim();
      const rawName = idx.name >= 0 ? (cols[idx.name] || '').trim() : '';
      if (!rawUrl || !user || !pass) { skipped++; continue; }

      // استخراج اسم الموقع من الـ URL
      let siteName = rawName;
      if (!siteName) {
        try { siteName = new URL(rawUrl.startsWith('http') ? rawUrl : 'https://'+rawUrl).hostname.replace('www.',''); }
        catch { siteName = rawUrl; }
      }

      // البحث عن موقع موجود أو إنشاء جديد
      let site = VAULT.sites.find(s => s.name.toLowerCase() === siteName.toLowerCase() || (s.url && s.url.includes(siteName)));
      if (!site) {
        site = { id: Date.now().toString() + Math.random().toString(36).slice(2), name: siteName, url: rawUrl, accounts: [] };
        VAULT.sites.push(site);
        sites++;
      }

      // تحقق من عدم التكرار
      const dup = site.accounts.find(a => a.user === user);
      if (dup) { skipped++; continue; }

      site.accounts.push({ id: Date.now().toString() + Math.random().toString(36).slice(2), user, pass, notes: '' });
      imported++;
    }

    await saveVault();
    renderList();
    closeModal('modal-settings');
    toast(`✅ تم استيراد ${imported} حساب في ${sites} موقع جديد — تخطي: ${skipped}`, 'success');
  } catch(e) {
    toast('فشل الاستيراد: ' + e.message, 'error');
  }
  input.value = '';
}

/* ── EXPORT XLSX (بدون مكتبات خارجية — XML مباشر) ── */
function doExportXLSX() {
  if (!VAULT || !VAULT.sites || VAULT.sites.length === 0) {
    toast('لا توجد بيانات للتصدير', 'error'); return;
  }

  // بناء ملف XLSX يدوياً (OpenXML format)
  const esc = s => String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&apos;');

  // أنماط الألوان
  const styles = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Cairo"/></font>
    <font><b/><sz val="13"/><color rgb="FFFFFFFF"/><name val="Cairo"/></font>
    <font><b/><sz val="11"/><color rgb="FF7C3AED"/><name val="Cairo"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF7C3AED"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF3F0FF"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border><left style="thin"><color rgb="FFE0D9FF"/></left><right style="thin"><color rgb="FFE0D9FF"/></right><top style="thin"><color rgb="FFE0D9FF"/></top><bottom style="thin"><color rgb="FFE0D9FF"/></bottom></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"><alignment horizontal="right" vertical="center" wrapText="1"/></xf>
  </cellXfs>
</styleSheet>`;

  // بناء الصفوف
  let rows = '';
  let rowNum = 1;

  // صف العنوان الرئيسي
  rows += `<row r="${rowNum}" ht="24" customHeight="1"><c r="A${rowNum}" s="1" t="s"><v>0</v></c><c r="B${rowNum}" s="1" t="s"><v>1</v></c><c r="C${rowNum}" s="1" t="s"><v>2</v></c><c r="D${rowNum}" s="1" t="s"><v>3</v></c></row>`;
  rowNum++;

  const sharedStrings = ['الموقع', 'الرابط', 'اسم المستخدم / البريد', 'كلمة المرور'];
  const ssMap = {};
  sharedStrings.forEach((s,i) => ssMap[s] = i);
  let ssIdx = sharedStrings.length;

  const getSSIdx = (val) => {
    if (ssMap[val] !== undefined) return ssMap[val];
    ssMap[val] = ssIdx++;
    sharedStrings.push(val);
    return ssMap[val];
  };

  for (const site of VAULT.sites) {
    // صف اسم الموقع
    rows += `<row r="${rowNum}" ht="20" customHeight="1"><c r="A${rowNum}" s="2" t="s"><v>${getSSIdx(site.name)}</v></c><c r="B${rowNum}" s="2" t="s"><v>${getSSIdx(site.url||'')}</v></c><c r="C${rowNum}" s="2"/><c r="D${rowNum}" s="2"/></row>`;
    rowNum++;

    for (const acc of site.accounts) {
      rows += `<row r="${rowNum}"><c r="A${rowNum}" s="3" t="s"><v>${getSSIdx(site.name)}</v></c><c r="B${rowNum}" s="3" t="s"><v>${getSSIdx(site.url||'')}</v></c><c r="C${rowNum}" s="3" t="s"><v>${getSSIdx(acc.user)}</v></c><c r="D${rowNum}" s="3" t="s"><v>${getSSIdx(acc.pass)}</v></c></row>`;
      rowNum++;
    }
  }

  const ssXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.length}" uniqueCount="${sharedStrings.length}">
${sharedStrings.map(s=>`<si><t xml:space="preserve">${esc(s)}</t></si>`).join('')}
</sst>`;

  const sheet = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetView rightToLeft="1"><selection activeCell="A1"/></sheetView>
  <cols><col min="1" max="1" width="20" customWidth="1"/><col min="2" max="2" width="30" customWidth="1"/><col min="3" max="3" width="30" customWidth="1"/><col min="4" max="4" width="25" customWidth="1"/></cols>
  <sheetData>${rows}</sheetData>
</worksheet>`;

  const wb = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="كلمات المرور" sheetId="1" r:id="rId1"/></sheets>
</workbook>`;

  const wbRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;

  const rels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;

  const ct = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;

  // بناء ZIP يدوياً (XLSX = ZIP)
  const files = [
    ['[Content_Types].xml', ct],
    ['_rels/.rels', rels],
    ['xl/workbook.xml', wb],
    ['xl/_rels/workbook.xml.rels', wbRels],
    ['xl/worksheets/sheet1.xml', sheet],
    ['xl/sharedStrings.xml', ssXml],
    ['xl/styles.xml', styles],
  ];

  // استخدام JSZip من CDN أو بناء ZIP بسيط
  // نستخدم طريقة بديلة: CSV بدلاً لو مفيش JSZip
  if (typeof JSZip !== 'undefined') {
    const zip = new JSZip();
    files.forEach(([path, content]) => zip.file(path, content));
    zip.generateAsync({type:'blob'}).then(blob => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `ReWord-${new Date().toISOString().slice(0,10)}.xlsx`;
      a.click();
      toast('✅ تم تصدير ملف إكسل بنجاح!', 'success');
    });
  } else {
    // fallback: تصدير CSV مع تنسيق Unicode
    let csv = '\uFEFF'; // BOM للعربية
    csv += 'الموقع,الرابط,اسم المستخدم,كلمة المرور\n';
    for (const site of VAULT.sites) {
      for (const acc of site.accounts) {
        const q = s => `"${String(s||'').replace(/"/g,'""')}"`;
        csv += `${q(site.name)},${q(site.url)},${q(acc.user)},${q(acc.pass)}\n`;
      }
    }
    const blob = new Blob([csv], {type:'text/csv;charset=utf-8'});
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `ReWord-${new Date().toISOString().slice(0,10)}.csv`;
    a.click();
    toast('✅ تم التصدير كـ CSV (جرب إضافة JSZip للـ XLSX)', 'success');
  }
}

window.addEventListener('load',init);

