package com.example.remoteportal.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regulatory provenance only. No CSA Code tables are copied or certified here.
 *
 * Ontario O. Reg. 164/99, as amended by O. Reg. 449/24, adopts CSA C22.1:24
 * and Ontario amendments (dated June 12, 2024), effective May 1, 2025.
 * See https://www.ontario.ca/laws/regulation/990164
 * and https://esasafe.com/role/oesc/
 */
public final class ElectricalCodeReferences {
    public static final String PROFILE_ID = "OESC_ON_2024";
    public static final String EDITION = "CEC_2024";
    public static final String DATASET_REVISION = "starter-3";
    public static final String UNVERIFIED = "AUTHORIZED_TABLE_VERIFICATION_REQUIRED";
    private ElectricalCodeReferences() {}

    public static Map<String, Object> provenance() {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("referenceProfileId", PROFILE_ID);
        r.put("codeEdition", EDITION);
        r.put("jurisdiction", "Ontario, Canada");
        r.put("cecDesignation", "CSA C22.1:24");
        r.put("ontarioCodeEffectiveDate", "2025-05-01");
        r.put("amendmentReference", "Ontario amendments referenced in O. Reg. 449/24, dated 2024-06-12");
        r.put("regulationUrl", "https://www.ontario.ca/laws/regulation/990164");
        r.put("electricalSafetyAuthorityUrl", "https://esasafe.com/role/oesc/");
        r.put("regulatoryAdoptionVerified", true);
        r.put("numericDatasetRevision", DATASET_REVISION);
        r.put("numericDatasetVerificationStatus", UNVERIFIED);
        r.put("numericDatasetVerified", false);
        r.put("codeComplianceVerified", false);
        r.put("note", "Regulatory adoption is confirmed, but embedded table values, calculation assumptions and equipment-specific rules have NOT been checked against a licensed current Ontario Electrical Safety Code copy. A passed arithmetic check is not field approval.");
        return r;
    }

    private static Map<String,Object> table(String id,String description, String status) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",id);
        m.put("purpose",description);
        m.put("sourceEdition",EDITION);
        m.put("verificationStatus",status);
        m.put("licensedRowByRowReviewComplete",false);
        m.put("verifiedBy",null);
        m.put("verifiedAt",null);
        m.put("hasApprovedNumericalDataset",false);
        return m;
    }

    public static Map<String,Object> catalog() {
        Map<String,Object> out = new LinkedHashMap<>(provenance());
        List<Map<String,Object>> tables = new ArrayList<>();
        tables.add(table("TABLE_1", "Copper conductors in free air; confirm installation", UNVERIFIED));
        tables.add(table("TABLE_2", "Copper conductors in raceway/cable; confirm installation", UNVERIFIED));
        tables.add(table("TABLE_3", "Aluminum conductors in free air; confirm installation", UNVERIFIED));
        tables.add(table("TABLE_4", "Aluminum conductors in raceway/cable; confirm installation", UNVERIFIED));
        tables.add(table("TABLE_5A", "Ambient-temperature correction examples; applicability to be checked", UNVERIFIED));
        tables.add(table("TABLE_5C", "Current-carrying-conductor group correction examples; applicability to be checked", UNVERIFIED));
        tables.add(table("TABLE_13", "Overcurrent device standard ratings: worksheet data not independently certified", UNVERIFIED));
        tables.add(table("TABLE_29", "Motor protection factors: no certified embedded table dataset", UNVERIFIED));
        out.put("tables", List.copyOf(tables));
        out.put("auditRequired", List.of(
            "Check each numerical row against an authorized current OESC/CEC reference with appropriate distribution rights.",
            "Record reviewed edition, table, insulation, material, wiring method, correction factors and equipment markings.",
            "Record verifier, date and source revision separately from the app version.",
            "Preserve regression examples, including failure cases, on every platform before a data status change."
        ));
        return out;
    }
}
