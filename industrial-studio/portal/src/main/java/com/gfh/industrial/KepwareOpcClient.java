package com.gfh.industrial;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.util.validation.ValidationCheck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional read-only OPC UA bridge for a private cloud Windows VM hosting Kepware.
 * The connection is always server-side. No OPC TCP endpoint is exposed to JavaFX clients.
 * In SIMULATION mode this worker does not connect anywhere.
 */
@Component
public final class KepwareOpcClient {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final String source;
    private final String endpoint;
    private final String keyStoreFile;
    private final String keyStorePassword;
    private final String trustedCertificate;
    private volatile OpcUaClient client;
    private volatile String state="DISABLED";
    private volatile Instant lastSuccessfulRead;
    private volatile List<StudioApi.Sample> latest = List.of();
    private volatile long lastAttemptMs;

    public KepwareOpcClient(
        JdbcTemplate db,ObjectMapper mapper,
        @Value("${STUDIO_SOURCE_MODE:SIMULATION}") String source,
        @Value("${STUDIO_KEPWARE_ENDPOINT:}") String endpoint,
        @Value("${STUDIO_KEPWARE_CLIENT_PKCS12:}") String keyStoreFile,
        @Value("${STUDIO_KEPWARE_CLIENT_PKCS12_PASSWORD:}") String keyStorePassword,
        @Value("${STUDIO_KEPWARE_SERVER_CERT:}") String trustedCertificate) {
        this.db=db;this.mapper=mapper;
        this.source=source.trim().toUpperCase(Locale.ROOT);
        this.endpoint=endpoint.trim();this.keyStoreFile=keyStoreFile;
        this.keyStorePassword=keyStorePassword;this.trustedCertificate=trustedCertificate;
        if(!Set.of("SIMULATION","KEPWARE").contains(this.source))
            throw new IllegalStateException("STUDIO_SOURCE_MODE must be SIMULATION or KEPWARE");
        if(this.source.equals("KEPWARE") &&
            (!this.endpoint.matches("opc\\.tcp://[A-Za-z0-9_.-]+:[0-9]{2,5}(/[^\\s]*)?")
                || keyStoreFile.isBlank() || trustedCertificate.isBlank() || keyStorePassword.isBlank()))
            throw new IllegalStateException("KEPWARE mode requires endpoint, approved server certificate, client PKCS12 and password");
    }

    public boolean enabled(){return source.equals("KEPWARE");}
    public String mode(){return source;}
    public String state(){return state;}
    public String lastRead(){return lastSuccessfulRead==null?"":lastSuccessfulRead.toString();}
    public List<StudioApi.Sample> readings(){return latest;}

    @Scheduled(fixedDelayString="${STUDIO_OPC_POLL_MS:1500}")
    public synchronized void refresh() {
        if(!enabled())return;
        try{
            ensureConnected();
            // This is only the cloud-demo mapping; real deployments should browse by
            // namespace URI and handle changes to namespace indices.
            List<StudioConfig.Tag> tags=db.query("select document from studio_config where id=1",
                (rs,row)->{
                    try{return mapper.readValue(rs.getString(1),StudioConfig.class).tags();}
                    catch(Exception e){throw new IllegalStateException("Invalid tag configuration",e);}
                }).stream().findFirst().orElse(List.of());
            if(tags.isEmpty()){latest=List.of();state="NO_TAGS";return;}
            List<NodeId> ids=new ArrayList<>();
            for(StudioConfig.Tag tag:tags)ids.add(NodeId.parse(tag.nodeId()));
            List<DataValue> values=client.readValues(0.0,TimestampsToReturn.Both,ids);
            if(values.size()!=tags.size())throw new IllegalStateException("Unexpected OPC UA read count");
            List<StudioApi.Sample> result=new ArrayList<>();
            for(int i=0;i<tags.size();i++){
                var tag=tags.get(i);
                DataValue dv=values.get(i);
                Object raw=dv.value().value();
                boolean good=dv.statusCode().isGood() && raw instanceof Number;
                double number=good?((Number)raw).doubleValue():0d;
                if(!Double.isFinite(number))good=false;
                String quality=good?"GOOD":"BAD";
                boolean alarm=good && ((tag.lowAlarm()!=null && number<tag.lowAlarm()) ||
                    (tag.highAlarm()!=null && number>tag.highAlarm()));
                result.add(new StudioApi.Sample(tag.id(),tag.label(),tag.unit(),good?number:0d,
                    quality,tag.nodeId(),alarm));
            }
            latest=List.copyOf(result);
            lastSuccessfulRead=Instant.now();
            state="CONNECTED";
        }catch(Exception error){
            state="DISCONNECTED";
            latest=List.of();
            closeClient();
        }
    }

    private void ensureConnected() throws Exception {
        if(client!=null)return;
        // Retry no more often than every five seconds. No synthetic failover or auto-trust.
        long now=System.currentTimeMillis();
        if(now-lastAttemptMs<5000)throw new IllegalStateException("Waiting before OPC reconnect");
        lastAttemptMs=now;
        if(!Files.isRegularFile(Path.of(keyStoreFile))||!Files.isRegularFile(Path.of(trustedCertificate)))
            throw new IllegalStateException("OPC UA certificates are not provisioned");
        char[] password=keyStorePassword.toCharArray();
        KeyStore store=KeyStore.getInstance("PKCS12");
        try(var in=Files.newInputStream(Path.of(keyStoreFile))){store.load(in,password);}
        String alias=null;
        Enumeration<String> aliases=store.aliases();
        while(aliases.hasMoreElements()){
            String candidate=aliases.nextElement();
            if(store.isKeyEntry(candidate)){alias=candidate;break;}
        }
        if(alias==null)throw new IllegalStateException("Client PKCS12 has no private key");
        PrivateKey key=(PrivateKey)store.getKey(alias,password);
        Certificate[] chain=store.getCertificateChain(alias);
        X509Certificate[] certificates=Arrays.copyOf(chain,chain.length,X509Certificate[].class);
        KeyPair keyPair=new KeyPair(certificates[0].getPublicKey(),key);
        X509Certificate serverCertificate;
        try(var in=Files.newInputStream(Path.of(trustedCertificate))){
            serverCertificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        var trust=new MemoryTrustListManager();
        trust.addTrustedCertificate(serverCertificate);
        var validator=new DefaultClientCertificateValidator(
            trust,ValidationCheck.ALL_OPTIONAL_CHECKS,new MemoryCertificateQuarantine());
        OpcUaClient created=OpcUaClient.create(
            endpoint,
            endpoints->endpoints.stream()
                .filter(e->SecurityPolicy.Basic256Sha256.getUri().equals(e.getSecurityPolicyUri()))
                .filter(e->e.getSecurityMode()==MessageSecurityMode.SignAndEncrypt)
                .filter(e->endpoint.equals(e.getEndpointUrl()))
                .findFirst(),
            transport->{},
            config->config
                .setApplicationName(LocalizedText.english("Industrial Control Studio read-only client"))
                .setCertificateIdentity(keyPair,certificates)
                .setCertificateValidator(validator)
                .setSessionEndpointValidationEnabled(true)
                .setIdentityProvider(new AnonymousProvider())
                .setRequestTimeout(uint(5000)));
        try{
            created.connect();
            client=created;
        }catch(Exception ex){
            try{created.disconnect();}catch(Exception ignored){}
            throw ex;
        }
    }

    private void closeClient() {
        OpcUaClient c=client;
        client=null;
        if(c!=null)try{c.disconnect();}catch(Exception ignored){}
    }
    @PreDestroy public synchronized void stop(){closeClient();}
}
