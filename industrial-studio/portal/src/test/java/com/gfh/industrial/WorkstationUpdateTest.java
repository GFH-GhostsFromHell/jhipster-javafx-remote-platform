package com.gfh.industrial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "studio.admin-password=Admin-Test-Pass-2026",
    "studio.viewer-password=Viewer-Test-Pass-2026",
    "spring.datasource.url=jdbc:h2:mem:studio-config-update-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class WorkstationUpdateTest {
    @Autowired MockMvc mvc;
    @Autowired ScreenApi screens;
    @Autowired StudioApi studio;
    @Autowired ObjectMapper json;

    private JsonNode deviceStatus(String id,String secret) throws Exception {
        return json.readTree(mvc.perform(get("/api/studio/client-update-status")
            .header("X-Studio-Device-Id",id).header("X-Studio-Device-Token",secret))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void acknowledge(String id,String token,String fingerprint) throws Exception {
        mvc.perform(post("/api/studio/client-update-ack")
            .header("X-Studio-Device-Id",id).header("X-Studio-Device-Token",token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new WorkstationUpdateResource.UpdateAck(fingerprint))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatePending").value(false));
    }

    @Test void changesQueueUntilExplicitlyAppliedAndClientAcknowledges() throws Exception {
        var a=screens.provision(new ScreenApi.NewWorkstation("updatepc1","Panel 1","overview"));
        var b=screens.provision(new ScreenApi.NewWorkstation("updatepc2","Panel 2","overview"));

        mvc.perform(get("/api/studio/workstations/update-status"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/studio/workstations/update-status")
            .with(httpBasic("studio-viewer","Viewer-Test-Pass-2026")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/studio/workstations/updatepc1/request-update")
            .with(httpBasic("studio-viewer","Viewer-Test-Pass-2026")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/studio/client-update-status"))
            .andExpect(status().isForbidden());

        String original=deviceStatus("updatepc1",a.oneTimeToken()).path("fingerprint").asText();
        assertTrue(deviceStatus("updatepc1",a.oneTimeToken()).path("updatePending").asBoolean());
        mvc.perform(get("/api/studio/client-update-status")
            .header("X-Studio-Device-Id","updatepc2")
            .header("X-Studio-Device-Token",a.oneTimeToken()))
            .andExpect(status().isForbidden());
        acknowledge("updatepc1",a.oneTimeToken(),original);
        assertFalse(deviceStatus("updatepc1",a.oneTimeToken()).path("updatePending").asBoolean());

        // Admin refresh request applies to a selected PC only.
        mvc.perform(post("/api/studio/workstations/updatepc1/request-update")
            .with(httpBasic("studio-admin","Admin-Test-Pass-2026")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatePending").value(true));
        String forced=deviceStatus("updatepc1",a.oneTimeToken()).path("fingerprint").asText();
        assertNotEquals(original,forced);
        assertTrue(deviceStatus("updatepc2",b.oneTimeToken()).path("updatePending").asBoolean());
        acknowledge("updatepc1",a.oneTimeToken(),forced);

        // Saving a screen creates an update for every PC assigned to it.
        var example=ScreenLayout.example();
        screens.saveScreen("overview",new ScreenApi.ChangeScreen(1,
            new ScreenLayout("New pump overview",example.columns(),example.widgets())));
        String revised=deviceStatus("updatepc1",a.oneTimeToken()).path("fingerprint").asText();
        assertNotEquals(forced,revised);
        assertTrue(deviceStatus("updatepc1",a.oneTimeToken()).path("updatePending").asBoolean());

        // A client cannot acknowledge a stale revision.
        mvc.perform(post("/api/studio/client-update-ack")
            .header("X-Studio-Device-Id","updatepc1")
            .header("X-Studio-Device-Token",a.oneTimeToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new WorkstationUpdateResource.UpdateAck(forced))))
            .andExpect(status().isConflict());
        acknowledge("updatepc1",a.oneTimeToken(),revised);

        // A global tag mapping/config save also queues a new update.
        var current=studio.getConfig();
        studio.putConfig(new StudioApi.Update(current.revision(),current.configuration()));
        assertTrue(deviceStatus("updatepc1",a.oneTimeToken()).path("updatePending").asBoolean());

        // Disabling a workstation revokes its ability to see updates.
        screens.updateWorkstation("updatepc1",new ScreenApi.ChangeWorkstation("Panel 1","overview",false));
        mvc.perform(get("/api/studio/client-update-status")
            .header("X-Studio-Device-Id","updatepc1")
            .header("X-Studio-Device-Token",a.oneTimeToken()))
            .andExpect(status().isForbidden());
    }
}
