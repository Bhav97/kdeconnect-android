package org.kde.kdeconnect.Plugins.LockPlugin;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.kde.kdeconnect_tp.R;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

@RequiresApi(api = Build.VERSION_CODES.N)
public class LockHelper extends FingerprintManager.AuthenticationCallback {

    public interface LockCallback {
        void onSuccess();
        void onFailure();
        void onError(int errCode, CharSequence errString);
    }

    private static final String KEY_NAME = UUID.randomUUID().toString();
    private static final String TAG = LockHelper.class.getSimpleName();

    private static volatile LockHelper mLockHelper;
    private ArrayList<LockCallback> callbacks = new ArrayList<>();

    public void addLockCallback(LockCallback newCallback) {
        callbacks.add(newCallback);
    }

    public void removeLockCallback(LockCallback oldCallback) {
        callbacks.remove(oldCallback);
    }


    private Dialog mDialog;

    // fp stuff
    private KeyStore mKeyStore;
    private Cipher mCipher;
    private CancellationSignal mCancellationSignal;


    private LockHelper(Context context) {
        if (mLockHelper != null) {
            throw new RuntimeException("Use getInstance()");
        }

        this.mDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.title_fp_unlock_diag)
                .setMessage(R.string.mesg_fp_unlock_diag)
                .setIcon(R.drawable.ic_fingerprint_black_24dp)
                .setOnDismissListener(dialog -> onAuthenticationFailed())
                .setNeutralButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    onAuthenticationFailed();
                })
                .create();
    }

    public synchronized static LockHelper getInstance(Context context) {
        if(mLockHelper == null) {
            mLockHelper = new LockHelper(context);
        }
        return mLockHelper;
    }

    boolean shouldAuth() {
        return !mDialog.isShowing();
    }

    public Dialog getDialog() {
        return mDialog;
    }

    private boolean generateKey() {
        mKeyStore = null;
        KeyGenerator keyGenerator;

        try {
            mKeyStore = KeyStore.getInstance("AndroidKeyStore");
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException |
                NoSuchProviderException e) {
            return false;
        } catch (KeyStoreException e) {
            return false;
        }

        try {
            mKeyStore.load(null);
            keyGenerator.init(new
                    KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(
                            KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();

            return true;
        } catch (NoSuchAlgorithmException
                | InvalidAlgorithmParameterException
                | CertificateException
                | IOException e) {
            return false;
        }
    }

    private boolean cipherInit() {

        if (!generateKey()) {
            onAuthenticationFailed();
            return false;
        }

        try {
            mCipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException |
                NoSuchPaddingException e) {
            onAuthenticationFailed();
            return false;
        }

        try {
            mKeyStore.load(null);
            SecretKey key = (SecretKey) mKeyStore.getKey(KEY_NAME, null);
            mCipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            onAuthenticationFailed();
            return false;
        } catch (KeyStoreException | CertificateException
                | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            onAuthenticationFailed();
            return false;
        }
    }

    @Override
    public void onAuthenticationError(int errorCode, CharSequence errString) {
        super.onAuthenticationError(errorCode, errString);
        mDialog.dismiss();
        Log.e(TAG, "onAuthenticationError: " + errString);
        for (LockCallback c :
                callbacks) {
            c.onError(errorCode, errString);
        }
        onAuthenticationFailed();
    }

    @Override
    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        super.onAuthenticationHelp(helpCode, helpString);
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        super.onAuthenticationSucceeded(result);
        mDialog.setOnDismissListener(null);
        mDialog.dismiss();
        for (LockCallback c :
                callbacks) {
            c.onSuccess();
        }
    }

    @Override
    public void onAuthenticationFailed() {
        super.onAuthenticationFailed();
        mDialog.dismiss();
        for (LockCallback c :
                callbacks) {
            c.onFailure();
        }
        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    public void pleaseAuthenticate(FingerprintManager manager) {
        if(!cipherInit()) {
            onAuthenticationFailed();
            return;
        }
        mCancellationSignal = new CancellationSignal();
        try {
            manager.authenticate(new FingerprintManager.CryptoObject(mCipher),
                    mCancellationSignal, 0, this, null);
        } catch (SecurityException e) {
            mDialog.dismiss();
            for (LockCallback c :
                    callbacks) {
                c.onError(-32, e.getMessage());
            }
            Log.e(TAG, "pleaseAuthenticate: ", e);
        }
    }
}
