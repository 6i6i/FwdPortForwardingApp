/*
 * Fwd: the port forwarding app
 * Copyright (C) 2016  Elixsr Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.elixsr.portforwarder.ui.preferences;

import static android.app.Activity.RESULT_CANCELED;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.elixsr.portforwarder.R;
import com.elixsr.portforwarder.adapters.RuleListJsonValidator;
import com.elixsr.portforwarder.adapters.RuleListTargetJsonSerializer;
import com.elixsr.portforwarder.dao.RuleDao;
import com.elixsr.portforwarder.db.RuleContract;
import com.elixsr.portforwarder.db.RuleDbHelper;
import com.elixsr.portforwarder.forwarding.ForwardingManager;
import com.elixsr.portforwarder.models.RuleModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Created by Niall McShane on 29/02/2016.
 */
public class SettingsFragment extends PreferenceFragment {

    private static final String TAG = "SettingsFragment";

    private static final String CLEAR_RULES_COMPLETE_MESSAGE = "All rules have been removed";

    public static final String DARK_MODE_BROADCAST = "com.elixsr.DARK_MODE_TOGGLE";

    private LocalBroadcastManager localBroadcastManager;
    private ForwardingManager forwardingManager;
    private Preference clearRulesButton;
    private Preference versionNamePreference;
    private Preference importRulesPreference;

    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferencesListener;

    private static final int RULE_LIST_CODE = 1;
    private Gson gson;
    private RuleDao ruleDao;
    private Toast toast;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        ruleDao = new RuleDao(new RuleDbHelper(getActivity()));

        gson = new GsonBuilder()
                .registerTypeAdapter(InetSocketAddress.class, new RuleListTargetJsonSerializer())
                .registerTypeAdapter(RuleModel.class, new RuleListJsonValidator())
                .excludeFieldsWithoutExposeAnnotation()
                .create();

        forwardingManager = ForwardingManager.getInstance();
        localBroadcastManager = LocalBroadcastManager.getInstance(getActivity().getBaseContext());

        toast = Toast.makeText(getActivity(), "",
                Toast.LENGTH_SHORT);

        clearRulesButton = findPreference(getString(R.string.pref_clear_rules));

        clearRulesButton.setOnPreferenceClickListener(preference -> {
            // Code for what you want it to do

            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.alert_dialog_delete_all_rules_title)
                    .setMessage(R.string.alert_dialog_delete_all_rules_text)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {

                        // Set up the database
                        SQLiteDatabase db = new RuleDbHelper(getActivity()).getReadableDatabase();


                        db.delete(RuleContract.RuleEntry.TABLE_NAME, null, null);

                        db.close();

                        clearRulesButton.setEnabled(false);

                        Toast.makeText(getActivity(), CLEAR_RULES_COMPLETE_MESSAGE,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.no, (dialog, which) -> {
                        // Do nothing
                    })
                    .show();
            return true;
        });

        versionNamePreference = findPreference(getString(R.string.pref_version));

        versionNamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            int versionPrefClicks = 0;

            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (versionPrefClicks >= 2 && versionPrefClicks <= 3) {
                    toast.setText(4 - versionPrefClicks + " more...");
                    toast.show();
                }
                if (++versionPrefClicks == 5) {
                    versionPrefClicks = 0;
                    toast.setText("...");
                    toast.show();
                    Intent advancedSettingsActivity = new Intent(getActivity(), AdvancedSettingsActivity.class);
                    startActivity(advancedSettingsActivity);
                    return true;
                }
                return false;
            }

        });

        importRulesPreference = findPreference(getString(R.string.pref_import));

        importRulesPreference.setOnPreferenceClickListener(preference -> {
            importRules();
            return false;
        });

        Preference exportRulesPreference = findPreference(getString(R.string.pref_export));

        exportRulesPreference.setOnPreferenceClickListener(preference -> {
            exportRules();
            return false;
        });


        // Set up click of about elixsr button - show webview
        Preference aboutElixsrButton = findPreference(getString(R.string.pref_about_link));
        aboutElixsrButton.setOnPreferenceClickListener(preference -> {
            //code for what you want it to do
            Intent aboutActivityIntent = new Intent(getActivity(), AboutElixsrActivity.class);
            startActivity(aboutActivityIntent);
            return true;
        });

        Preference sourceCodeButton = findPreference("pref_source_code");
        sourceCodeButton.setOnPreferenceClickListener(preference -> {
            Intent sourceCodeIntent = new Intent(getActivity(), SourceCodeActivity.class);
            startActivity(sourceCodeIntent);
            return true;
        });


    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Recreate our activity if we changed to dark theme
        sharedPreferencesListener = (sharedPreferences, key) -> {
            if (key.equals("pref_dark_theme")) {
                Intent intent = new Intent();
                intent.setAction(DARK_MODE_BROADCAST);
                localBroadcastManager.sendBroadcast(intent);

            }
        };

        // Prevent garbage collection
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(sharedPreferencesListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (forwardingManager.isEnabled()) {
            clearRulesButton.setEnabled(false);
            importRulesPreference.setEnabled(false);
        } else {
            clearRulesButton.setEnabled(true);
            importRulesPreference.setEnabled(true);
        }

        String versionName = "Version ";
        try {
            versionName = versionName + getActivity().getBaseContext().getPackageManager()
                    .getPackageInfo(getActivity().getBaseContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Application Version could not be found.", e);
            versionName = versionName + "not found";
        }
        versionNamePreference.setTitle(versionName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Ensure we unregister our previous listener - as it now points to a null activity
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener);
    }

    private void importRules() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select a rule list to import"), RULE_LIST_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), "A file manager is required to import rule lists.", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportRules() {
        if (ruleDao.getAllRuleModels().size() > 0) {
            // Lets create out file to store our data
            File outputDir = getActivity().getCacheDir();
            String ruleList = ruleListToJsonString();
            try {
                File outputFile = File.createTempFile("fwd_rule_list", ".json", outputDir);
                outputFile.createNewFile();

                FileWriter writer = new FileWriter(outputFile);

                writer.append(ruleList);

                // Ensure the writer is closed - assuming its blocking
                writer.close();

                // Everything good, lets send an intent
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_SUBJECT, "Fwd Rule List");
                intent.putExtra(Intent.EXTRA_TEXT, "Your fwd rules have been attached with the name '" + outputFile.getName() + "'.");
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getActivity().getApplicationContext(), getActivity().getApplicationContext().getPackageName() + ".util.provider", outputFile));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, getString(R.string.export_rules_action_title)));

                Log.i(TAG, "onDataChange: URI " + Uri.fromFile(outputFile).toString());

            } catch (IOException e) {
                Log.e(TAG, "onDataChange: error trying to create file to store exported data", e);
                Toast.makeText(getActivity().getBaseContext(), "Error when trying to export dreams.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getActivity().getBaseContext(), "No rules to export.", Toast.LENGTH_SHORT).show();
        }
    }

    private String ruleListToJsonString() {
        List<RuleModel> ruleModels = ruleDao.getAllRuleModels();

        return gson.toJson(ruleModels);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_CANCELED && requestCode == RULE_LIST_CODE && data.getData() != null) {
            Intent importRulesActivityIntent = new Intent(getActivity(), ImportRulesActivity.class);
            importRulesActivityIntent.putExtra(ImportRulesActivity.IMPORTED_RULE_DATA, data.getData().toString());
            startActivity(importRulesActivityIntent);
        }

    }
}
