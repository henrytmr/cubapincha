package com.cubapincha.job.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Cifrado AES-256-GCM con clave en Android Keystore para los datos
 *  sensibles guardados en la base local (borradores, cola offline,
 *  candidaturas y notificaciones). Las ofertas en caché son datos
 *  públicos y quedan en claro, como en cualquier app.
 *
 *  Formato: "v1:" + Base64(iv 12 + ciphertext + tag). Los valores sin
 *  el prefijo se tratan como texto de versiones anteriores y se leen
 *  tal cual (la migración a v4 los cifra). Si el Keystore no está
 *  disponible, cifrar falla en voz alta: nunca se guarda un dato
 *  sensible en claro por accidente. */
public final class CryptoHelper {
    private static final String TAG = "CryptoHelper";
    private static final String KEY_ALIAS = "cubapincha_local";
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private CryptoHelper() { }

    /** Cifra un texto. Lanza IllegalStateException si el Keystore no está
     *  disponible: quien llama no debe guardar el texto en claro. */
    public static String encrypt(String plain) {
        if (plain == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return PREFIX + Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo cifrar un dato local", e);
            throw new IllegalStateException(
                    "El cifrado local no está disponible en este dispositivo.", e);
        }
    }

    /** Descifra un valor. Sin prefijo "v1:" se devuelve tal cual (texto de
     *  versiones anteriores). Si el descifrado falla, devuelve null para
     *  que quien llama descarte esa fila. */
    public static String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) return stored;
        try {
            byte[] raw = Base64.decode(stored.substring(PREFIX.length()), Base64.NO_WRAP);
            if (raw.length <= IV_BYTES + 16) return null;
            byte[] iv = Arrays.copyOfRange(raw, 0, IV_BYTES);
            byte[] ct = Arrays.copyOfRange(raw, IV_BYTES, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo descifrar un dato local; se descarta esa fila", e);
            return null;
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }
}
