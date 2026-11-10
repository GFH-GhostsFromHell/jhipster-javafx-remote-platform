'use strict';
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright-core');

const root = path.resolve('portal/src/main/resources/static');
const screenshots = path.resolve('qa-screenshots');
fs.mkdirSync(screenshots, { recursive: true });
let activeUser = 'qa-account-a';
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.json': 'application/json', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, 'http://127.0.0.1').pathname;
  if (pathname === '/api/profile') {
    res.setHeader('Content-Type','application/json');
    res.statusCode = activeUser ? 200 : 401;
    res.end(JSON.stringify(activeUser ? { username: activeUser, displayName: activeUser, subscriptionTier:'TIER_1' } : { error:'Unauthorized' }));
    return;
  }
  if (pathname.startsWith('/api/')) {
    res.statusCode=404;res.end('{}');return;
  }
  const candidate = path.resolve(root, '.' + decodeURIComponent(pathname === '/' ? '/start.html' : pathname));
  if (!candidate.startsWith(root + path.sep) || !fs.existsSync(candidate) || !fs.statSync(candidate).isFile()) {
    res.statusCode=404;res.end('Not found');return;
  }
  res.setHeader('Content-Type',mime[path.extname(candidate)]||'application/octet-stream');
  res.end(fs.readFileSync(candidate));
});

(async()=>{
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  const executablePath=process.env.CHROME_BIN||'/usr/bin/google-chrome';
  const browser=await chromium.launch({ executablePath, headless:true, args:['--no-sandbox','--disable-dev-shm-usage'] });
  const context=await browser.newContext({viewport:{width:1280,height:900},acceptDownloads:true});
  const page=await context.newPage();
  const issues=[];
  page.on('pageerror',error=>{issues.push(error.message);});
  async function goto(url){await page.goto(base+url,{waitUntil:'load'});await page.waitForTimeout(350);}
  async function checkNoOverflow(label){
    const dims=await page.evaluate(()=>({
      clientWidth:document.documentElement.clientWidth,
      scrollWidth:document.documentElement.scrollWidth,
      bodyWidth:document.body.scrollWidth
    }));
    assert.ok(dims.scrollWidth<=dims.clientWidth+2,label+' horizontal overflow: '+JSON.stringify(dims));
  }
  async function visible(selector){assert.ok(await page.locator(selector).isVisible(),'Expected visible: '+selector);}
  async function take(name){await page.screenshot({path:path.join(screenshots,name),fullPage:true,animations:'disabled'});}
  try{
    await goto('/start.html');
    await page.waitForFunction(()=>document.getElementById('accountStatus').textContent.includes('qa-account-a'));
    assert.equal(await page.locator('.task').count(),24,'Web catalog count');
    await visible('#query');
    await page.locator('#query').fill('hospital');
    await visible('.task:has(h3:text-is("Service Load Calculations"))');
    assert.equal(await page.locator('.task').count(),1,'Plain-language search finds service calculator');
    await page.locator('#query').fill('');
    await page.locator('button[data-category="installation"]').click();
    assert.ok((await page.locator('.task').count())>=3,'Installation filter');
    await page.locator('button[data-category="all"]').click();
    const conduit=page.locator('.task:has(h3:text-is("Conduit Bend Marks + 3D"))');
    await conduit.getByRole('button',{name:/Add to favourites/}).click();
    await visible('#favourites a:text-is("Conduit Bend Marks + 3D")');
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(()=>document.getElementById('accountStatus').textContent.includes('qa-account-a'));
    await visible('#favourites a:text-is("Conduit Bend Marks + 3D")');
    await conduit.getByRole('button',{name:'How to use'}).click();
    await visible('#selectedTask');
    assert.ok((await page.locator('#selectedTask').innerText()).includes('Your workflow'),'Guidance explains next steps');
    await conduit.locator('a.open').click();
    await page.waitForURL('**/bend-instruction-lab.html');
    await goto('/start.html');
    await page.waitForFunction(()=>document.getElementById('accountStatus').textContent.includes('qa-account-a'));
    await visible('#recent a:text-is("Conduit Bend Marks + 3D")');

    activeUser='qa-account-b';
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(()=>document.getElementById('accountStatus').textContent.includes('qa-account-b'));
    assert.ok(!(await page.locator('#favourites').innerText()).includes('Conduit Bend Marks + 3D'),'Account B should not see A favourites');
    activeUser='qa-account-a';
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(()=>document.getElementById('accountStatus').textContent.includes('qa-account-a'));
    await visible('#favourites a:text-is("Conduit Bend Marks + 3D")');

    for(const width of [320,360,390,768,1280]){
      await page.setViewportSize({width,height:860});
      await checkNoOverflow('Find a Tool @'+width+'px');
      if([320,390,1280].includes(width))await take('tool-finder-'+width+'.png');
      const controls=await page.locator('button.filter').evaluateAll(nodes=>nodes.map(n=>Math.round(n.getBoundingClientRect().height)));
      assert.ok(controls.every(h=>h>=43),'Category touch targets @'+width);
    }
    await page.setViewportSize({width:390,height:860});
    await goto('/guided-calculators.html?tool=conductor');
    await visible('#conductor.module.active');
    await page.waitForFunction(()=>document.getElementById('ewbStatus').textContent.includes('qa-account-a'));
    await visible('#ewbSave');
    await page.locator('#c_i').fill('43');
    await page.locator('#ewbDraftName').fill('Release QA test feeder');
    await page.locator('#ewbSave').click();
    assert.ok((await page.locator('#ewbStatus').innerText()).includes('Saved'),'Draft successfully saved');
    const selections=await page.locator('#ewbSaved option').count();
    assert.ok(selections>=2,'Saved draft exists in selector');
    await page.locator('#c_i').fill('99');
    await page.locator('#ewbRestore').click();
    assert.equal(await page.locator('#c_i').inputValue(),'43','Restored exact input');
    assert.ok((await page.locator('#ewbStatus').innerText()).includes('Press Calculate again'),'Restored result requires revalidation');
    const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#ewbExport').click()]);
    assert.equal(download.suggestedFilename(),'electrical-workbench-worksheet.json');
    await take('guided-calculator-390.png');
    await checkNoOverflow('Guided calculator @390px');

    activeUser='qa-account-b';
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(()=>document.getElementById('ewbStatus').textContent.includes('qa-account-b'));
    assert.equal(await page.locator('#ewbSaved option').count(),1,'Account B cannot see A saved drafts');
    activeUser='qa-account-a';
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(()=>document.getElementById('ewbStatus').textContent.includes('qa-account-a'));
    assert.ok((await page.locator('#ewbSaved option').count())>=2,'Account A local saved draft preserved');
    await page.locator('#ewbSaved').selectOption({index:1});
    page.once('dialog', dialog=>dialog.accept());
    await page.locator('#ewbDelete').click();
    assert.equal(await page.locator('#ewbSaved option').count(),1,'Draft deleted');

    await goto('/field-suite.html#quote');
    await visible('#view-quote.view.active');
    await checkNoOverflow('Jobsite tools @390px');
    await take('jobsite-390.png');

    await goto('/start.html');
    await page.keyboard.press('Tab');
    const focusTag=await page.evaluate(()=>document.activeElement.tagName);
    assert.ok(['A','BUTTON','INPUT'].includes(focusTag),'Keyboard navigation finds interactive element');
    await page.evaluate(()=>{document.body.style.zoom='1.3';});
    await checkNoOverflow('Zoomed tool launcher @390px');
    await take('tool-finder-390-zoom.png');

    assert.equal(issues.length,0,'Unexpected browser runtime exceptions: '+issues.join(' | '));
    console.log('BROWSER QA PASS: search, categories, favourites, recent, account isolation, guided deep links, local draft save/restore/export/delete, field deep links, mobile overflow and keyboard focus.');
  } finally {
    await browser.close();
    await new Promise(resolve=>server.close(resolve));
  }
})().catch(e=>{console.error('BROWSER QA FAILED:',e.stack||e);process.exitCode=1;});
