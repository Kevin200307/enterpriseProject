package lk.ac.mrt.swiftdrop.merchant_service.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookCrypto {
    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();
    public WebhookCrypto(@Value("${merchant.webhook.master-key:development-only-webhook-master-key}") String key) {
        try { masterKey = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public String encrypt(String value) { try { byte[] iv=new byte[12]; random.nextBytes(iv); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(masterKey,"AES"),new GCMParameterSpec(128,iv)); byte[] encrypted=c.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] all=new byte[iv.length+encrypted.length]; System.arraycopy(iv,0,all,0,iv.length); System.arraycopy(encrypted,0,all,iv.length,encrypted.length); return Base64.getEncoder().encodeToString(all); } catch(Exception e){throw new IllegalStateException(e);} }
    public String decrypt(String value) { try { byte[] all=Base64.getDecoder().decode(value); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(masterKey,"AES"),new GCMParameterSpec(128,all,0,12)); return new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8); } catch(Exception e){throw new IllegalStateException(e);} }
    public static String sign(String secret, long timestamp, String rawBody) { try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return java.util.HexFormat.of().formatHex(mac.doFinal((timestamp+"."+rawBody).getBytes(StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);} }
}
