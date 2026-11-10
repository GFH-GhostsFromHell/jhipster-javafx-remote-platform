package com.gfh.industrial;

import java.util.*;
import java.util.regex.*;

/** Bounded, declarative math for the experimental edge-client dry run.
 * No user-provided JavaScript, Java bytecode, shells, reflection or OPC UA writes.
 * SIM_WRITE is never an executable PLC command.
 */
public final class HeadlessLogic {
    private HeadlessLogic() {}

    public record Proposal(String outputTag, String inputTag, double value,
                           String mode, String quality) {}
    public record Rule(String output, String input, double scale, double offset,
                       double minimum, double maximum) {}
    private static final String NUMBER = "[-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)";
    private static final String ID = "[A-Za-z][A-Za-z0-9_]{0,49}";
    private static final Pattern LINE = Pattern.compile(
        "^SIM_WRITE\\s+("+ID+")\\s*=\\s*CLAMP\\(\\s*("+ID+")\\s*\\*\\s*("+NUMBER+")"+
        "\\s*\\+\\s*("+NUMBER+")\\s*,\\s*("+NUMBER+")\\s*,\\s*("+NUMBER+")\\s*\\)$");

    public static List<Rule> compile(String script, Set<String> inputs) {
        if(script==null || script.length()>8000) throw new IllegalArgumentException("Rules exceed 8,000 characters");
        List<Rule> rules=new ArrayList<>();
        Set<String> outputIds=new HashSet<>();
        for(String line:script.split("\\R",-1)) {
            line=line.trim();
            if(line.isEmpty() || line.startsWith("#"))continue;
            if(line.length()>250)throw new IllegalArgumentException("Rule line exceeds 250 characters");
            Matcher match=LINE.matcher(line);
            if(!match.matches())throw new IllegalArgumentException(
                "Use SIM_WRITE output = CLAMP(input * scale + offset, min, max)");
            String output=match.group(1),input=match.group(2);
            if(!inputs.contains(input))throw new IllegalArgumentException("Unknown input tag: "+input);
            if(!outputIds.add(output))throw new IllegalArgumentException("Repeated output: "+output);
            if(inputs.contains(output))throw new IllegalArgumentException("Rule cannot target a source tag");
            double scale=Double.parseDouble(match.group(3));
            double offset=Double.parseDouble(match.group(4));
            double low=Double.parseDouble(match.group(5));
            double high=Double.parseDouble(match.group(6));
            if(!Double.isFinite(scale)||!Double.isFinite(offset)||!Double.isFinite(low)||
                !Double.isFinite(high)||low>=high||Math.abs(scale)>10000||
                Math.abs(offset)>1_000_000||Math.abs(low)>1_000_000||Math.abs(high)>1_000_000)
                throw new IllegalArgumentException("Non-finite, unbounded or reversed rule limits");
            rules.add(new Rule(output,input,scale,offset,low,high));
            if(rules.size()>20)throw new IllegalArgumentException("Maximum 20 output rules per agent");
        }
        return List.copyOf(rules);
    }

    public static List<Proposal> preview(List<Rule> rules, Map<String,Double> inputs) {
        List<Proposal> proposals=new ArrayList<>();
        for(Rule rule:rules) {
            Double value=inputs.get(rule.input());
            if(value==null||!Double.isFinite(value))continue; // no stale-value fallback
            double raw=value*rule.scale()+rule.offset();
            if(!Double.isFinite(raw))continue;
            double bounded=Math.max(rule.minimum(),Math.min(rule.maximum(),raw));
            proposals.add(new Proposal(rule.output(),rule.input(),bounded,
                "DRY_RUN_ONLY","SIMULATED_WRITE_NOT_SENT"));
        }
        return List.copyOf(proposals);
    }
}
