/*
 * Copyright (C) 2012-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.brewcrewfoo.performance.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.SystemService;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.util.Log;

import com.brewcrewfoo.performance.R;
import com.brewcrewfoo.performance.activities.PCSettings;
import com.brewcrewfoo.performance.util.CMDProcessor;
import com.brewcrewfoo.performance.util.Helpers;

//import com.android.settings.Utils;

import java.lang.Runtime;
import java.io.File;
import java.io.IOException;

import static com.brewcrewfoo.performance.util.Constants.*;

//
// CPU Related Settings
//
public class Gpu extends PreferenceFragment implements
        OnSharedPreferenceChangeListener {

    public static final String GPU_VPLL_PREF = "pref_gpu_vpll";
    public static final String MALI_VPLL_FILE = "/sys/module/mali/parameters/mali_use_vpll";
    public static final String FREQ_MIN_PREF = "pref_gpu_freq_min";
    public static final String MIN_FREQ_VOLT_PREF = "pref_gpu_min_freq_volt";
    public static final String FREQ_MAX_PREF = "pref_gpu_freq_max";
    public static final String MAX_FREQ_VOLT_PREF = "pref_gpu_max_freq_volt";
    public static final String CUR_FREQ_FILE = "/sys/class/misc/gpu_clock_control/gpu_control";
    public static final String CUR_VOLT_FILE = "/sys/class/misc/gpu_voltage_control/gpu_control";
    public static String FREQ_MAX_FILE = null;
    public static String FREQ_MIN_FILE = null;
    public static final String SOB_PREF = "pref_gpu_set_on_boot";

    private final CMDProcessor cmd = new CMDProcessor();

    private static final String TAG = "GPUSettings";

    private String mMinFrequencyFormat;
    private String mMinFreqVoltFormat;
    private String mMaxFrequencyFormat;
    private String mMaxFreqVoltFormat;

    private CheckBoxPreference mGpuVpll;
    private ListPreference mMinFrequencyPref;
    private ListPreference mMinFreqVoltPref;
    private ListPreference mMaxFrequencyPref;
    private ListPreference mMaxFreqVoltPref;

    private SharedPreferences mPreferences;
    private Context context;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getActivity();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        addPreferencesFromResource(R.xml.gpu_settings);

        mMinFrequencyFormat = getString(R.string.gpu_min_freq_summary);
        mMinFreqVoltFormat = getString(R.string.gpu_min_freq_volt_summary);
        mMaxFrequencyFormat = getString(R.string.gpu_max_freq_summary);
        mMaxFreqVoltFormat = getString(R.string.gpu_max_freq_volt_summary);

        String[] currentFrequencies = new String[0];
        String currentFrequenciesLine;

        mGpuVpll = (CheckBoxPreference) findPreference(GPU_VPLL_PREF);
        mMinFrequencyPref = (ListPreference) findPreference(FREQ_MIN_PREF);
        mMinFreqVoltPref = (ListPreference) findPreference(MIN_FREQ_VOLT_PREF);
        mMaxFrequencyPref = (ListPreference) findPreference(FREQ_MAX_PREF);
        mMaxFreqVoltPref = (ListPreference) findPreference(MAX_FREQ_VOLT_PREF);

	String vpll;
        if (!Helpers.fileExists(MALI_VPLL_FILE) || (vpll = Helpers.readOneLine(MALI_VPLL_FILE)) == null) {
            mGpuVpll.setEnabled(false);
	} else {
	    mGpuVpll.setChecked(vpll.equals("1"));
	}

        // Disable the min/max list if we dont have a list file
        if (!Helpers.fileExists(CUR_FREQ_FILE) || (currentFrequenciesLine = Helpers.readOneLine(CUR_FREQ_FILE)) == null) {
            mMinFrequencyPref.setEnabled(false);
            mMaxFrequencyPref.setEnabled(false);
        } else {
            currentFrequencies = currentFrequenciesLine.split(" ");

            // Max frequency
            mMaxFrequencyPref.setValue(currentFrequencies[1]);
            mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat, toMHz(currentFrequencies[1])));

            // Min frequency
            mMinFrequencyPref.setValue(currentFrequencies[0]);
            mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat, toMHz(currentFrequencies[0])));
        }

        String[] currentVoltages = new String[0];
        String currentVoltagesLine;

        // Disable the min/max voltage list if we dont have a list file
        if (!Helpers.fileExists(CUR_VOLT_FILE) || (currentVoltagesLine = Helpers.readOneLine(CUR_VOLT_FILE)) == null) {
            mMinFreqVoltPref.setEnabled(false);
            mMaxFreqVoltPref.setEnabled(false);

        } else {
            currentVoltages = currentVoltagesLine.split(" ");

            // Min frequency voltage
            mMinFreqVoltPref.setValue(currentVoltages[0]);
            mMinFreqVoltPref.setSummary(String.format(mMinFreqVoltFormat, toMV(currentVoltages[0])));

            // Max frequency voltage
            mMaxFreqVoltPref.setValue(currentVoltages[1]);
            mMaxFreqVoltPref.setSummary(String.format(mMaxFreqVoltFormat, toMV(currentVoltages[1])));
	}
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!getResources().getBoolean(R.bool.config_showPerformanceOnly)) {
            inflater.inflate(R.menu.gpu_settings_menu, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.app_settings:
                Intent intent = new Intent(context, PCSettings.class);
                startActivity(intent);
                break;
        }
        return true;
    }

    private void updateGpufreqValues() {
        String temp;
	String[] frequencies;

        if (Helpers.fileExists(CUR_FREQ_FILE) && (temp = Helpers.readOneLine(CUR_FREQ_FILE)) != null) {
            frequencies = temp.split(" ");
            mMinFrequencyPref.setValue(frequencies[0]);
            mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat, toMHz(frequencies[0])));
            mMaxFrequencyPref.setValue(frequencies[1]);
            mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat, toMHz(frequencies[1])));
        }
    }

    private void updateGpuVoltValues() {
        String temp;
	String[] voltages;

        if (Helpers.fileExists(CUR_VOLT_FILE) && (temp = Helpers.readOneLine(CUR_VOLT_FILE)) != null) {
            voltages = temp.split(" ");
            mMinFreqVoltPref.setValue(voltages[0]);
            mMinFreqVoltPref.setSummary(String.format(mMinFreqVoltFormat, toMV(voltages[0])));
            mMaxFreqVoltPref.setValue(voltages[1]);
            mMaxFreqVoltPref.setSummary(String.format(mMaxFreqVoltFormat, toMV(voltages[1])));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGpufreqValues();
        updateGpuVoltValues();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mGpuVpll) {
	    mGpuVpll.setSummary("***Reboot required*** Not working on Andoird 5.0 or above ...");

	    // check if DEFAULT_INITD_FILE exist
	    if (!Helpers.fileExists(DEFAULT_INITD_FILE)) {
		Log.e(TAG, "Can't locate default init.d file " + DEFAULT_INITD_FILE);
	    }
	    else {
		String temp = (mGpuVpll.isChecked()) ? "1":"0";
		// mount /system as rw
		cmd.su.runWaitFor(String.format(REMOUNT_CMD, "rw"));
		// replace the VPLL_VAL
		cmd.su.runWaitFor(String.format(REPLACE_CMD, "VPLL=.", "VPLL="+temp));
		// mount /system back to ro
		cmd.su.runWaitFor(String.format(REMOUNT_CMD, "ro"));
		Log.i(TAG, "vpll set to " + temp);
	    }
	    // @daniel, let super to handle checked state           return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, String key) {
        final SharedPreferences.Editor editor = sharedPreferences.edit();

        String fname = "";
	String temp;
	String[] items;

	if ((key.equals(FREQ_MIN_PREF)) || (key.equals(FREQ_MAX_PREF))) {
	    fname = CUR_FREQ_FILE;
	} else if ((key.equals(MIN_FREQ_VOLT_PREF)) || (key.equals(MAX_FREQ_VOLT_PREF))) {
	    fname = CUR_VOLT_FILE;
	}

	if (Helpers.fileExists(fname) && (temp = Helpers.readOneLine(fname)) != null) {
	    items = temp.split(" ");
            if (key.equals(FREQ_MIN_PREF)) {
		items[0] = mMinFrequencyPref.getValue();
                mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat, toMHz(items[0])));
	    }
            if (key.equals(FREQ_MAX_PREF)) {
		items[1] = mMaxFrequencyPref.getValue();
                mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat, toMHz(items[1])));
	    }
            if (key.equals(MIN_FREQ_VOLT_PREF)) {
		items[0] = mMinFreqVoltPref.getValue();
                mMinFreqVoltPref.setSummary(String.format(mMinFreqVoltFormat, toMV(items[0])));
	    }
            if (key.equals(MAX_FREQ_VOLT_PREF)) {
		items[1] = mMaxFreqVoltPref.getValue();
                mMaxFreqVoltPref.setSummary(String.format(mMaxFreqVoltFormat, toMV(items[1])));
	    }
	    if (Helpers.isSystemApp(getActivity())) {
		Helpers.writeOneLine(fname, items[0] +" "+ items[1]);
	    } else {
		cmd.su.runWaitFor("busybox echo "+items[0] +" "+ items[1]+" > " + fname);
	    }
	}
    }

    private String toMV(String mhzString) {
        return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" mV")
                .toString();
    }

    private String toMHz(String mhzString) {
        return new StringBuilder().append(mhzString).append(" MHz")
                .toString();
    }
}
