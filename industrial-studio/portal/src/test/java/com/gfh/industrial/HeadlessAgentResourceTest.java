package com.gfh.industrial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "studio.admin-password=Headless-Admin-Pass-2026",
    "studio.viewer-password=Headless-Viewer-Pass-2026",
    "spring.datasource.url=jdbc:h2:mem:studio-headless-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class HeadlessAgentResourceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private static final String ID="test-headless-pump";
    private static final String URL="/api/studio/headless-agents";
    private static final String CONFIG="""
        {"id":"test-headless-pump","configuration":{
          "name":"Pump test collector","sourceMode":"SIMULATION","enabled":true,
          "tags":[{"id":"tankLevel","label":"Tank level","nodeId":"ns=2;s=Channel1.Device1.TankLevel","unit":"%"}],
          "script":"SIM_WRITE desiredSpeed = CLAMP(tankLevel * 2 + 0, 0, 100)"
        }}
        """;

    @Test void enrollmentAuthenticatesOnlyAssignedCollectorAndNeverWritesPlc() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(httpBasic("studio-viewer","Headless-Viewer-Pass-2026")))
            .andExpect(status().isForbidden());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(CONFIG))
            .andExpect(status().isUnauthorized());
        var created=mvc.perform(post(URL)
            .with(httpBasic("studio-admin","Headless-Admin-Pass-2026"))
            .contentType(MediaType.APPLICATION_JSON).content(CONFIG))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.agent.configuration.sourceMode").value("SIMULATION"))
            .andExpect(jsonPath("$.warning").exists())
            .andReturn().getResponse().getContentAsString();
        JsonNode enrollment=json.readTree(created);
        String token=enrollment.path("oneTimeToken").asText();
        assertEquals(43,token.length());

        mvc.perform(get(URL).with(httpBasic("studio-admin","Headless-Admin-Pass-2026")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tokenHash").doesNotExist())
            .andExpect(jsonPath("$[0].oneTimeToken").doesNotExist());

        String endpoint="/api/studio/edge-clients/"+ID;
        mvc.perform(get(endpoint+"/config"))
            .andExpect(status().isForbidden());
        mvc.perform(get(endpoint+"/config")
                .header("X-Studio-Edge-Token","invalid"))
            .andExpect(status().isForbidden());
        mvc.perform(get(endpoint+"/config")
                .header("X-Studio-Edge-Token",token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plcWritesEnabled").value(false));

        mvc.perform(post(endpoint+"/samples")
            .header("X-Studio-Edge-Token",token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"configurationRevision":1,
                 "samples":[{"id":"tankLevel","value":75.0,"quality":"SIMULATED"}]}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED_DRY_RUN"))
            .andExpect(jsonPath("$.plcWritesPerformed").value(false))
            .andExpect(jsonPath("$.simulatedWriteProposals").value(1));

        mvc.perform(get(URL).with(httpBasic("studio-admin","Headless-Admin-Pass-2026")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].proposedWrites[0].value").value(100d))
            .andExpect(jsonPath("$[0].proposedWrites[0].mode").value("DRY_RUN_ONLY"));

        mvc.perform(post(endpoint+"/samples").header("X-Studio-Edge-Token",token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"configurationRevision":1,
                 "samples":[{"id":"tankLevel","value":75.0,"quality":"GOOD"}]}
                """))
            .andExpect(status().isBadRequest());

        var replacement=mvc.perform(post(URL+"/"+ID+"/rotate-token")
                .with(httpBasic("studio-admin","Headless-Admin-Pass-2026")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String newToken=json.readTree(replacement).path("oneTimeToken").asText();
        assertNotEquals(token,newToken);
        mvc.perform(get(endpoint+"/config").header("X-Studio-Edge-Token",token))
            .andExpect(status().isForbidden());
        mvc.perform(get(endpoint+"/config").header("X-Studio-Edge-Token",newToken))
            .andExpect(status().isOk());
    }

    @Test void rejectedCloudScriptsCannotCreateCollector() throws Exception {
        mvc.perform(post(URL)
            .with(httpBasic("studio-admin","Headless-Admin-Pass-2026"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(CONFIG.replace(
                "SIM_WRITE desiredSpeed = CLAMP(tankLevel * 2 + 0, 0, 100)",
                "java.lang.Runtime.getRuntime().exec('powershell')")))
            .andExpect(status().isBadRequest());
    }
}
