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
  <div class="tier" data-tier="TIER_1"><h3>Tier 1</h3><div>Core tools</div><div style="margin-top:10px"><button id="buyTier1" class="btn ghost" disabled>Included / current base tier</button></div></div>
  <div class="tier" data-tier="TIER_2"><h3>Tier 2</h3><div>Expanded tools + Engineering Power Systems</div><div style="margin-top:10px"><button id="buyTier2" class="btn ghost" onclick="buyTier('TIER_2')">Buy / upgrade</button></div></div>
  <div class="tier" data-tier="TIER_3"><h3>Tier 3</h3><div>Advanced analytics + full engineering mode</div><div style="margin-top:10px"><button id="buyTier3" class="btn ghost" onclick="buyTier('TIER_3')">Buy / upgrade</button></div></div>
</div>
<div id="billingStatus" class="status"></div>
<div class="photo-help">Paid-tier prices and checkout are supplied by the configured payment provider. Purchasing does not grant features until the subscription is activated by the billing integration.</div>
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
      if(!opt?.checkoutConfigured){btn.textContent='Checkout not configured';btn.disabled=true;return;}
      btn.textContent='Buy / upgrade to '+t.replace('_',' ');btn.disabled=false;
    });
  }catch(e){const s=document.getElementById('billingStatus');if(s){s.textContent='Billing options could not be loaded.';s.className='status bad'}}
}
async function buyTier(t){
  try{
    if(!billingOptions)await loadBillingOptions();
    const opt=(billingOptions?.tiers||[]).find(x=>x.id===t);
    if(!opt?.checkoutUrl){setStatus('billingStatus','Checkout for '+t.replace('_',' ')+' is not configured yet.',false);return;}
    location.href=opt.checkoutUrl;
  }catch(e){setStatus('billingStatus',e.message||'Checkout could not be opened.',false)}
}
window.addEventListener('load',loadBillingOptions);
</script>
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
    return html;
  }
}
