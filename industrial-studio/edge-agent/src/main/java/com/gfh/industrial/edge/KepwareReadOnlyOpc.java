package com.gfh.industrial.edge;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Experimental on-site Kepware OPC UA READ ONLY client. No OPC UA write method.
 * The endpoint, trusted certificate, client identity and approved NodeIds are
 * locally provisioned; a cloud editor cannot expand the site's read allowlist.
 */
public final class KepwareReadOnlyOpc implements AutoCloseable {
    private final String endpoint=env("STUDIO_EDGE_OPC_ENDPOINT");
    private final String pkcs12=env("STUDIO_EDGE_OPC_CLIENT_PKCS12");
    private final String keyPassword=env("STUDIO_EDGE_OPC_CLIENT_PKCS12_PASSWORD");
    private final String serverCert=env("STUDIO_EDGE_OPC_SERVER_CERT");
    private final String opcUser=env("STUDIO_EDGE_OPC_USER");
    private final String opcPassword=env("STUDIO_EDGE_OPC_PASSWORD");
    private final String approvedFile=env("STUDIO_EDGE_APPROVED_NODES_FILE");
    private final boolean enabled=Boolean.parseBoolean(env("STUDIO_EDGE_LOCAL_OPC_READ_ALLOWED"));
    private OpcUaClient client;

    static String env(String name){return System.getenv().getOrDefault(name,"").trim();}

    private void verifyLocalApproval(List<String> nodes) throws Exception {
        if(!enabled || endpoint.isBlank() || pkcs12.isBlank() || keyPassword.isBlank() ||
           serverCert.isBlank() || opcUser.isBlank() || opcPassword.isBlank() ||
           approvedFile.isBlank())
            throw new IllegalStateException("Site OPC UA read is not locally authorized");
        if(!endpoint.matches("opc\\.tcp://[A-Za-z0-9_.-]+:[0-9]{2,5}(/[^\\s]*)?"))
            throw new IllegalStateException("Invalid site-pinned OPC UA endpoint");
        if(!Files.isRegularFile(Path.of(approvedFile)))throw new IllegalStateException(
            "The site-maintained approved-node file is missing");
        Set<String> approved=new HashSet<>();
        for(String line:Files.readAllLines(Path.of(approvedFile))){
            line=line.trim();
            if(!line.isEmpty()&&!line.startsWith("#"))approved.add(line);
        }
        if(approved.isEmpty() || nodes.isEmpty() || !approved.containsAll(nodes))
            throw new IllegalStateException("Cloud tag mapping requests nodes not approved locally");
    }

    private void connected() throws Exception {
        if(client!=null)return;
        if(!Files.isRegularFile(Path.of(pkcs12))||!Files.isRegularFile(Path.of(serverCert)))
            throw new IllegalStateException("Site OPC UA certificate files are missing");
        KeyStore store=KeyStore.getInstance("PKCS12");
        char[] password=keyPassword.toCharArray();
        try(var in=Files.newInputStream(Path.of(pkcs12))){store.load(in,password);}
        String alias=null;
        Enumeration<String> aliases=store.aliases();
        while(aliases.hasMoreElements()){
            String candidate=aliases.nextElement();
            if(store.isKeyEntry(candidate)){alias=candidate;break;}
        }
        if(alias==null)throw new IllegalStateException("OPC UA client key missing");
        PrivateKey key=(PrivateKey)store.getKey(alias,password);
        Certificate[] chain=store.getCertificateChain(alias);
        X509Certificate[] certs=Arrays.copyOf(chain,chain.length,X509Certificate[].class);
        KeyPair pair=new KeyPair(certs[0].getPublicKey(),key);
        X509Certificate trusted;
        try(var in=Files.newInputStream(Path.of(serverCert))){
            trusted=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        var trust=new MemoryTrustListManager();
        trust.addTrustedCertificate(trusted);
        var validator=new DefaultClientCertificateValidator(trust,
            ValidationCheck.ALL_OPTIONAL_CHECKS,new MemoryCertificateQuarantine());
        OpcUaClient made=OpcUaClient.create(
            endpoint,
            endpoints->endpoints.stream()
                .filter(e->SecurityPolicy.Basic256Sha256.getUri().equals(e.getSecurityPolicyUri()))
                .filter(e->e.getSecurityMode()==MessageSecurityMode.SignAndEncrypt)
                .filter(e->endpoint.equals(e.getEndpointUrl()))
                .findFirst(),
            transport->{},
            config->config
                .setApplicationName(LocalizedText.english("Industrial Studio local headless collector"))
                .setCertificateIdentity(pair,certs)
                .setCertificateValidator(validator)
                .setSessionEndpointValidationEnabled(true)
                .setIdentityProvider(new UsernameProvider(opcUser,opcPassword))
                .setRequestTimeout(uint(5000)));
        try{made.connect();client=made;}
        catch(Exception error){try{made.disconnect();}catch(Exception ignored){}throw error;}
    }

    public synchronized Map<String,Double> read(JsonNode configuredTags) throws Exception {
        List<NodeId> nodes=new ArrayList<>();
        List<String> ids=new ArrayList<>();
        List<String> mapping=new ArrayList<>();
        if(!configuredTags.isArray() || configuredTags.size()<1 || configuredTags.size()>50)
            throw new IllegalStateException("Invalid cloud read mapping");
        for(JsonNode tag:configuredTags){
            String id=tag.path("id").asText("");
            String raw=tag.path("nodeId").asText("");
            if(!id.matches("[A-Za-z][A-Za-z0-9_]{0,49}") ||
               !raw.matches("ns=[0-9]+;[isgb]=.+"))
                throw new IllegalStateException("Invalid cloud read mapping");
            ids.add(id);mapping.add(raw);nodes.add(NodeId.parse(raw));
        }
        verifyLocalApproval(mapping);
        try{
            connected();
            List<DataValue> values=client.readValues(0.0,TimestampsToReturn.Both,nodes);
            if(values.size()!=ids.size())throw new IllegalStateException("Unexpected OPC UA read size");
            Map<String,Double> result=new LinkedHashMap<>();
            for(int i=0;i<ids.size();i++){
                DataValue data=values.get(i);
                Object raw=data.value().value();
                if(!data.statusCode().isGood()||!(raw instanceof Number number)||
                   !Double.isFinite(number.doubleValue()))
                    throw new IllegalStateException("Bad quality or non-numeric OPC UA sample");
                result.put(ids.get(i),number.doubleValue());
            }
            return result;
        }catch(Exception error){close();throw error;}
    }
    @Override public synchronized void close(){
        OpcUaClient c=client;client=null;
        if(c!=null)try{c.disconnect();}catch(Exception ignored){}
    }
}
