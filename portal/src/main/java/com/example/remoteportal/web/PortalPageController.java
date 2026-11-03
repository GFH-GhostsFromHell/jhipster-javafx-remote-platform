package com.example.remoteportal.web;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the established portal page with small additive navigation and account controls.
 * The underlying portal.html remains the source UI.
 */
@Controller
public class PortalPageController {
  private static final String CALCULATOR_LINK="<a href=\"/calculators.html\"><span class=\"icon\">∑</span><span class=\"label\">CEC Calculators</span></a>";
  private static final String CHATGPT_LINK="<a href=\"/chatgpt.html\"><span class=\"icon\">✦</span><span class=\"label\">Ask ChatGPT</span></a>";
  private static final String ENGINEERING_LINK="<a href=\"/engineering-power.html\"><span class=\"icon\">⚙</span><span class=\"label\">Engineering Power</span></a>";
  private static final String TIERS_BLOCK="<div id=\"tiers\" class=\"tier-row\"><div class=\"tier\" data-tier=\"TIER_1\"><h3>Tier 1</h3><div>Core tools</div></div><div class=\"tier\" data-tier=\"TIER_2\"><h3>Tier 2</h3><div>Expanded tools</div></div><div class=\"tier\" data-tier=\"TIER_3\"><h3>Tier 3</h3><div>Advanced tools</div></div></div>";
  private static final String BILLING_TIERS="""
<div id="tiers" class="tier-row">
  <div class="tier" data-tier="TIER_1"><h3>Tier 1</h3><div>Core tools</div><div style="margin-top:10px"><button id="buyTier1" class="btn ghost" disabled>Included / base tier</button></div></div>
  <div class="tier" data-tier="TIER_2"><h3>Tier 2</h3><div>Expanded tools + Engineering Power Systems</div><div style="margin-top:10px"><button id="buyTier2" class="btn ghost" onclick="buyTier('TIER_2')">Subscribe / upgrade</button></div></div>
  <div class="tier" data-tier="TIER_3"><h3>Tier 3</h3><div>Advanced analytics + full engineering mode</div><div style="margin-top:10px"><button id="buyTier3" class="btn ghost" onclick="buyTier('TIER_3')">Subscribe / upgrade</button></div></div>
</div>
<div style="margin-top:12px"><button id="manageBilling" class="btn secondary" style="display:none" onclick="openBillingPortal()">Manage Stripe billing</button></div>
<div id="billingStatus" class="status"></div>
<div class="photo-help">Stripe confirms subscription status by signed webhook before paid features are granted. Existing paid subscribers use the Stripe billing portal to change or cancel plans.</div>
""";
  private static final String BILLING_SCRIPT="""
<script>
let billingOptions=null;
async function loadBillingOptions(){
  try{
    billingOptions=await api('/api/profile/billing-options');
    ['TIER_2','TIER_3'].forEach(t=>{
      const btn=document.getElementById(t==='TIER_2'?'buyTier2':'buyTier3');
      if(!btn)return;
      const opt=(billingOptions.tiers||[]).find(x=>x.id===t);
      if(billingOptions.currentTier===t){btn.textContent='Current tier';btn.disabled=true;return;}
      if(billingOptions.hasSubscription){btn.textContent='Change plan in billing portal';btn.disabled=false;return;}
      if(!opt?.checkoutConfigured){btn.textContent='Stripe price not configured';btn.disabled=true;return;}
      btn.textContent='Subscribe to '+t.replace('_',' ');btn.disabled=false;
    });
    const manage=document.getElementById('manageBilling');
    if(manage)manage.style.display=billingOptions.billingPortalAvailable?'inline-block':'none';
    const qs=new URLSearchParams(location.search);
    if(qs.get('billing')==='success')setStatus('billingStatus','Checkout completed. Stripe is confirming the subscription; paid features activate from the signed webhook.');
    if(qs.get('billing')==='cancel')setStatus('billingStatus','Checkout was cancelled. No subscription change was made.',false);
    if(qs.get('billing')==='return')setStatus('billingStatus','Returned from Stripe billing. Subscription changes sync automatically.');
  }catch(e){const s=document.getElementById('billingStatus');if(s){s.textContent='Billing options could not be loaded.';s.className='status bad'}}
}
async function buyTier(t){
  try{
    if(!billingOptions)await loadBillingOptions();
    if(billingOptions?.hasSubscription){await openBillingPortal();return;}
    const r=await api('/api/profile/checkout/'+encodeURIComponent(t),{method:'POST'});
    if(!r.url)throw new Error('Stripe did not return a checkout URL.');
    location.href=r.url;
  }catch(e){setStatus('billingStatus',e.message||'Checkout could not be opened.',false)}
}
async function openBillingPortal(){
  try{
    const r=await api('/api/profile/billing-portal',{method:'POST'});
    if(!r.url)throw new Error('Stripe did not return a billing portal URL.');
    location.href=r.url;
  }catch(e){setStatus('billingStatus',e.message||'Billing portal could not be opened.',false)}
}
window.addEventListener('load',loadBillingOptions);
</script>
""";
  private static final String MANAGEMENT_SCRIPTS="""
<script src="/admin-tier-inline.js"></script>
<script src="/enterprise-team.js"></script>
""";

  private final Resource portal;

  public PortalPageController(ResourceLoader resources){this.portal=resources.getResource("classpath:/static/portal.html");}

  @GetMapping(value="/portal.html",produces=MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String portal() throws IOException {
    String html=portal.getContentAsString(StandardCharsets.UTF_8);
    if(!html.contains(CHATGPT_LINK)) html=html.replace(CALCULATOR_LINK,CALCULATOR_LINK+"\n      "+CHATGPT_LINK);
    if(!html.contains(ENGINEERING_LINK)) {
      String anchor=html.contains(CHATGPT_LINK)?CHATGPT_LINK:CALCULATOR_LINK;
      html=html.replace(anchor,anchor+"\n      "+ENGINEERING_LINK);
    }
    if(!html.contains("id=\"buyTier2\"")) html=html.replace(TIERS_BLOCK,BILLING_TIERS);
    if(!html.contains("function buyTier(t)")) html=html.replace("</body>",BILLING_SCRIPT+"\n</body>");
    if(!html.contains("/enterprise-team.js")) html=html.replace("</body>",MANAGEMENT_SCRIPTS+"\n</body>");
    return html;
  }
}
