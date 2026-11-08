package com.example.remoteportal.web;

import com.example.remoteportal.service.AccountLifecycleService;
import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.PortalStore;
import com.example.remoteportal.service.TierCheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionAccountSafetyTest {
    private static Principal client(String name) { return () -> name; }
    private static UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken("root-admin","",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test void accountLookupFailureNeverGrantsNormalUserAnAdminProfile() {
        ClientAccountStore accounts=mock(ClientAccountStore.class);
        PortalStore portal=mock(PortalStore.class);
        when(accounts.get("worker")).thenThrow(new UsernameNotFoundException("worker"));
        PortalResource endpoint=new PortalResource(portal,accounts,"","");
        ResponseStatusException profile=assertThrows(ResponseStatusException.class,
            ()->endpoint.profile(client("worker")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,profile.getStatusCode());
        ResponseStatusException bootstrap=assertThrows(ResponseStatusException.class,
            ()->endpoint.bootstrap(client("worker")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,bootstrap.getStatusCode());
        verify(portal,never()).effective("worker");
    }

    @Test void inMemoryAdminIsOnlyRecognizedWhenRoleAdminIsActuallyPresent() {
        ClientAccountStore accounts=mock(ClientAccountStore.class);
        PortalResource endpoint=new PortalResource(mock(PortalStore.class),accounts,"","");
        when(accounts.get("root-admin")).thenThrow(new UsernameNotFoundException("root-admin"));
        Map<String,Object> profile=endpoint.profile(admin());
        assertEquals("ADMIN",profile.get("subscriptionTier"));
        assertEquals("root-admin",profile.get("username"));
        doThrow(new IllegalStateException("DB down")).when(accounts).get("root-admin");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows(ResponseStatusException.class,()->endpoint.profile(admin())).getStatusCode());
    }

    @Test void subscriptionLookupFailureCannotReturnAdminForOrdinaryUser() {
        AccountLifecycleService lifecycle=mock(AccountLifecycleService.class);
        ClientAccountStore users=mock(ClientAccountStore.class);
        TierCheckoutService checkout=mock(TierCheckoutService.class);
        PublicAccountResource endpoint=new PublicAccountResource(lifecycle,users,checkout);
        when(checkout.profileOptions("worker")).thenThrow(new IllegalStateException("temporary"));
        when(users.tier("worker")).thenThrow(new IllegalStateException("temporary"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows(ResponseStatusException.class,
                ()->endpoint.billingOptions(client("worker"))).getStatusCode());
        when(checkout.profileOptions("root-admin")).thenThrow(new IllegalStateException("temporary"));
        when(users.tier("root-admin")).thenThrow(new UsernameNotFoundException("root-admin"));
        when(checkout.options()).thenReturn(List.of());
        assertEquals("ADMIN",endpoint.billingOptions(admin()).get("currentTier"));
    }

    @Test void publicBuildVersionContainsNoCredentialsAndNoCodeApprovalClaims() {
        ProductionBuildResource health=new ProductionBuildResource(
            "F2A8B786B1F1539C447E44A7B98137EAC6A13633","remote-javafx-portal");
        Map<String,Object> data=health.buildInfo();
        assertEquals("f2a8b786b1f1539c447e44a7b98137eac6a13633",data.get("commit"));
        assertEquals(true,data.get("commitAvailable"));
        assertEquals(false,data.get("numericDatasetVerified"));
        assertEquals(false,data.get("codeComplianceVerified"));
        assertFalse(data.containsKey("password"));
        assertFalse(data.containsKey("token"));
        assertEquals("unknown",new ProductionBuildResource("not-a-sha","portal").buildInfo().get("commit"));
    }

    @Test void allFourSubscriptionTiersAreSupportedInAccountDropdown() {
        assertEquals(List.of("TIER_1","TIER_2","TIER_3","TIER_4"),
                     ClientAccountStore.TIERS);
    }
}
