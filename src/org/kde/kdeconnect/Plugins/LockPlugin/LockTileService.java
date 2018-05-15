package org.kde.kdeconnect.Plugins.LockPlugin;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandPlugin;
import org.kde.kdeconnect_tp.R;

import java.util.Locale;

@RequiresApi(api = Build.VERSION_CODES.N)
public class LockTileService extends TileService implements LockHelper.LockCallback {

    private LockHelper mLockHelper;
    private static final String STATE_SELECTED_DEVICE = "selected_device";
    private final static String TAG = LockTileService.class.getSimpleName();

    @Override
    public void onClick() {
        super.onClick();
        if(isLocked()) {
            unlockAndRun(this::unlockComputer);
        } else {
            if (mLockHelper.shouldAuth()) {
                showDialog(mLockHelper.getDialog());
                mLockHelper.pleaseAuthenticate((FingerprintManager)
                        getSystemService(FINGERPRINT_SERVICE));
            }
        }
    }

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_fingerprint_black_24dp));
        tile.setLabel(getString(R.string.label_qs_unlock));
        tile.setContentDescription(getString(R.string.desc_qs_unlock));
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
        mLockHelper = LockHelper.getInstance(this);
        mLockHelper.addLockCallback(this);
    }

    @Override
    public void onSuccess() {
        //TODO: are toasts really necessary? they ugly
        if(unlockComputer(1)) {
            Toast.makeText(this, "Authenticated Successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(LockTileService.this, R.string.mesg_app_discon, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFailure() {
        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(int errCode, CharSequence errString) {
        Toast.makeText(this, String.format(Locale.getDefault(), "%d : %s", errCode, errString),
                Toast.LENGTH_SHORT).show();
    }

    boolean bool = false;

    private boolean unlockComputer() {
        BackgroundService.RunCommand(this, service -> {
            Device device = service.getDevice(getSharedPreferences(STATE_SELECTED_DEVICE, Context.MODE_PRIVATE)
            .getString(STATE_SELECTED_DEVICE, null));
            LockPlugin p = device.getPlugin(LockPlugin.class);
            if (p == null) {
                bool = false;
                return;
            }
            p.unlockDevice();
             bool = true;
        });
        return bool;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLockHelper.removeLockCallback(this);
    }

    private boolean unlockComputer(int a) {
        BackgroundService.RunCommand(this, service -> {
            //TODO: don't piggyback on runcommand
            String deviceId = getSharedPreferences(STATE_SELECTED_DEVICE,
                    Context.MODE_PRIVATE)
                    .getString(STATE_SELECTED_DEVICE, null);
            Device device = service.getDevice(deviceId);
            RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
            if (plugin == null) {
                bool = false;
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
            bool = true;
        });
        return bool;
    }
}
