package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

@SpringBootTest(properties={
    "studio.admin-password=Admin-Test-Pass-2026",
    "studio.viewer-password=Viewer-Test-Pass-2026",
    "spring.datasource.url=jdbc:h2:mem:studio-device-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class WorkstationSecurityTest {
    @Autowired ScreenApi screens;
    @Autowired MockMvc mvc;

    @Test void workstationSecretsRestrictLayoutsAndTelemetry() throws Exception {
        screens.createScreen(new ScreenApi.NewScreen("motorroom",
            new ScreenLayout("Motor room",1,List.of(
                new ScreenLayout.Widget("motor","VALUE","Motor current","motorCurrent",null,null)))));
        var a=screens.provision(new ScreenApi.NewWorkstation("operatorpc","Operator PC","overview"));
        var b=screens.provision(new ScreenApi.NewWorkstation("engineerpc","Engineer PC","motorroom"));
        mvc.perform(get("/api/studio/client-screen").with(httpBasic("studio-viewer","Viewer-Test-Pass-2026"))
                .header("X-Studio-Device-Id","operatorpc").header("X-Studio-Device-Token",a.oneTimeToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.screenId").value("overview"));
        mvc.perform(get("/api/studio/client-screen").with(httpBasic("studio-viewer","Viewer-Test-Pass-2026"))
                .header("X-Studio-Device-Id","engineerpc").header("X-Studio-Device-Token",b.oneTimeToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.screenId").value("motorroom"));
        mvc.perform(get("/api/studio/client-screen").with(httpBasic("studio-viewer","Viewer-Test-Pass-2026"))
                .header("X-Studio-Device-Id","engineerpc").header("X-Studio-Device-Token",a.oneTimeToken()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/studio/screens").with(httpBasic("studio-viewer","Viewer-Test-Pass-2026")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/studio/screens").with(httpBasic("studio-admin","Admin-Test-Pass-2026")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/studio/client-live").with(httpBasic("studio-viewer","Viewer-Test-Pass-2026"))
                .header("X-Studio-Device-Id","engineerpc").header("X-Studio-Device-Token",b.oneTimeToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.samples.length()").value(1))
            .andExpect(jsonPath("$.samples[0].id").value("motorCurrent"));
        screens.updateWorkstation("operatorpc",new ScreenApi.ChangeWorkstation("Operator PC","motorroom",true));
        mvc.perform(get("/api/studio/client-screen").with(httpBasic("studio-viewer","Viewer-Test-Pass-2026"))
                .header("X-Studio-Device-Id","operatorpc").header("X-Studio-Device-Token",a.oneTimeToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.screenId").value("motorroom"));
        screens.updateWorkstation("operatorpc",new ScreenApi.ChangeWorkstation("Operator PC","motorroom",false));
        mvc.perform(get("/api/studio/client-screen").with(httpBasic("studio-viewer","Viewer-Test-Pass-2026"))
                .header("X-Studio-Device-Id","operatorpc").header("X-Studio-Device-Token",a.oneTimeToken()))
            .andExpect(status().isForbidden());
    }
}
