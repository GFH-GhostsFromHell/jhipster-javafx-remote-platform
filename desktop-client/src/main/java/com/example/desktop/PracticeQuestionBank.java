package com.example.desktop;

import java.time.LocalDate;
import java.util.*;

/** Starter import of the user's existing 309A/Red Seal study bank, five questions per task category. */
public final class PracticeQuestionBank {
    public record Question(String category, String question, List<String> options, int correctIndex, String explanation, String reference) {}

    private PracticeQuestionBank() {}

    public static List<Question> all() {
        return List.of(
            q("A — Common Occupational Skills","A hydraulic press operates at 15,000 psi with a piston area of 2 in². What force is produced?","7,500 lb|15,000 lb|30,000 lb|45,000 lb",2,"Force = pressure × area = 15,000 × 2 = 30,000 lb.","Trade math / hydraulic force"),
            q("A — Common Occupational Skills","A 400:5 CT has 2.5 A on the secondary. What primary current is flowing?","100 A|150 A|200 A|400 A",2,"The CT ratio is 80:1. 2.5 A × 80 = 200 A.","CT ratio calculation"),
            q("A — Common Occupational Skills","What is the usual ladder setup ratio for a straight/extension ladder?","1:2|1:3|1:4|1:6",2,"A common safe setup is about 1 unit out for every 4 units of vertical height.","Occupational safety"),
            q("A — Common Occupational Skills","A VFD uses a 4–20 mA speed command for 0–1800 rpm. What signal represents 1200 rpm?","10.7 mA|12.0 mA|14.7 mA|16.0 mA",2,"1200/1800 = 0.6667. 0.6667 × 16 mA = 10.67 mA above live zero; +4 mA = 14.67 mA.","Controls / 4–20 mA scaling"),
            q("A — Common Occupational Skills","Which document should be checked before using a piece of electrical equipment in a way that may differ from normal installation practice?","Only the panel schedule|Manufacturer instructions and equipment markings|Only the single-line diagram|Only the purchase order",1,"Approval, markings, ratings and manufacturer instructions are part of determining suitable use.","General installation practice"),

            q("B — Generating, Distribution and Service Systems","A 3-phase 75 kVA transformer has a 600 V primary. Approximately what is the primary full-load current?","48 A|72 A|96 A|125 A",1,"I = 75,000 /(√3 × 600) ≈ 72.2 A.","Transformer calculation"),
            q("B — Generating, Distribution and Service Systems","For a 3-phase load, which factor appears in the line-current power relationship?","0.5|0.707|0.866 / √3 relationship|2.0",2,"Three-phase calculations use √3; 0.866 is √3/2 and commonly appears in rearranged trade formulas.","Three-phase calculations"),
            q("B — Generating, Distribution and Service Systems","Which service-calculation section would you use for a hospital occupancy in the app?","Residential|Apartment|Hospital|Welder",2,"Hospital demand calculations are treated separately from dwelling and other occupancy calculations.","CEC 2024 Section 8 study workflow"),
            q("B — Generating, Distribution and Service Systems","A transformer has 3% impedance. Ignoring upstream impedance, the approximate secondary fault current at its terminals is full-load current multiplied by what?","3|10|33.3|100",2,"Available fault current ≈ FLA / Zpu = FLA / 0.03 ≈ 33.3 × FLA.","Transformer impedance / fault current"),
            q("B — Generating, Distribution and Service Systems","In an open-delta bank made from two equal transformers, the bank kVA is approximately what percentage of the original closed-delta bank rating?","50%|57.7%|66.7%|86.6%",1,"Open-delta capacity is approximately 57.7% of the equivalent closed-delta bank capacity.","Transformer banks"),

            q("C — Wiring Systems","A 24 V fire-alarm circuit has a 3.9 kΩ end-of-line resistor. What current flows?","0.0006 A|0.0062 A|0.062 A|6.2 A",1,"I = 24/3900 ≈ 0.00615 A, or about 6.15 mA.","Ohm's law / EOL circuit"),
            q("C — Wiring Systems","Which table family is commonly used for raceway fill dimensions in CEC study problems?","Table 2 only|Table 9 series|Table 44 only|Table 65 only",1,"The Table 9 series is used for raceway dimensions/fill-related work depending on raceway type.","CEC 2024 Table 9 series"),
            q("C — Wiring Systems","A 347/600 V lighting circuit neutral was not counted as a current-carrying conductor in the specific balanced linear-load example discussed in the study bank. What should you do on a real installation?","Always ignore every neutral|Always count every neutral|Apply the actual CEC neutral/current-carrying-conductor rule for the load|Remove the neutral",2,"Neutral treatment depends on the circuit and load characteristics; verify the actual rule rather than applying a blanket shortcut.","CEC conductor grouping study note"),
            q("C — Wiring Systems","When calculating a two-bend offset, the distance between marks is the offset height multiplied by what?","Bend multiplier|Conductor K factor|Power factor|Transformer ratio",0,"Offset spacing = desired offset × the multiplier for the chosen bend angle.","Conduit bending"),
            q("C — Wiring Systems","What is the purpose of an expansion fitting in a long PVC raceway run?","Increase conductor ampacity|Accommodate thermal movement|Reduce fault current|Increase box fill",1,"PVC changes length with temperature; expansion fittings accommodate that movement where required.","PVC raceway installation"),

            q("D — Motors and Control Systems","To reverse a 3-phase induction motor, what is normally done?","Reverse all three leads|Swap any two phase leads|Swap neutral and bond|Increase overload setting",1,"Swapping any two phase conductors reverses phase sequence and motor direction.","Motor controls"),
            q("D — Motors and Control Systems","A wye-delta starter applies approximately what percentage of direct-on-line starting voltage to each winding during wye starting?","33%|50%|58%|100%",2,"Each winding sees about 1/√3 = 57.7% of line voltage during wye starting.","Motor starting"),
            q("D — Motors and Control Systems","Which control method is generally best for adjustable motor speed when a suitable AC motor is used?","Fixed resistor|VFD|Fuse size change|Swap two leads repeatedly",1,"A variable-frequency drive provides controlled frequency/voltage for adjustable AC motor speed.","VFD application"),
            q("D — Motors and Control Systems","In a standard 3-wire motor control circuit, what happens when the STOP pushbutton opens the control circuit?","Motor speeds up|Motor stops|Motor reverses|Overload is bypassed",1,"Opening the normally closed stop path de-energizes the starter coil and stops the motor.","3-wire control"),
            q("D — Motors and Control Systems","A shunt DC motor generally has what speed characteristic compared with a series DC motor?","More nearly constant speed|Always zero starting torque|No commutator|Cannot be speed controlled",0,"Shunt motors are known for relatively constant speed; series motors have high starting torque and larger speed variation.","DC machines"),

            q("E — Signalling and Communication Systems","What is the approximate current through a 3.9 kΩ EOL resistor on a 24 V signalling circuit?","0.6 mA|6.2 mA|62 mA|620 mA",1,"24/3900 ≈ 0.00615 A = 6.15 mA.","Fire alarm / signalling"),
            q("E — Signalling and Communication Systems","A Class A fire-alarm circuit differs from a Class B circuit primarily because Class A wiring is arranged to provide what?","Higher voltage only|A return path / improved survivability after a single open|No end-of-line supervision|No fault monitoring",1,"Class A topology returns to the source/control unit so a single open does not necessarily remove all devices beyond the break.","Fire alarm circuit topology"),
            q("E — Signalling and Communication Systems","When optical fibre and electrical Class 2 conductors share pathways or spaces, what should be checked?","Only fibre colour|CEC separation and cable-rating requirements|Only conductor ampacity|Motor overload size",1,"Communications/fibre and power/control conductors have separation and suitability requirements that must be verified.","CEC communications / Class 2 study"),
            q("E — Signalling and Communication Systems","For fire-alarm output circuits such as NACs, the final conductor and fuse limits should be based on what?","Any convenient fuse size|Listed control-unit/power-supply ratings plus Code requirements|Only the building service size|Only conductor colour",1,"Listed equipment ratings and manufacturer instructions are critical for signalling outputs in addition to Code requirements.","Fire alarm equipment"),
            q("E — Signalling and Communication Systems","What does an end-of-line device primarily help a supervised signalling circuit detect?","Motor direction|Open-circuit wiring faults|Transformer polarity only|Power factor",1,"The EOL device allows the control equipment to supervise field wiring continuity and detect opens/fault conditions.","Supervised signalling circuits")
        );
    }

    public static List<Question> daily25(LocalDate date, String username) {
        Map<String,List<Question>> by = new LinkedHashMap<>();
        for (Question q : all()) by.computeIfAbsent(q.category(), k -> new ArrayList<>()).add(q);
        long seed = Objects.hash(date, username == null ? "" : username);
        Random random = new Random(seed);
        List<Question> out = new ArrayList<>();
        for (List<Question> list : by.values()) {
            List<Question> copy = new ArrayList<>(list);
            Collections.shuffle(copy, random);
            out.addAll(copy.subList(0, Math.min(5, copy.size())));
        }
        return out;
    }

    private static Question q(String category, String question, String options, int correct, String explanation, String reference) {
        return new Question(category, question, List.of(options.split("\\|")), correct, explanation, reference);
    }
}
