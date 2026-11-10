/* Shared task catalog for web navigation. Keep stable IDs in other clients. */
(function(g){'use strict';g.EWBTaskCatalog=Object.freeze([
  {
    "id": "conduit-marks",
    "name": "Conduit Bend Marks + 3D",
    "description": "Conduit bending with tape marks, shoe direction and a rotatable preview.",
    "category": "installation",
    "keywords": "offset saddle kick 90 stub mark hook flip pipe",
    "path": "/bend-instruction-lab.html",
    "steps": [
      "Enter the conduit size and required bend measurements.",
      "Confirm every mark and shoe orientation against your bender.",
      "Save or export the bend instructions from the tool."
    ]
  },
  {
    "id": "bend-guide",
    "name": "Conduit Bend Diagrams",
    "description": "See common bends and step-by-step field marking instructions.",
    "category": "installation",
    "keywords": "conduit emt offset saddle 90 kick rolling bend",
    "path": "/bend-calcs.html",
    "steps": [
      "Choose the bend type and dimensions.",
      "Check shrink, gain, deduct and shoe orientation.",
      "Record final marks for the job."
    ]
  },
  {
    "id": "bender-calibrate",
    "name": "Calibrate a Conduit Bender",
    "description": "Measure and save shoe deduct and gain for your bender.",
    "category": "installation",
    "keywords": "hand bender profile shoe deduct gain",
    "path": "/bender-calibration.html",
    "steps": [
      "Choose conduit size and bender.",
      "Measure your actual trial bend.",
      "Save the verified calibration profile."
    ]
  },
  {
    "id": "pull-box",
    "name": "Pull Box Dimensions",
    "description": "Determine straight, angle, U-pull and back-wall dimensions.",
    "category": "installation",
    "keywords": "box fill pull junction splice cable raceway",
    "path": "/pull-box-calcs.html",
    "steps": [
      "Select the pull layout and raceways.",
      "Verify required clearances and code assumptions.",
      "Save dimensions in your project."
    ]
  },
  {
    "id": "conductor-drop",
    "name": "Circuit Sizing & Voltage Drop",
    "description": "Check conductor ampacity, grouping, ambient and voltage drop.",
    "category": "calculations",
    "keywords": "cable wire k factor ampacity feeder table d3 length",
    "path": "/guided-calculators.html?tool=conductor",
    "steps": [
      "Enter load, voltage, phase, length and conductor facts.",
      "Review ampacity, voltage drop and CEC table selections.",
      "Save the calculation draft or export it."
    ]
  },
  {
    "id": "service-demand",
    "name": "Service Load Calculations",
    "description": "Calculate demand for dwellings, apartments, schools, hotels and hospitals.",
    "category": "calculations",
    "keywords": "residential apartment school hotel hospital service load demand",
    "path": "/guided-calculators.html?tool=service",
    "steps": [
      "Choose the building type and measured loads.",
      "Check demand assumptions and any Code table values.",
      "Save the demand worksheet."
    ]
  },
  {
    "id": "transformer",
    "name": "Transformer Sizing",
    "description": "Calculate transformer currents, ratios and protection checks.",
    "category": "calculations",
    "keywords": "transformer kva volts primary secondary ocpd fuse",
    "path": "/guided-calculators.html?tool=xfmr",
    "steps": [
      "Enter kVA, voltage and phase.",
      "Verify primary/secondary current and protection limits.",
      "Save the worksheet for review."
    ]
  },
  {
    "id": "motor",
    "name": "Motor Protection & Starter Sizing",
    "description": "Choose motor protection and starting methods.",
    "category": "calculations",
    "keywords": "motor flc overload fuse breaker disconnect starter ocpd",
    "path": "/calculators.html?section=s28",
    "steps": [
      "Enter motor type, nameplate and system values.",
      "Verify FLC/FLA sources and protection selections.",
      "Save the calculation or export the inputs."
    ]
  },
  {
    "id": "refrigeration",
    "name": "Refrigeration Motor Sizing",
    "description": "Size compressor circuits using MCA/MOCP and motor data.",
    "category": "calculations",
    "keywords": "hvac compressor rla lra mca mocp air conditioner",
    "path": "/guided-calculators.html?tool=refrig",
    "steps": [
      "Enter compressor and nameplate data.",
      "Check MCA, MOCP and special motor rules.",
      "Save the compressor worksheet."
    ]
  },
  {
    "id": "grounding",
    "name": "Grounding & Bonding",
    "description": "Review grounding electrodes and equipment bonding conductors.",
    "category": "calculations",
    "keywords": "ground electrode bond jumper cec section 10",
    "path": "/guided-calculators.html?tool=ground",
    "steps": [
      "Choose the grounding or bonding task.",
      "Confirm conductor sizes against the correct CEC table.",
      "Save the verified design notes."
    ]
  },
  {
    "id": "fire-pump",
    "name": "Fire Pump Circuit",
    "description": "Review fire-pump motor, controller and feeder values.",
    "category": "calculations",
    "keywords": "fire pump jockey locked rotor controller",
    "path": "/guided-calculators.html?tool=firepump",
    "steps": [
      "Enter motor and controller values.",
      "Check locked-rotor and applicable Code requirements.",
      "Save the worksheet."
    ]
  },
  {
    "id": "neutral",
    "name": "Neutral Sizing",
    "description": "Evaluate neutral and 347/600 V vector-current calculations.",
    "category": "calculations",
    "keywords": "neutral conductor 3 phase 347 600",
    "path": "/guided-calculators.html?tool=neutral",
    "steps": [
      "Enter phase currents and load details.",
      "Check non-linear loads and neutral requirements.",
      "Save your calculations."
    ]
  },
  {
    "id": "fire-alarm",
    "name": "Fire Alarm Circuits",
    "description": "Work through EOL and other fire-alarm circuit checks.",
    "category": "calculations",
    "keywords": "fire alarm eol resistor notification class",
    "path": "/guided-calculators.html?tool=firealarm",
    "steps": [
      "Enter source voltage and device values.",
      "Verify output and panel manufacturer instructions.",
      "Save the worksheet."
    ]
  },
  {
    "id": "advanced-code",
    "name": "Advanced CEC Calculators",
    "description": "More detailed Code worksheets: HVAC, welders, neutral and more.",
    "category": "calculations",
    "keywords": "advanced electrical conductor welders fire pumps protection",
    "path": "/advanced-calculations.html",
    "steps": [
      "Choose a specialist worksheet.",
      "Check the cited Code path and equipment data.",
      "Save calculations before leaving."
    ]
  },
  {
    "id": "cec-sections",
    "name": "Electrical Calculators by CEC Section",
    "description": "Browse all calculations grouped by electrical code section.",
    "category": "calculations",
    "keywords": "cec 2024 ontario rules section 4 8 12 14 26 28",
    "path": "/calculators.html",
    "steps": [
      "Pick the applicable CEC section.",
      "Enter exact problem and equipment values.",
      "Save the worksheet for reuse."
    ]
  },
  {
    "id": "relay",
    "name": "Protection & Relay Calculators",
    "description": "Instrument transformer ratios, relay pickup and field math.",
    "category": "engineering",
    "keywords": "ct pt current transformer ratio protection relay 4 20ma",
    "path": "/industrial-commissioning.html",
    "steps": [
      "Enter CT/PT ratio and test data.",
      "Verify relay settings against approved settings sheets.",
      "Record and export your findings."
    ]
  },
  {
    "id": "power",
    "name": "Power System Analysis",
    "description": "Per-unit, impedance, sequence components and fault studies.",
    "category": "engineering",
    "keywords": "short circuit three phase fault transmission lines symmetrical",
    "path": "/engineering-power.html",
    "steps": [
      "Pick a network analysis method.",
      "Check assumptions and equipment ratings.",
      "Save your study records."
    ]
  },
  {
    "id": "fault",
    "name": "Equipment Troubleshooting",
    "description": "Step-by-step diagnostic guides for motors, drives, PLCs and relays.",
    "category": "troubleshooting",
    "keywords": "fault isolation mine mining ep iroc vfd plc no start",
    "path": "/troubleshooting-kb.html",
    "steps": [
      "Identify the symptom and safe isolation procedure.",
      "Follow a documented fault-finding path.",
      "Record findings in a work order."
    ]
  },
  {
    "id": "jobsite",
    "name": "Jobsite Tools",
    "description": "Takeoffs, control diagrams, quotes, commissioning and nameplates.",
    "category": "projects",
    "keywords": "field drawings controls one line jobsite quote project",
    "path": "/field-suite.html",
    "steps": [
      "Choose the task you need on site.",
      "Complete the worksheet or diagram.",
      "Save it to the active project."
    ]
  },
  {
    "id": "takeoff",
    "name": "Drawing Takeoff",
    "description": "Scale blueprint runs and estimate material quantities.",
    "category": "projects",
    "keywords": "blueprint measure plan drawings scale quantities",
    "path": "/field-suite.html#blueprint",
    "steps": [
      "Load a drawing and set its scale.",
      "Verify each measured conduit run.",
      "Add takeoff lines to the project."
    ]
  },
  {
    "id": "quote",
    "name": "Build a Job Quote",
    "description": "Estimate materials, labour, markup and tax.",
    "category": "projects",
    "keywords": "estimating contractor price quote invoice labour materials",
    "path": "/field-suite.html#quote",
    "steps": [
      "Enter labour and material items.",
      "Review assumptions and tax/markup.",
      "Save or print the quote."
    ]
  },
  {
    "id": "commission",
    "name": "Commissioning Records",
    "description": "Record test measurements, sign-off and inspection details.",
    "category": "projects",
    "keywords": "commission testing test report insulation resistance voltage",
    "path": "/field-suite.html#commission",
    "steps": [
      "Choose the equipment and test list.",
      "Enter measured values and verification notes.",
      "Save the report to the project."
    ]
  },
  {
    "id": "saved",
    "name": "Saved Work & Exports",
    "description": "Open cloud-synced records and export saved data.",
    "category": "projects",
    "keywords": "save records sync cloud backup reports export pdf",
    "path": "/account-records.html",
    "steps": [
      "Choose a record collection.",
      "Review synchronized records and any conflicts.",
      "Export or reuse the saved work."
    ]
  },
  {
    "id": "code-updates",
    "name": "Code Updates & Bulletins",
    "description": "Ontario electrical code announcements and notices.",
    "category": "reference",
    "keywords": "cec oesc esa news changes bulletin",
    "path": "/news.html",
    "steps": [
      "Choose the relevant Code update.",
      "Verify publication date and applicability.",
      "Keep the official reference for the job."
    ]
  }
]);})(window);
