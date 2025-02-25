package com.odysee.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import com.odysee.app.exceptions.AuthTokenInvalidatedException;
import com.odysee.app.utils.Helper;
import com.odysee.app.utils.Lbry;
import com.odysee.app.utils.LbryAnalytics;
import com.odysee.app.utils.Lbryio;

import com.odysee.app.R;

public class FirstRunActivity extends AppCompatActivity {

    private BroadcastReceiver sdkReceiver;
    private BroadcastReceiver authReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_first_run);
//
//        TextView welcomeTos = findViewById(R.id.welcome_text_view_tos);
//        welcomeTos.setMovementMethod(LinkMovementMethod.getInstance());
//        welcomeTos.setText(HtmlCompat.fromHtml(getString(R.string.welcome_tos), HtmlCompat.FROM_HTML_MODE_LEGACY));
//
//        findViewById(R.id.welcome_link_use_lbry).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                finishFirstRun();
//            }
//        });
//
//        registerAuthReceiver();
//        findViewById(R.id.welcome_wait_container).setVisibility(View.VISIBLE);
//        IntentFilter filter = new IntentFilter();

        CheckInstallIdTask task = new CheckInstallIdTask(this, new CheckInstallIdTask.InstallIdHandler() {
            @Override
            public void onInstallIdChecked(boolean result) {
                if (result) {
                    // install_id generated and validated, authenticate now
                    authenticate();
                    return;
                }
            }
        });
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        finishFirstRun();
    }

    public void onResume() {
        super.onResume();
        LbryAnalytics.setCurrentScreen(this, "First Run", "FirstRun");
    }

    private void authenticate() {
        new AuthenticateTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void finishFirstRun() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putBoolean(MainActivity.PREFERENCE_KEY_INTERNAL_FIRST_RUN_COMPLETED, true).apply();

        // first_run_completed event
        LbryAnalytics.logEvent(LbryAnalytics.EVENT_FIRST_RUN_COMPLETED);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private static class CheckInstallIdTask extends AsyncTask<Void, Void, Boolean> {
        private final Context context;
        private final InstallIdHandler handler;
        public CheckInstallIdTask(Context context, InstallIdHandler handler) {
            this.context = context;
            this.handler = handler;
        }
        protected Boolean doInBackground(Void... params) {
            // Load the installation id from the file system
            File[] dirs = context.getExternalFilesDirs(null);
            String lbrynetDir = dirs[0].getAbsolutePath().concat("/lbrynet");

            File dir = new File(lbrynetDir);
            boolean dirExists = dir.isDirectory();
            if (!dirExists) {
                dirExists = dir.mkdirs();
            }

            if (!dirExists) {
                return false;
            }

            String installIdPath = String.format("%s/install_id", lbrynetDir);
            File file = new File(installIdPath);
            String installId  = null;
            if (!file.exists()) {
                // generate the install_id
                installId = Lbry.generateId();
                BufferedWriter writer = null;
                try {
                    writer = new BufferedWriter(new FileWriter(file));
                    writer.write(installId);
                } catch (IOException ex) {
                    return false;
                } finally {
                    Helper.closeCloseable(writer);
                }
            } else {
                // read the installation id from the file
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(installIdPath)));
                    installId = reader.readLine();
                } catch (IOException ex) {
                    return false;
                } finally {
                    Helper.closeCloseable(reader);
                }
            }

            if (!Helper.isNullOrEmpty(installId)) {
                Lbry.INSTALLATION_ID = installId;
            }
            return !Helper.isNullOrEmpty(installId);
        }
        protected void onPostExecute(Boolean result) {
            if (handler != null) {
                handler.onInstallIdChecked(result);
            }
        }

        public interface InstallIdHandler {
            void onInstallIdChecked(boolean result);
        }
    }

    private static class AuthenticateTask extends AsyncTask<Void, Void, Void> {
        private final Context context;
        public AuthenticateTask(Context context) {
            this.context = context;
        }
        protected Void doInBackground(Void... params) {
            try {
                Lbryio.authenticate(context);
            } catch (AuthTokenInvalidatedException ex) {
                // pass
            }
            return null;
        }
    }
}
