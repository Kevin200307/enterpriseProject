package lk.ac.mrt.swiftdrop.merchant_service.service;

import java.net.*;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.events.*;
import lk.ac.mrt.swiftdrop.merchant_service.domain.*;
import lk.ac.mrt.swiftdrop.merchant_service.repository.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WebhookService {
    private final MerchantRepository merchants; private final WebhookDeliveryRepository deliveries;
    private final WebClient client; private final WebhookCrypto crypto; private final JsonMapper json; private final Clock clock=Clock.systemUTC();
    public WebhookService(MerchantRepository merchants, WebhookDeliveryRepository deliveries, WebClient.Builder clients, WebhookCrypto crypto, JsonMapper json) {
        this.merchants=merchants; this.deliveries=deliveries; this.client=clients.build(); this.crypto=crypto; this.json=json;
    }
    public record SecretResponse(String secret) {}
    @Transactional public SecretResponse configure(UUID ownerId, String url) {
        validatePublicHttps(url); Merchant m=merchants.findByOwnerUserId(ownerId).orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
        if(m.getStatus()!=MerchantStatus.APPROVED) throw new IllegalStateException("Merchant is not approved");
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); String secret="whsec_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        m.setWebhookUrl(url); m.setWebhookSecretHash(crypto.encrypt(secret)); merchants.save(m); return new SecretResponse(secret);
    }
    @Transactional public void accept(EventEnvelope<ParcelStatusChangedV2> event) {
        UUID merchantId=event.payload().merchantId(); if(merchantId==null) return;
        Merchant m=merchants.findById(merchantId).orElse(null); if(m==null||m.getWebhookUrl()==null||m.getWebhookSecretHash()==null) return;
        if(deliveries.findByEventIdAndMerchantId(event.eventId(),merchantId).isPresent()) return;
        String raw=json.writeValueAsString(event); WebhookDelivery d=deliveries.save(WebhookDelivery.builder().eventId(event.eventId()).merchant(m).url(m.getWebhookUrl()).payload(raw).build());
        deliver(d);
    }
    @Transactional public void deliver(WebhookDelivery d) {
        long timestamp=clock.instant().getEpochSecond(); String signature=WebhookCrypto.sign(crypto.decrypt(d.getMerchant().getWebhookSecretHash()),timestamp,d.getPayload());
        d.setAttempts(d.getAttempts()+1);
        try { var response=client.post().uri(d.getUrl()).contentType(MediaType.APPLICATION_JSON).header("X-SwiftDrop-Timestamp",Long.toString(timestamp)).header("X-SwiftDrop-Signature","v1="+signature).bodyValue(d.getPayload()).retrieve().toBodilessEntity().block(); d.setLastStatus(Integer.toString(response.getStatusCode().value())); d.setState(WebhookDeliveryState.DELIVERED); d.setLastError(null); }
        catch(Exception e){ d.setLastError(e.getMessage()); d.setState(WebhookDeliveryState.PENDING); }
        deliveries.save(d);
    }
    static void validatePublicHttps(String value) {
        try { URI uri=URI.create(value); if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||uri.getHost()==null) throw new IllegalArgumentException("Webhook URL must use HTTPS"); for(InetAddress address:InetAddress.getAllByName(uri.getHost())) if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()) throw new IllegalArgumentException("Webhook URL must not resolve to a private address"); }
        catch(UnknownHostException e){ throw new IllegalArgumentException("Webhook host could not be resolved",e); }
    }
}
