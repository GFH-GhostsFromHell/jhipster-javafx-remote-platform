package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public final class CalculatorGuideDecorator {
  private CalculatorGuideDecorator() {}

  public static Node decorate(Node root) {
    decorateNode(root);
    return root;
  }

  private static void decorateNode(Node node) {
    if (node == null) return;
    if (node instanceof TabPane tabs) {
      for (Tab tab : tabs.getTabs()) {
        Node content = tab.getContent();
        decorateNode(content);
        Guide g = guideFor(tab.getText());
        if (g != null && content != null && !Boolean.TRUE.equals(content.getProperties().get("guideWrapped"))) {
          BorderPane wrapper = new BorderPane();
          wrapper.setCenter(content);
          VBox guide = guideBox(g);
          BorderPane.setMargin(guide, new Insets(8,14,14,14));
          wrapper.setBottom(guide);
          wrapper.getProperties().put("guideWrapped", true);
          tab.setContent(wrapper);
        }
      }
      return;
    }
    if (node instanceof TitledPane pane) {
      Node content = pane.getContent();
      decorateNode(content);
      Guide g = guideFor(pane.getText());
      if (g != null && content != null && !Boolean.TRUE.equals(pane.getProperties().get("guideWrapped"))) {
        VBox box = new VBox(10, content, guideBox(g));
        VBox.setVgrow(content, Priority.ALWAYS);
        pane.setContent(box);
        pane.getProperties().put("guideWrapped", true);
      }
      return;
    }
    if (node instanceof Parent p) {
      for (Node child : new ArrayList<>(p.getChildrenUnmodifiable())) decorateNode(child);
    }
  }

  private static VBox guideBox(Guide g) {
    Label h = new Label("How to use this calculator");
    h.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");
    StringBuilder body = new StringBuilder();
    for (int i=0;i<g.steps.length;i++) body.append(i+1).append(". ").append(g.steps[i]).append('\n');
    body.append("\nCEC 2024 reference: ").append(g.refs);
    Label b = new Label(body.toString().trim());
    b.setWrapText(true);
    b.setStyle("-fx-text-fill:#334155;-fx-font-size:12px;");
    VBox v = new VBox(6,h,b);
    v.setPadding(new Insets(11,13,11,13));
    v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:9;-fx-border-radius:9;");
    return v;
  }

  private static Guide guideFor(String title) {
    if (title == null) return null;
    String t = title.toLowerCase(Locale.ROOT);

    if (t.equals("residential")) return g(new String[]{"Enter dwelling areas and connected loads.","Confirm whether heating and A/C are interlocked.","Calculate demand current, then use that result for service conductor/equipment sizing."},"Rule 8-200 and applicable Section 8 demand tables.");
    if (t.contains("apartment")) return g(new String[]{"Enter suite count, suite area and per-suite loads.","Enter common/house loads and the stated demand factors.","Calculate total demand, then size the service/feeder from the resulting current."},"Rule 8-202 and applicable dwelling/service rules.");
    if (t.equals("school")) return g(new String[]{"Enter total and classroom areas.","Add other connected loads, heating/A-C and special loads.","Calculate demand before choosing service conductors/equipment."},"Rule 8-204 and applicable Section 8 tables.");
    if (t.equals("hospital")) return g(new String[]{"Enter total and high-intensity areas.","Add other connected loads and heating/A-C.","Calculate the hospital demand before service sizing."},"Rule 8-206 and applicable Section 8 tables.");
    if (t.contains("other occupancy")) return g(new String[]{"Enter occupancy area and the correct Table 14 W/m² value.","Enter the applicable demand factor and special loads.","Calculate final demand current and size the service/feeder from that result."},"Rule 8-210 and Table 14, plus the occupancy-specific rule where applicable.");

    if (t.contains("dimensions") && t.contains("ampacity")) return g(new String[]{"Enter busbar material, width and thickness.","Calculate cross-sectional area and apply the selected current-density basis.","Verify spacing, temperature and enclosure assumptions before field selection."},"CEC 2024 Section 4 and the applicable busbar/current-density design basis used by the question.");
    if (t.contains("current") && t.contains("bar size")) return g(new String[]{"Enter required current and busbar material.","Calculate required cross-sectional area.","Choose practical width/thickness dimensions meeting or exceeding the result."},"CEC 2024 Section 4; verify the current-density basis specified by the question/design.");
    if (t.contains("parallel straps")) return g(new String[]{"Enter required total current and one strap/bar rating.","Divide total current by one-bar capacity and round up.","Confirm equal current sharing and installation conditions."},"CEC 2024 Section 4 and applicable parallel-conductor/busbar design requirements.");
    if (t.contains("current density")) return g(new String[]{"Enter busbar area and current.","Calculate current density using the selected units.","Compare with the design limit supplied by the question or engineering standard."},"CEC 2024 Section 4; current-density limit is a design input, not a universal single Code value.");

    if (t.contains("voltage drop") || t.contains("d3")) return g(new String[]{"Enter voltage, load current, one-way length and the matching Table D3 K value.","Choose 1-phase/DC or 3-phase and the correct temperature adjustment.","Use the result for actual voltage drop, maximum length or maximum allowable K."},"Rule 8-102 and Appendix D Table D3 notes.");
    if (t.contains("pvc expansion")) return g(new String[]{"Enter conduit length and expected minimum/maximum temperatures.","Use the PVC expansion coefficient or manufacturer value.","If movement exceeds the Code trigger, size/locate expansion joints using the listed fitting travel."},"Rule 12-1118 and Appendix B guidance for rigid PVC expansion.");
    if (t.contains("conduit bending")) return g(new String[]{"Enter offset/rise and bend angle.","For rolling offsets, enter vertical and horizontal displacement.","For stub-ups, use the take-up marked on the actual bender."},"Rule 12-924 and raceway-specific minimum bend-radius rules.");
    if (t.contains("supports") || t.contains("straps")) return g(new String[]{"Choose the wiring method and raceway size when applicable.","Enter the straight run length.","Use the result as a minimum support layout and verify exceptions/field conditions."},"Rules 12-510, 12-618 and the applicable raceway support rules such as 12-1010, 12-1114, 12-1406 or 12-1504.");
    if (t.startsWith("conduit fill")) return g(new String[]{"Determine conductor type/size and conductor area.","Enter raceway area and allowable fill percentage.","Confirm total conductor area does not exceed permitted raceway fill."},"Rule 12-910; Tables 6A–6K, Table 8 and Tables 9A–9H.");

    if (t.contains("general tap")) return g(new String[]{"Choose the applicable 3 m or 7.5 m tap case.","Enter the larger conductor ampacity, tap ampacity, length and terminating OCPD.","Confirm the physical-protection and termination conditions before accepting PASS."},"Rule 14-100(1)(b)/(c) and Rule 14-104 where applicable.");
    if (t.contains("transformer tap")) return g(new String[]{"Enter transformer kVA/voltages and larger feeder ampacity.","Enter selected primary/secondary tap ampacities and both lengths.","Check one-third, voltage-ratio, load, length and downstream-protection conditions together."},"Rule 14-100(1)(f), Rule 26-256 and Rule 14-104 where applicable.");
    if (t.contains("motor tap")) return g(new String[]{"Choose feeder-tap or group-branch-tap mode.","Enter source conductor ampacity, motor FLC(s), tap ampacity and length.","Verify motor load plus the special tap length/protection conditions."},"Rules 28-106(3) and 28-110(2); Table 27 for non-continuous-duty motors.");
    if (t.contains("welder tap")) return g(new String[]{"Determine the welder supply-conductor load first.","Enter feeder/tap ampacity, length and downstream OCPD.","Apply the general tap rule after the Section 42 calculation."},"Section 42 plus Rule 14-100; Section 28 also applies to motor-generator welders.");

    if (t.contains("class 2 power")) return g(new String[]{"Enter source VA and voltage.","Calculate source-limited current and choose OCPD that does not exceed the permitted value.","Check the selected cable ampacity against the actual load."},"Section 16, including Rule 16-200 and Table 57 where applicable.");
    if (t.contains("class 1 / listed")) return g(new String[]{"Identify whether the circuit is Class 1 or equipment-specific/listed.","Use the source/OCPD/conductor limits that apply to that classification.","Where listed equipment is more restrictive, follow the listing."},"Section 16, including Rules 16-100 and 16-104 as applicable.");
    if (t.contains("patient care environment")) return g(new String[]{"Enter the normal bed/table dimensions.","Use the calculated footprint only as a layout aid.","Confirm the actual patient-care classification and wiring rules for the room."},"Section 24; patient-care environment definition and Rule 24-100 context.");

    if (t.contains("dry-type")) return g(new String[]{"Enter kVA, primary/secondary voltage and phases.","Calculate both side currents before conductor/protection selection.","Use the applicable ampacity, raceway and bonding tables for final sizes."},"Rule 26-256 plus applicable Tables 2/4 and raceway/bonding tables.");
    if (t.contains("medium-voltage")) return g(new String[]{"Enter transformer MVA, voltages and protection type.","Apply the protection factor required by the question.","Calculate primary/secondary minimums and select the applicable high-voltage cable table."},"Sections 26 and 36; Rule 26-256 and applicable D17/high-voltage cable tables.");
    if (t.contains("high-voltage")) return g(new String[]{"Enter MVA, primary/secondary kV and protection type.","Calculate current and protection-derived conductor minimums.","Select conductor/cable by voltage class and wiring method."},"Sections 26 and 36; Rule 26-256 and applicable high-voltage cable tables.");
    if (t.contains("capacitor bank")) return g(new String[]{"Enter kVAR, voltage and phases.","Calculate capacitor current and conductor minimum at 135%.","Check OCPD and disconnect ratings separately."},"Rules 26-208, 26-210, 26-212 and 26-214.");

    if (t.equals("ac motors")) return g(new String[]{"Enter the correct motor FLC/FLA.","Apply conductor-duty and branch-OCPD factors from the applicable motor rules/tables.","Calculate conductor, overload and disconnect values separately."},"Section 28; Table 27, Table 29 and Tables 44/45 as applicable.");
    if (t.equals("dc motors")) return g(new String[]{"Enter DC motor current from the question/nameplate or applicable table.","Apply conductor and protection factors for the DC circuit.","Keep field-circuit/speed-control calculations separate from armature supply sizing."},"Section 28 and applicable DC motor rules.");
    if (t.contains("multiple motors")) return g(new String[]{"Enter all motor FLC values.","Size feeder conductors from 125% of the largest motor plus the others for continuous duty.","Calculate feeder OCPD using the permitted largest branch OCPD plus remaining motor currents."},"Rule 28-204 and related Section 28 feeder/OCPD rules.");
    if (t.contains("refrigeration")) return g(new String[]{"Enter compressor RLA and LRA/nameplate values.","Calculate conductor and normal/not-starting protection values.","Verify overload, disconnect and equipment nameplate limits separately."},"Section 28 refrigeration/hermetic-motor rules and equipment nameplate MCA/MOCP where provided.");

    if (t.equals("fire pump")) return g(new String[]{"Enter fire-pump FLC plus applicable jockey/auxiliary loads.","Calculate minimum supply-conductor ampacity.","Verify controller, disconnect, OCPD and fire-pump wiring method separately."},"Rule 32-300 and related Section 32 fire-pump rules.");
    if (t.contains("eol calculator")) return g(new String[]{"Enter supervision voltage and EOL resistance.","Calculate I = V/R.","Compare with the listed fire-alarm control equipment supervision requirement."},"Ohm's law plus Section 32 and listed equipment instructions.");

    if (t.equals("single welder")) return g(new String[]{"Enter welder rating, voltage and duty-cycle factor.","Calculate duty-adjusted conductor load.","Check conductor selection and both Section 42 OCPD limits."},"Section 42 and applicable welder duty-cycle/OCPD tables.");
    if (t.contains("multiple welder")) return g(new String[]{"Enter each welder input current, duty factor and branch-conductor ampacity.","Apply the multiple-welder demand sequence to obtain feeder load.","Calculate feeder OCPD using the permitted largest branch protection plus the remaining duty-adjusted loads."},"Section 42 multiple-welder feeder/demand rules.");

    return null;
  }

  private static Guide g(String[] steps,String refs){return new Guide(steps,refs);}
  private record Guide(String[] steps,String refs){}
}
