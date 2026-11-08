package com.example.remoteportal.web;

import com.example.remoteportal.service.AccountLifecycleService;
import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.MailDeliveryService;
import com.example.remoteportal.service.PortalStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSyncAndRegistrationValidationTest {
    @Test void accountRegistrationRejectsWeakOrInvalidDetailsWithoutDatabaseWrites() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        MailDeliveryService mail=mock(MailDeliveryService.class);
        when(mail.configured()).thenReturn(true);
        AccountLifecycleService accounts=new AccountLifecycleService(db,
            mock(PasswordEncoder.class),mail,mock(ClientAccountStore.class));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.beginRegistration("bad space","Worker","worker@example.com","a-secure-password","TIER_1"));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.beginRegistration("worker","Worker","not-an-email","a-secure-password","TIER_1"));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.beginRegistration("worker","Worker","worker@example.com","123","TIER_1"));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.beginPasswordReset(""));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.completePasswordReset("worker","123456","too"));
        verifyNoInteractions(db);
        verify(mail,never()).sendVerificationCode(anyString(),anyString(),anyString());
        verify(mail,never()).sendPasswordResetCode(anyString(),anyString(),anyString());
    }

    @Test void verificationCodesAreNotAcceptedWithoutExactlySixDigits() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        MailDeliveryService mail=mock(MailDeliveryService.class);
        AccountLifecycleService accounts=new AccountLifecycleService(db,
            mock(PasswordEncoder.class),mail,mock(ClientAccountStore.class));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.verifyRegistration("worker","12"));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.verifyRegistration("worker","abcdef"));
        assertThrows(IllegalArgumentException.class,
            ()->accounts.verifyRegistration("","123456"));
    }

    @Test void accountSyncRefusesUnknownBucketsDuplicateIDsAndOversizedBatches() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        ObjectMapper mapper=new ObjectMapper();
        AccountSyncResource endpoint=new AccountSyncResource(db,mapper);
        Principal worker=()->"worker";
        ResponseStatusException noBucket=assertThrows(ResponseStatusException.class,
            ()->endpoint.list("otherUsers",worker));
        assertEquals(HttpStatus.BAD_REQUEST,noBucket.getStatusCode());
        var one=new AccountSyncResource.SyncItem("entry-1",0L,
            mapper.createObjectNode().put("kind","circuitWorksheet"),false);
        var duplicates=new AccountSyncResource.BatchRequest(List.of(one,one));
        assertEquals(HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class,
                ()->endpoint.push("designReports",duplicates,worker)).getStatusCode());
        var negative=new AccountSyncResource.BatchRequest(List.of(
            new AccountSyncResource.SyncItem("entry-1",-1L,mapper.createObjectNode(),false)));
        assertEquals(HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class,
                ()->endpoint.push("designReports",negative,worker)).getStatusCode());
        var invalidName=new AccountSyncResource.BatchRequest(List.of(
            new AccountSyncResource.SyncItem("../../escape",0L,mapper.createObjectNode(),false)));
        assertEquals(HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class,
                ()->endpoint.push("designReports",invalidName,worker)).getStatusCode());
        var oversize=new ArrayList<AccountSyncResource.SyncItem>();
        for(int i=0;i<201;i++)
            oversize.add(new AccountSyncResource.SyncItem("entry-"+i,0L,mapper.createObjectNode(),false));
        assertEquals(HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class,
                ()->endpoint.push("designReports",new AccountSyncResource.BatchRequest(oversize),worker)).getStatusCode());
    }

    @Test void subscriptionFeaturesRespectFourTiers() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        ClientAccountStore accounts=mock(ClientAccountStore.class);
        PortalStore store=new PortalStore(db,accounts);
        for(String tier:List.of("TIER_1","TIER_2","TIER_3","TIER_4")){
            when(accounts.tier("worker")).thenReturn(tier);
            Map<String,Boolean> result=store.effective("worker");
            assertEquals(true,result.get("ELECTRICAL_CALCULATORS"));
            assertEquals(!"TIER_1".equals(tier),result.get("ENGINEERING_POWER_SYSTEMS"));
            assertEquals("TIER_3".equals(tier)||"TIER_4".equals(tier),
                result.get("ENGINEERING_MODE"));
            assertEquals("TIER_4".equals(tier),
                result.get("ENTERPRISE_TEAM_MANAGEMENT"));
            assertEquals(false,result.get("ADMIN_TOOLS"));
        }
    }
}
