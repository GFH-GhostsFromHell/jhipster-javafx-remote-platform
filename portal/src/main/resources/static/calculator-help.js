(() => {
  const STYLE_ID = 'calculator-help-style';
  if (!document.getElementById(STYLE_ID)) {
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .calc-guide{margin-top:12px;padding:13px 14px;border:1px solid #bfdbfe;border-radius:11px;background:#eff6ff;color:#334155;font-size:12.5px;line-height:1.45}
      .calc-guide b{display:block;color:#1d4ed8;font-size:13px;margin-bottom:6px}
      .calc-guide ol{margin:6px 0 8px 19px;padding:0}.calc-guide li{margin:3px 0}
      .calc-guide .refs{border-top:1px solid #bfdbfe;padding-top:7px;margin-top:7px;font-weight:700;color:#0f4c81}
      .boxfill-groups{display:grid;grid-template-columns:1.25fr .75fr;gap:8px;margin-top:8px}
      .boxfill-groups .head{font-size:11px;font-weight:800;color:#64748b;text-transform:uppercase;letter-spacing:.04em}
      .boxfill-note{margin-top:9px;padding:10px 11px;border-radius:9px;background:#f8fafc;border:1px solid #e2e8f0;color:#475569;font-size:12px;line-height:1.4}
      @media(max-width:680px){.boxfill-groups{grid-template-columns:1fr 1fr}}
    `;
    document.head.appendChild(style);
  }

  const guides = {
    o_r: {
      steps: ['Enter the dwelling floor areas and connected loads from the question.', 'Enter heating and A/C separately; check the interlock box only when they cannot operate together.', 'Calculate, then use the resulting service load/current to select service equipment and conductors.'],
      refs: 'CEC 2024 Rule 8-200; applicable demand/load tables and conductor ampacity rules.'
    },
    o_a: {
      steps: ['Enter suite count, suite area and the loads that apply to each suite.', 'Enter common/house loads and the stated demand/continuous factors.', 'Calculate the building demand, then size the service/feeder from the resulting current.'],
      refs: 'CEC 2024 Rule 8-202; applicable dwelling and service conductor rules.'
    },
    o_sc: {
      steps: ['Enter total school area and classroom area.', 'Add other connected loads, heating/A-C, EVSE and high-power cord loads as applicable.', 'Calculate the demand load and use the result for feeder/service sizing.'],
      refs: 'CEC 2024 Rule 8-204; applicable Section 8 demand tables.'
    },
    o_h: {
      steps: ['Enter total hospital area and high-intensity area.', 'Enter the non-area loads plus heating/A-C and any special loads.', 'Calculate the hospital demand before sizing the service conductors and equipment.'],
      refs: 'CEC 2024 Rule 8-206; applicable Section 8 demand tables.'
    },
    o_o: {
      steps: ['Choose the occupancy basis from the question and enter the Table 14 W/m² value.', 'Enter the applicable demand factor and special connected loads.', 'Calculate demand current, then select conductor/equipment ratings from the applicable ampacity rules.'],
      refs: 'CEC 2024 Rule 8-210 and Table 14, plus the occupancy-specific rule where applicable.'
    },
    o_cf: {
      steps: ['Determine each conductor type/size and its conductor area.', 'Enter the raceway usable area and the maximum fill percentage for the number of conductors.', 'Add each conductor group and calculate; the used area must not exceed allowable fill.'],
      refs: 'CEC 2024 Rule 12-910; Tables 6A–6K for conductor area, Table 8 for fill percentage, Tables 9A–9H for raceway area.'
    },
    o_bf: {
      steps: ['Enter the box internal/marked volume in mL.', 'Add conductor groups by selecting AWG size; the Table 22 mL allowance is filled in automatically.', 'Add standard flush devices, capped connectors and fixture studs/hickeys; calculate and confirm required volume does not exceed the box volume.'],
      refs: 'CEC 2024 Rule 12-3034; Table 22 conductor-space allowances; Table 23 for listed standard boxes.'
    },
    o_bend: {
      steps: ['Enter the required offset/rise and bend angle.', 'For rolling offsets, enter both vertical and horizontal displacement.', 'For stub-ups, enter the desired stub and the take-up marked on the actual bender.'],
      refs: 'CEC 2024 Rule 12-924 and the raceway-specific bend-radius requirements; field geometry uses standard trigonometry.'
    },
    o_sup: {
      steps: ['Choose the wiring method and raceway size when applicable.', 'Enter the straight run length between boxes/equipment.', 'Use the calculated support count as a minimum layout check, then verify special conditions and exceptions.'],
      refs: 'CEC 2024 Rule 12-510 for NMD90, Rule 12-618 for AC90/BX, and the applicable raceway support rule such as 12-1010, 12-1114, 12-1406 or 12-1504.'
    },
    tap_general_out: {
      steps: ['Choose the 3 m equipment-tap or 7.5 m one-third-tap mode.', 'Enter the larger conductor ampacity, selected tap ampacity, length and downstream OCPD.', 'Confirm all required physical-protection/termination conditions before accepting PASS.'],
      refs: 'CEC 2024 Rule 14-100(1)(b) and 14-100(1)(c), with Rule 14-104 where applicable.'
    },
    tap_tx_out: {
      steps: ['Enter the transformer kVA, primary/secondary voltage, phases and larger feeder ampacity.', 'Enter the selected primary and secondary tap ampacities plus both conductor lengths.', 'Check the one-third primary floor, transformer load basis, voltage-ratio secondary floor, combined length and terminating OCPD.'],
      refs: 'CEC 2024 Rule 14-100(1)(f), Rule 26-256, and Rule 14-104 where applicable.'
    },
    tap_motor_out: {
      steps: ['Choose feeder-tap or group-branch-tap mode.', 'Enter source conductor ampacity, motor FLC(s), selected tap ampacity and tap length.', 'Verify the motor-load basis and the special length/mechanical-protection conditions before accepting PASS.'],
      refs: 'CEC 2024 Rules 28-106(3) and 28-110(2); Table 27 for non-continuous-duty motors.'
    },
    tap_w_out: {
      steps: ['Choose the welder type and enter its rated/actual primary current and duty-cycle factor.', 'Enter feeder ampacity, selected tap ampacity, tap length and downstream OCPD.', 'The calculator first establishes the Section 42 supply-conductor load, then applies the general tap rule.'],
      refs: 'CEC 2024 Section 42 and the applicable welder table; Rule 14-100 for reduced taps; Section 28 also applies to motor-generator welders.'
    },
    o_lv: {
      steps: ['Select Class 1 or Class 2 and enter source VA and circuit voltage.', 'Enter the permitted OCPD and selected cable ampacity.', 'Calculate source current and verify both OCPD and conductor limits.'],
      refs: 'CEC 2024 Section 16, including Rules 16-100/16-104 and Rule 16-200/Table 57 where applicable.'
    },
    o_pc: {
      steps: ['Enter the patient bed/table dimensions.', 'Use the result to visualize the patient-care environment around the patient-supporting mechanism.', 'Confirm the actual area classification and wiring requirements from the facility design and Section 24.'],
      refs: 'CEC 2024 Section 24 — Patient Care Areas.'
    },
    o_pc_lv: {
      steps: ['Enter the actual low-voltage source rating and voltage.', 'Select the circuit class and enter the permitted OCPD/cable ampacity.', 'Verify Section 24 requirements in addition to Section 16.'],
      refs: 'CEC 2024 Sections 16 and 24; listed medical equipment instructions also apply.'
    },
    o_dt: {
      steps: ['Enter transformer kVA, primary/secondary voltage and phases.', 'Calculate primary and secondary current before selecting protection/conductors.', 'Use the result with the applicable conductor ampacity, raceway and bonding tables.'],
      refs: 'CEC 2024 Rule 26-256; Tables 2/4 and the applicable raceway/bonding tables.'
    },
    o_mv: {
      steps: ['Enter transformer MVA, voltages and protection type.', 'Enter the protection multiplier required by the question.', 'Calculate primary/secondary current and conductor minimums, then select the applicable high-voltage cable table.'],
      refs: 'CEC 2024 Sections 26 and 36; Rule 26-256 and applicable high-voltage cable tables.'
    },
    o_hv: {
      steps: ['Enter transformer MVA, high/low voltage and protection type.', 'Calculate both side currents and the protection-derived conductor minimum.', 'Select conductor/cable from the voltage-class table and verify high-voltage installation requirements.'],
      refs: 'CEC 2024 Sections 26 and 36; Rule 26-256 and applicable D17/high-voltage cable tables.'
    },
    o_cap: {
      steps: ['Enter capacitor kVAR, voltage and phases.', 'Calculate capacitor current, then size conductors at 135%.', 'Check OCPD at the permitted maximum and size the disconnect separately.'],
      refs: 'CEC 2024 Rules 26-208, 26-210, 26-212 and 26-214.'
    },
    o_pf: {
      steps: ['Enter real load kW, present power factor and target power factor.', 'Calculate required kVAR and capacitor current.', 'Use the result for capacitor conductor, protection and disconnect sizing.'],
      refs: 'CEC 2024 capacitor Rules 26-208 through 26-214; equipment/manufacturer requirements also apply.'
    },
    o_acm: {
      steps: ['Enter the applicable motor FLC/FLA from the question or CEC motor-current table.', 'Apply the conductor-duty factor and the correct branch OCPD factor for the motor/protective device.', 'Calculate conductor, overload and disconnect values separately.'],
      refs: 'CEC 2024 Section 28; Table 27 for duty, Table 29 for branch OCPD, Tables 44/45 for motor current where applicable.'
    },
    o_dcm: {
      steps: ['Enter the DC motor current from the question/nameplate or applicable table.', 'Apply the required conductor and protection factors for the DC motor circuit.', 'Keep field-circuit and speed-control calculations separate from armature/supply sizing.'],
      refs: 'CEC 2024 Section 28 and applicable DC motor/current/protection rules.'
    },
    o_mm: {
      steps: ['Enter all motor FLC values.', 'Size the feeder conductor from the largest-motor factor plus the remaining motors.', 'Size feeder OCPD from the largest permitted branch OCPD plus the other motor currents, including any allowed simultaneous-start provision.'],
      refs: 'CEC 2024 Rule 28-204 and related Section 28 feeder/OCPD rules.'
    },
    o_ref: {
      steps: ['Enter compressor RLA and LRA; use nameplate values when supplied.', 'Calculate minimum conductor ampacity and the applicable normal/not-starting OCPD limits.', 'Verify overload and disconnect requirements separately.'],
      refs: 'CEC 2024 Section 28 refrigeration/hermetic-motor rules and equipment nameplate MCA/MOCP where provided.'
    },
    o_fa_lv: {
      steps: ['Enter the listed fire-alarm/control source VA and voltage.', 'Enter the listed maximum OCPD and cable ampacity.', 'Verify the circuit classification and the fire-alarm equipment listing before final selection.'],
      refs: 'CEC 2024 Sections 16 and 32; listed fire-alarm equipment instructions control where more restrictive.'
    },
    o_eol: {
      steps: ['Enter the supervision circuit voltage and end-of-line resistance.', 'Calculate current using I = V/R.', 'Compare with the listed fire-alarm panel/module supervision requirement.'],
      refs: 'Ohm’s law plus CEC 2024 Section 32 and the listed fire-alarm equipment instructions.'
    },
    o_fp: {
      steps: ['Enter fire-pump motor FLC plus jockey/auxiliary loads that belong in the conductor calculation.', 'Calculate the required conductor ampacity.', 'Then verify controller, disconnect, OCPD and wiring-method requirements separately.'],
      refs: 'CEC 2024 Section 32 fire-pump requirements.'
    },
    o_w: {
      steps: ['Choose welder type and enter kVA, voltage and applicable duty-cycle multiplier.', 'Calculate duty-adjusted conductor load.', 'Select conductor and branch protection from the applicable Section 42 rule/table.'],
      refs: 'CEC 2024 Section 42 and the applicable Tables 42A/42B/42C or equipment data.'
    },
    o_wg: {
      steps: ['Enter each welder input current, duty factor and selected branch conductor ampacity.', 'Apply the multi-welder demand sequence to obtain feeder conductor load.', 'Calculate feeder OCPD from the permitted largest branch protection plus the remaining duty-adjusted loads.'],
      refs: 'CEC 2024 Section 42 multiple-welder feeder/demand rules and applicable welder tables.'
    }
  };

  function guideHtml(g) {
    return `<div class="calc-guide"><b>How to use this calculator</b><ol>${g.steps.map(s => `<li>${s}</li>`).join('')}</ol><div class="refs">Code reference: ${g.refs}</div></div>`;
  }

  function addGuides() {
    Object.entries(guides).forEach(([id, g]) => {
      const out = document.getElementById(id);
      if (!out) return;
      const ws = out.closest('.workspace');
      if (!ws) return;
      const left = ws.children[0];
      if (!left || left.querySelector(`.calc-guide[data-for="${id}"]`)) return;
      const holder = document.createElement('div');
      holder.innerHTML = guideHtml(g);
      const guide = holder.firstElementChild;
      guide.dataset.for = id;
      left.appendChild(guide);
    });
  }

  const allowances = {14:24.6, 12:28.7, 10:36.9, 8:45.1, 6:73.7};
  const sizeOrder = [14,12,10,8,6];
  function sizeOptions(selected, allowAuto=false) {
    const auto = allowAuto ? '<option value="AUTO">Auto — largest entered conductor</option>' : '';
    return auto + sizeOrder.map(s => `<option value="${s}" ${String(s)===String(selected)?'selected':''}>#${s} AWG — ${allowances[s]} mL each</option>`).join('');
  }
  function boxGroup(row, selected, qty) {
    return `<select id="bf_size_${row}">${sizeOptions(selected)}</select><input id="bf_qty_${row}" type="number" min="0" step="1" value="${qty}">`;
  }
  function largestEnteredSize() {
    let chosen = null;
    for (let r=1;r<=4;r++) {
      const q = Math.max(0, parseInt(document.getElementById(`bf_qty_${r}`)?.value || '0',10) || 0);
      const s = parseInt(document.getElementById(`bf_size_${r}`)?.value || '14',10);
      if (q>0 && (chosen===null || allowances[s] > allowances[chosen])) chosen=s;
    }
    return chosen || 14;
  }
  function selectedOrAuto(id) {
    const v = document.getElementById(id)?.value || 'AUTO';
    return v==='AUTO' ? largestEnteredSize() : parseInt(v,10);
  }
  function nval(id) {
    const v = parseFloat(document.getElementById(id)?.value || '0');
    if (!Number.isFinite(v) || v<0) throw new Error(`Check ${id}`);
    return v;
  }
  function calculateBoxFill() {
    const out = document.getElementById('o_bf');
    try {
      const capacity = nval('bf_capacity');
      if (capacity<=0) throw new Error('box volume');
      let conductorVolume=0, conductorCount=0, lines=[];
      for (let r=1;r<=4;r++) {
        const size=parseInt(document.getElementById(`bf_size_${r}`).value,10);
        const qty=Math.max(0,Math.trunc(nval(`bf_qty_${r}`)));
        if (!qty) continue;
        const vol=qty*allowances[size];
        conductorCount+=qty; conductorVolume+=vol;
        lines.push(`${qty} × #${size} AWG @ ${allowances[size].toFixed(1)} mL = ${vol.toFixed(1)} mL`);
      }
      const devices=Math.trunc(nval('bf_devices'));
      const connectors=Math.trunc(nval('bf_connectors'));
      const studs=Math.trunc(nval('bf_studs'));
      const deep=nval('bf_deep');
      const adjSize=selectedOrAuto('bf_adjust_size'), connSize=selectedOrAuto('bf_connector_size');
      const deviceVol=devices*2*allowances[adjSize];
      const studVol=studs*allowances[adjSize];
      const connectorPairs=Math.floor(connectors/2);
      const connectorVol=connectorPairs*allowances[connSize];
      const required=conductorVolume+deviceVol+studVol+connectorVol+deep;
      const remaining=capacity-required;
      const pass=remaining>=-1e-9;
      out.textContent = [
        'CEC 2024 BOX FILL — RULE 12-3034 / TABLE 22','',
        'INSULATED CONDUCTORS',
        ...(lines.length?lines:['No insulated conductors entered']),
        `Conductor subtotal = ${conductorVolume.toFixed(1)} mL`,'',
        'ADJUSTMENTS',
        `Flush-mounted single-strap devices: ${devices} × 2 × #${adjSize} allowance (${allowances[adjSize].toFixed(1)} mL) = ${deviceVol.toFixed(1)} mL`,
        `Fixture studs / hickeys: ${studs} × #${adjSize} allowance = ${studVol.toFixed(1)} mL`,
        `Insulating-cap connectors: ${connectors} connector(s) = ${connectorPairs} pair allowance(s) × #${connSize} = ${connectorVol.toFixed(1)} mL`,
        `Additional deep-device volume entered = ${deep.toFixed(1)} mL`,'',
        `TOTAL REQUIRED = ${required.toFixed(1)} mL`,
        `BOX VOLUME = ${capacity.toFixed(1)} mL`,
        `REMAINING = ${remaining.toFixed(1)} mL`,
        `RESULT: ${pass?'PASS — volume is sufficient':'FAIL — box volume is insufficient'}`,'',
        'Counting reminders: insulated conductors that enter/leave or pass through count; conductors entirely inside the box do not. Locknuts, bushings, box connectors and clamps are disregarded by Rule 12-3034(5). Bare bonding conductors are not entered here; an insulated bonding conductor should be added as an insulated-conductor group.'
      ].join('\n');
    } catch (e) {
      out.textContent = 'Check inputs: '+e.message;
    }
  }

  function upgradeBoxFill() {
    const out=document.getElementById('o_bf');
    if (!out) return;
    const ws=out.closest('.workspace');
    if (!ws || ws.dataset.boxFillUpgraded==='1') return;
    ws.dataset.boxFillUpgraded='1';
    const left=ws.children[0];
    left.innerHTML = `
      <div class="card">
        <h3>Box fill — CEC 2024 Table 22 built in</h3>
        <div class="grid">
          <div class="field wide"><label>Box usable / marked volume (mL)</label><input id="bf_capacity" type="number" min="0" value="245"></div>
        </div>
        <div class="boxfill-groups"><div class="head">Conductor size / automatic mL allowance</div><div class="head">Quantity</div>${boxGroup(1,14,6)}${boxGroup(2,12,0)}${boxGroup(3,10,0)}${boxGroup(4,8,0)}</div>
        <div class="grid" style="margin-top:12px">
          <div class="field"><label>Flush-mounted single-strap devices</label><input id="bf_devices" type="number" min="0" step="1" value="0"></div>
          <div class="field"><label>Fixture studs / hickeys</label><input id="bf_studs" type="number" min="0" step="1" value="0"></div>
          <div class="field"><label>Insulating-cap connectors</label><input id="bf_connectors" type="number" min="0" step="1" value="0"></div>
          <div class="field"><label>Additional deep-device volume (mL)</label><input id="bf_deep" type="number" min="0" step="0.1" value="0"></div>
          <div class="field"><label>Device / stud conductor-size allowance</label><select id="bf_adjust_size">${sizeOptions('AUTO',true)}</select></div>
          <div class="field"><label>Capped-connector conductor-size allowance</label><select id="bf_connector_size">${sizeOptions('AUTO',true)}</select></div>
        </div>
        <div class="boxfill-note">Table 22 allowances are built in: #14 24.6 mL, #12 28.7 mL, #10 36.9 mL, #8 45.1 mL, #6 73.7 mL. For a device deeper than 2.54 cm, enter the additional device volume required by Rule 12-3034(3), or verify the full calculation directly from the Code.</div>
      </div>
      <button class="btn" id="bf_calculate" type="button">Calculate box fill</button>`;
    document.getElementById('bf_calculate').addEventListener('click',calculateBoxFill);
  }

  function init() {
    upgradeBoxFill();
    addGuides();
  }
  if (document.readyState==='loading') document.addEventListener('DOMContentLoaded',()=>{setTimeout(init,25);setTimeout(init,500)});
  else {setTimeout(init,25);setTimeout(init,500)}
})();
