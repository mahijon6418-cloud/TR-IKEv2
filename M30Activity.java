package org.strongswan.android.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnProfileSource;
import org.strongswan.android.data.VpnType;
import org.strongswan.android.logic.VpnStateService;

import java.util.List;

public class M30Activity extends Activity implements VpnStateService.VpnStateListener {
    private static final int VPN_PREPARE = 3001;
    private static final String PREFS = "m30";
    private static final String PROFILE_NAME = "M30";
    private static final String DEFAULT_SERVER = "149.50.208.98";
    private static final String SERVER_IDENTITY = "pointtoserver.com";
    private static final String DEFAULT_USER = "purevpn0s13269248";
    private static final String DEFAULT_DNS = "8.8.8.8";

    private EditText server;
    private EditText username;
    private EditText password;
    private TextView status;
    private Button connect;
    private VpnStateService vpnService;
    private boolean bound;
    private VpnProfile profile;
    private long connectedAt;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            vpnService = ((VpnStateService.LocalBinder) binder).getService();
            bound = true;
            vpnService.registerListener(M30Activity.this);
            refreshState();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            vpnService = null;
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(getResources().getColor(R.color.m30_bg));
        buildUi();
        bindService(new Intent(this, VpnStateService.class), connection, Context.BIND_AUTO_CREATE);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setBackgroundColor(getResources().getColor(R.color.m30_bg));

        TextView title = text("M30", 34, true);
        title.setTextColor(getResources().getColor(R.color.m30_text));
        root.addView(title, lp(-1, -2));

        TextView sub = text("IKEv2 Secure Connection", 15, false);
        sub.setTextColor(getResources().getColor(R.color.m30_muted));
        root.addView(sub, lp(-1, -2));

        server = field("Server");
        server.setText(getPreferences(MODE_PRIVATE).getString("server", DEFAULT_SERVER));
        root.addView(server, lp(-1, dp(56)));

        username = field("Username");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        username.setText(getPreferences(MODE_PRIVATE).getString("username", DEFAULT_USER));
        root.addView(username, lp(-1, dp(56)));

        password = field("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, lp(-1, dp(56)));

        status = text("Disconnected", 17, true);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(getResources().getColor(R.color.m30_muted));
        root.addView(status, lp(-1, dp(64)));

        connect = new Button(this);
        connect.setText("CONNECT");
        connect.setAllCaps(false);
        connect.setOnClickListener(v -> toggleConnection());
        root.addView(connect, lp(-1, dp(56)));

        TextView hint = text("Identity: automatic CA  •  DNS: 8.8.8.8  •  IPv6 blocked", 12, false);
        hint.setTextColor(getResources().getColor(R.color.m30_muted));
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, lp(-1, dp(45)));

        setContentView(root);
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        return e;
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(0, dp(8), 0, dp(8));
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toggleConnection() {
        if (vpnService != null && vpnService.getState() != VpnStateService.State.DISABLED) {
            vpnService.disconnect();
            return;
        }
        String host = server.getText().toString().trim();
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Server, username and password are required", Toast.LENGTH_SHORT).show();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putString("server", host).putString("username", user).apply();
        saveAndPrepare(host, user, pass);
    }

    private void saveAndPrepare(String host, String user, String pass) {
        VpnProfileSource source = new VpnProfileSource(this);
        VpnProfileDataSource db = source.open();
        try {
            List<VpnProfile> profiles = db.getAllVpnProfiles();
            profile = null;
            for (VpnProfile p : profiles) {
                if (PROFILE_NAME.equals(p.getName())) { profile = p; break; }
            }
            if (profile == null) {
                profile = new VpnProfile();
                profile.setName(PROFILE_NAME);
                profile.setVpnType(VpnType.IKEV2_EAP);
            }
            profile.setGateway(host);
            profile.setRemoteId(SERVER_IDENTITY);
            profile.setUsername(user);
            profile.setPassword(pass);
            profile.setDnsServers(DEFAULT_DNS);
            profile.setSplitTunneling(VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6);
            profile.setReadOnly(false);
            if (profile.getId() < 0) profile = db.insertProfile(profile);
            else db.updateVpnProfile(profile);
        } finally {
            source.close();
        }

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_PREPARE);
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        if (vpnService == null) {
            Toast.makeText(this, "VPN service is not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle info = new Bundle();
        info.putString(VpnProfileDataSource.KEY_UUID, profile.getUUID().toString());
        info.putString(VpnProfileDataSource.KEY_PASSWORD, profile.getPassword());
        info.putString(VpnProfileDataSource.KEY_USERNAME, profile.getUsername());
        vpnService.connect(info, true);
        status.setText("Connecting…");
        connect.setText("DISCONNECT");
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VPN_PREPARE) {
            if (result == RESULT_OK) startTunnel();
            else Toast.makeText(this, "VPN permission was not granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshState() {
        if (vpnService == null) return;
        VpnStateService.State s = vpnService.getState();
        if (s == VpnStateService.State.CONNECTED) {
            status.setText("Connected");
            connect.setText("DISCONNECT");
            if (connectedAt == 0) connectedAt = System.currentTimeMillis();
        } else if (s == VpnStateService.State.CONNECTING) {
            status.setText("Connecting…");
            connect.setText("DISCONNECT");
        } else if (s == VpnStateService.State.DISCONNECTING) {
            status.setText("Disconnecting…");
            connect.setText("DISCONNECT");
        } else {
            status.setText("Disconnected");
            connect.setText("CONNECT");
            connectedAt = 0;
        }
    }

    @Override public void stateChanged() {
        runOnUiThread(this::refreshState);
    }

    @Override protected void onDestroy() {
        if (bound && vpnService != null) {
            vpnService.unregisterListener(this);
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }
}
