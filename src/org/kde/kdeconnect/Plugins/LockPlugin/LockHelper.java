package org.kde.kdeconnect.Plugins.LockPlugin;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandPlugin;
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
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

@RequiresApi(api = Build.VERSION_CODES.N)
public class LockHelper extends FingerprintManager.AuthenticationCallback {

    private static final String KEY_NAME = UUID.randomUUID().toString();
    private static final String STATE_SELECTED_DEVICE = "selected_device";
    private static final String TAG = LockHelper.class.getSimpleName();


    private Dialog mDialog;
    private Context mContext;

    // fp stuff
    private KeyStore mKeyStore;
    private Cipher mCipher;
    private CancellationSignal mCancellationSignal;

    private boolean unlocked = false;

    LockHelper(Context context) {
        this.mContext = context;
        this.mDialog = new AlertDialog.Builder(context).setTitle(R.string.title_fp_unlock_diag)
                .setMessage(mContext.getString(R.string.mesg_fp_unlock_diag))
                .setIcon(R.drawable.ic_fingerprint_black_24dp)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        onAuthenticationFailed();
                    }
                })
                .setNeutralButton(mContext.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        onAuthenticationFailed();
                    }
                })
                .create();
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
        if(unlockComputer()) {
            Toast.makeText(mContext, R.string.mesg_auth_success, Toast.LENGTH_SHORT).show();
        }
        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    @Override
    public void onAuthenticationFailed() {
        super.onAuthenticationFailed();
        mDialog.dismiss();
        Toast.makeText(mContext, R.string.mesg_auth_fail, Toast.LENGTH_SHORT).show();
        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    boolean unlockComputer() {
        BackgroundService.RunCommand(mContext, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                //TODO: don't piggyback on runcommand
                String deviceId = mContext.getSharedPreferences(STATE_SELECTED_DEVICE,
                        Context.MODE_PRIVATE)
                        .getString(STATE_SELECTED_DEVICE, null);
                Device device = service.getDevice(deviceId);
                RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
                if (plugin == null) {
                    Toast.makeText(mContext, R.string.mesg_app_discon, Toast.LENGTH_SHORT).show();
                    unlocked = false;
                    return;
                }
                for (JSONObject obj : plugin.getCommandList()) {
                    try {
                        // ideally there should only be one command named "unlock"
                        // all of them are executed though
                        // command: loginctl unlock-session
                        if(obj.getString("name").equals("unlock")) {
                            plugin.runCommand(obj.getString("key"));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                unlocked = true;
            }
        });
        return  unlocked;
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
            Toast.makeText(mContext, R.string.mesg_sec_exc, Toast.LENGTH_SHORT).show();
        }
    }
}
