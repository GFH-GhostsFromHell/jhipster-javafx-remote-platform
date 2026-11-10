package com.gfh.industrial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "studio.admin-password=Admin-Test-Pass-2026",
    "studio.viewer-password=Viewer-Test-Pass-2026",
    "spring.datasource.url=jdbc:h2:mem:studio-pair-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class WorkstationPairingTest {
    @Autowired MockMvc mvc;
    @Autowired ScreenApi screens;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;

    @Test void setupCodeIsOneTimeAdminIssuedAndAllowsScopedReadsWithoutViewerPassword() throws Exception {
        screens.provision(new ScreenApi.NewWorkstation("pairtestpc","Pair Test PC","overview"));
        mvc.perform(post("/api/studio/workstations/pairtestpc/pairing"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/studio/workstations/pairtestpc/pairing")
            .with(httpBasic("studio-viewer","Viewer-Test-Pass-2026")))
            .andExpect(status().isForbidden());

        var offerResponse=mvc.perform(post("/api/studio/workstations/pairtestpc/pairing")
            .with(httpBasic("studio-admin","Admin-Test-Pass-2026")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
            .andReturn().getResponse();
        JsonNode offer=json.readTree(offerResponse.getContentAsString());
        String setupCode=offer.path("setupCode").asText();
        assertTrue(setupCode.startsWith("ICS1|http://127.0.0.1:8090|pairtestpc|"));
        String secret=setupCode.split("\\|",4)[3];
        assertEquals(43,secret.length());

        String request=json.writeValueAsString(new WorkstationPairingResource.PairingRequest("pairtestpc",secret));
        var paired=mvc.perform(post("/api/studio/pair").contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isOk())
            .andReturn().getResponse();
        JsonNode auth=json.readTree(paired.getContentAsString());
        String deviceToken=auth.path("deviceSecret").asText();
        assertEquals(43,deviceToken.length());
        assertEquals("pairtestpc",auth.path("workstationId").asText());
        assertFalse(deviceToken.equals(secret));

        mvc.perform(get("/api/studio/client-screen")
                .header("X-Studio-Device-Id","pairtestpc")
                .header("X-Studio-Device-Token",deviceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.screenId").value("overview"));
        mvc.perform(get("/api/studio/client-live")
                .header("X-Studio-Device-Id","pairtestpc")
                .header("X-Studio-Device-Token",deviceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("SIMULATION"));
        mvc.perform(get("/api/studio/client-live"))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/studio/pair").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/studio/pair").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new WorkstationPairingResource.PairingRequest("otherpc",secret))))
            .andExpect(status().isForbidden());
    }

    @Test void revokedAndExpiredPairingCodesCannotBeUsed() throws Exception {
        screens.provision(new ScreenApi.NewWorkstation("expiredpc","Expired PC","overview"));
        var first=json.readTree(mvc.perform(post("/api/studio/workstations/expiredpc/pairing")
            .with(httpBasic("studio-admin","Admin-Test-Pass-2026")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String firstSecret=first.path("setupCode").asText().split("\\|",4)[3];
        var second=json.readTree(mvc.perform(post("/api/studio/workstations/expiredpc/pairing")
            .with(httpBasic("studio-admin","Admin-Test-Pass-2026")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String secondSecret=second.path("setupCode").asText().split("\\|",4)[3];
        mvc.perform(post("/api/studio/pair").contentType(MediaType.APPLICATION_JSON)
             .content(json.writeValueAsString(new WorkstationPairingResource.PairingRequest("expiredpc",firstSecret))))
            .andExpect(status().isForbidden());
        db.update("update studio_pairing set expires_at=? where device_id=?",
            Timestamp.from(Instant.now().minusSeconds(10)),"expiredpc");
        mvc.perform(post("/api/studio/pair").contentType(MediaType.APPLICATION_JSON)
             .content(json.writeValueAsString(new WorkstationPairingResource.PairingRequest("expiredpc",secondSecret))))
            .andExpect(status().isForbidden());
    }
}
