package com.example.remoteportal.device;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceStatusControllerTest {
    private final DeviceStatusRepository repository = mock(DeviceStatusRepository.class);
    private final DeviceStatusController controller = new DeviceStatusController(repository);

    @Test void rejectsUnrecognizedKeyOrTokenFieldsAsStatus() {
        var auth = new UsernamePasswordAuthenticationToken("owner", "test");
        var request = new DeviceStatusController.ReportRequest(UUID.randomUUID().toString(),
            "Owner laptop", "Windows", "C:", "RECOVERY_TOKEN");
        var exception = assertThrows(ResponseStatusException.class, () -> controller.report(auth, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test void returnsOnlyCurrentUsersReports() {
        var auth = new UsernamePasswordAuthenticationToken("owner", "test");
        when(repository.findAllByOwnerUsernameOrderByReportedAtDesc("owner")).thenReturn(List.of());
        assertTrue(controller.list(auth).isEmpty());
        verify(repository).findAllByOwnerUsernameOrderByReportedAtDesc("owner");
    }
}
