package com.example.remoteportal.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirms official Ontario adoption metadata without making unsupported
 * claims about licensed numerical table datasets.
 */
class ElectricalCodeReferencesTest {
    private final ElectricalCalculatorService calc=new ElectricalCalculatorService();
    private static final String E=ElectricalCalculatorService.DEFAULT_EDITION;

    @Test void ontario2024RegulatoryReferenceIsSeparateFromTableVerification() {
        Map<String,Object> refs=ElectricalCodeReferences.catalog();
        assertEquals("OESC_ON_2024",refs.get("referenceProfileId"));
        assertEquals("CEC_2024",refs.get("codeEdition"));
        assertEquals("CSA C22.1:24",refs.get("cecDesignation"));
        assertEquals("2025-05-01",refs.get("ontarioCodeEffectiveDate"));
        assertEquals(true,refs.get("regulatoryAdoptionVerified"));
        assertEquals(false,refs.get("numericDatasetVerified"));
        assertEquals(false,refs.get("codeComplianceVerified"));
        assertEquals(ElectricalCodeReferences.UNVERIFIED,refs.get("numericDatasetVerificationStatus"));
        assertTrue(String.valueOf(refs.get("regulationUrl")).startsWith("https://www.ontario.ca/"));
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> tables=(List<Map<String,Object>>)refs.get("tables");
        assertEquals(8,tables.size());
        assertTrue(tables.stream().allMatch(t->Boolean.FALSE.equals(t.get("licensedRowByRowReviewComplete"))));
        assertTrue(tables.stream().allMatch(t->Boolean.FALSE.equals(t.get("hasApprovedNumericalDataset"))));
        assertTrue(tables.stream().noneMatch(t->t.get("verifiedBy")!=null||t.get("verifiedAt")!=null));
    }

    @Test void editionCatalogDisclosesUnverifiedWorksheetDataset() {
        Map<String,Object> version=calc.editions().getFirst();
        assertEquals("WORKSHEET_ONLY",version.get("status"));
        assertEquals(false,version.get("numericDatasetVerified"));
        assertEquals(ElectricalCodeReferences.PROFILE_ID,version.get("referenceProfileId"));
        Map<String,Object> catalog=calc.catalog("TIER_1");
        assertEquals(ElectricalCodeReferences.UNVERIFIED,catalog.get("numericDatasetVerificationStatus"));
    }

    @Test void everyRepresentativeResultIncludesCodeProvenanceAndDoesNotClaimApproval() {
        List<Map<String,Object>> results=List.of(
            calc.conductorReview(30,55,60,90,0.58,0.8,"TABLE_2",E),
            calc.transformer(75,600,208,3,1.25,1.25,1.25,E),
            calc.motor(34,1.25,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,E),
            calc.conduitFill(100,List.of(10.0),List.of(4),40,E),
            calc.boxFill(200,15,4,2,1,E),
            calc.service(List.of(6000.0,4000.0),List.of(1.0,.75),600,3,10,E)
        );
        for(Map<String,Object> r:results) {
            assertEquals(ElectricalCodeReferences.PROFILE_ID,r.get("referenceProfileId"));
            assertEquals(ElectricalCodeReferences.UNVERIFIED,r.get("numericDatasetVerificationStatus"));
            assertEquals(false,r.get("numericDatasetVerified"));
            assertEquals(false,r.get("codeComplianceVerified"));
        }
    }

    @Test void validBoxRacewayAndServiceArithmeticRemainStable() {
        Map<String,Object> raceway=calc.conduitFill(100,List.of(10.0),List.of(4),40,E);
        assertEquals(4,raceway.get("totalConductors"));
        assertEquals(40.0,((Number)raceway.get("actualFillPercent")).doubleValue(),.001);
        assertEquals(true,raceway.get("passes"));
        Map<String,Object> box=calc.boxFill(200,15,4,2,1,E);
        assertEquals(7,box.get("totalEquivalents"));
        assertEquals(105.0,((Number)box.get("requiredVolume")).doubleValue(),.001);
        assertEquals(true,box.get("passes"));
        Map<String,Object> service=calc.service(
            List.of(6000.0,4000.0),List.of(1.0,.75),600,3,10,E);
        assertEquals(10000.0,((Number)service.get("connectedLoadVA")).doubleValue(),.001);
        assertEquals(9000.0,((Number)service.get("demandLoadVA")).doubleValue(),.001);
        assertEquals(9900.0,((Number)service.get("designLoadVA")).doubleValue(),.001);
        assertEquals(9.53,((Number)service.get("serviceCurrentA")).doubleValue(),.01);
    }

    @Test void racewayFillRejectsBadAreasCountsAndPercent() {
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(10.0),List.of(-1),40,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(10.0),List.of(0),40,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(-10.0),List.of(2),40,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(Double.NaN),List.of(2),40,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(10.0),List.of(2),101,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(10.0),List.of(2),Double.NaN,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(10.0),List.of(2,3),40,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.conduitFill(100,List.of(10.0),List.of(10001),40,E));
    }

    @Test void boxFillRejectsNegativeCountsOrUnusableVolume() {
        assertThrows(IllegalArgumentException.class,
            ()->calc.boxFill(200,15,-1,2,1,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.boxFill(200,15,0,0,0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.boxFill(200,-1,4,2,1,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.boxFill(Double.NaN,15,4,2,1,E));
    }

    @Test void tapWorksheetCannotTreatNegativeLengthsOrInvalidDownstreamDeviceAsOmitted() {
        Map<String,Object> r=calc.tap(225,100,1.0/3,3,100.0,E);
        assertEquals(true,r.get("overallWorksheetPass"));
        assertEquals(false,r.get("lengthRequirementVerified"));
        assertThrows(IllegalArgumentException.class,
            ()->calc.tap(225,100,1.0/3,-3,100.0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.tap(225,100,-.5,3,100.0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.tap(225,100,1.5,3,100.0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.tap(225,100,1.0/3,3,-100.0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.tap(225,100,1.0/3,3,Double.NaN,E));
    }

    @Test void serviceCannotReturnPlausibleAmpsFromNegativeOrNonfiniteLoads() {
        assertThrows(IllegalArgumentException.class,
            ()->calc.service(List.of(-100.0),List.of(1.0),600,3,0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.service(List.of(100.0),List.of(Double.NaN),600,3,0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.service(List.of(100.0),List.of(-.1),600,3,0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.service(List.of(100.0),List.of(1.0),600,2,0,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.service(List.of(100.0),List.of(1.0),600,3,-1,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.service(List.of(100.0),List.of(1.0),600,3,101,E));
        assertThrows(IllegalArgumentException.class,
            ()->calc.service(List.of(100.0),List.of(0.0),600,3,0,E));
    }

    @Test void unsupportedStandardDeviceIsRejectedRatherThanInventingRating() {
        assertThrows(IllegalArgumentException.class,
            ()->calc.welder(7000,1,1,1,1,1,E));
    }
}
