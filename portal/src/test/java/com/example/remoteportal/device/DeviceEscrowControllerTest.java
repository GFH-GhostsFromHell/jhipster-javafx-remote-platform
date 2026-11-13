package com.example.remoteportal.device;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceEscrowControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DeviceEscrowController controller = new DeviceEscrowController(jdbc);

    private UsernamePasswordAuthenticationToken client() {
        return new UsernamePasswordAuthenticationToken("owner", "hidden",
            List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    @Test void refusesDepositWithoutIndependentOfflineBackup() {
        var input = new DeviceEscrowController.EscrowInput("sample-file",
            "v1.AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAA.ZmFrZS10YWc=", false);
        ResponseStatusException response = assertThrows(ResponseStatusException.class,
            () -> controller.deposit(client(), input));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void operatorDecisionRequiresAdminRole() {
        ResponseStatusException response = assertThrows(ResponseStatusException.class,
            () -> controller.decide(client(), "e9058004-2c8e-4565-8f70-80fa10f254c7", "approve"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void ownerCanOnlySeeOwnEntryList() {
        when(jdbc.queryForList(anyString(), eq("owner"))).thenReturn(List.of());
        assertTrue(controller.entries(client()).isEmpty());
        verify(jdbc).queryForList(contains("WHERE owner_username=?"), eq("owner"));
    }
}
