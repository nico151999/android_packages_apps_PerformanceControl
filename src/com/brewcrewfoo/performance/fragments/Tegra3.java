/*
 * Performance Control - An Android CPU Control application Copyright (C) 2012
 * Jared Rummler Copyright (C) 2012 James Roberts
 * Tegra3 CPU support (http://github.com/danielhk) 2016/2/24
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.brewcrewfoo.performance.fragments;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.brewcrewfoo.performance.R;
import com.brewcrewfoo.performance.activities.PCSettings;
import com.brewcrewfoo.performance.util.CMDProcessor;
import com.brewcrewfoo.performance.util.Helpers;
import com.brewcrewfoo.performance.util.Voltage;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.brewcrewfoo.performance.util.Constants.*;

public class Tegra3 extends PreferenceFragment
	implements OnSharedPreferenceChangeListener {

    private static boolean DEBUG = false;

    public static final int DIALOG_EDIT_VOLT = 0;
    public static final int MAX_VOLTAGE_PREF = 9;
    public static final int MIN_BL_LOWEST = 1;
    public static final int MIN_BL_HIGHEST = 50;
    public static final int MAX_BL_LOWEST = 100;
    public static final int MAX_BL_HIGHEST = 255;

    //private ListAdapter mAdapter;
    private List<Voltage> mVoltages;
    private SharedPreferences mPreferences;
    private Context context;

    private EditTextPreference mPanelMinBLPref;
    private EditTextPreference mPanelMaxBLPref;
    private String mMinBacklight;
    private String mMaxBacklight;

    private List<Voltage> mGpuVoltages;
    private List<Voltage> mLpVoltages;
    private List<Voltage> mEmcVoltages;
    private String mGpuMaxFreq;
    private Preference mGpuMaxFreqPref;
    private List<Preference> mGpuVoltagePrefs = null;
    private List<Preference> mLpVoltagePrefs = null;
    private List<Preference> mEmcVoltagePrefs = null;

    private CheckBoxPreference mGpuVoltageSOB;
    private CheckBoxPreference mLpVoltageSOB;
    private CheckBoxPreference mEmcVoltageSOB;

    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	context = getActivity();
	mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
	mPreferences.registerOnSharedPreferenceChangeListener(this);
	addPreferencesFromResource(R.xml.tegra3_settings);

	mGpuMaxFreqPref = findPreference(PREF_GPU_MAX_FREQ);
	mGpuMaxFreq = Helpers.readOneLine(TEGRA_GPU_MAX_FREQ_PATH);
	mGpuMaxFreqPref.setSummary(mGpuMaxFreq + "  MHz");

	mGpuVoltageSOB = (CheckBoxPreference) findPreference(PREF_GPU_VOLT_SOB);
	mLpVoltageSOB = (CheckBoxPreference) findPreference(PREF_LP_VOLT_SOB);
	mEmcVoltageSOB = (CheckBoxPreference) findPreference(PREF_EMC_VOLT_SOB);

	PreferenceCategory hideCat = (PreferenceCategory) findPreference("category_panel_backlight");
	if (!Helpers.fileExists(TEGRA_MIN_BACKLIGHT_PATH) ||
	    !Helpers.fileExists(TEGRA_MAX_BACKLIGHT_PATH)) {
	    getPreferenceScreen().removePreference(hideCat);
	}
	else {
	    mPanelMinBLPref = (EditTextPreference) findPreference(PREF_MIN_BACKLIGHT);
	    mMinBacklight = Helpers.readOneLine(TEGRA_MIN_BACKLIGHT_PATH);
	    UpdateBackLight(true, mMinBacklight);
	    mPanelMaxBLPref = (EditTextPreference) findPreference(PREF_MAX_BACKLIGHT);
	    mMaxBacklight = Helpers.readOneLine(TEGRA_MAX_BACKLIGHT_PATH);
	    UpdateBackLight(false, mMaxBacklight);
	}
	hideCat = (PreferenceCategory) findPreference("category_gpu_voltage");
	if (!Helpers.fileExists(TEGRA_GPU_UV_MV_PATH)) {
	    getPreferenceScreen().removePreference(hideCat);
	}
	else {
	    mGpuVoltagePrefs = new ArrayList<Preference>();
	    mGpuVoltages = readVoltages(TEGRA_GPU_UV_MV_PATH);
	    String lastFreq = "";
	    for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		Preference pref = findPreference(PREF_GPU_UV_MV+Integer.toString(i));
		mGpuVoltagePrefs.add(pref);
		String freq = mGpuVoltages.get(i).getFreq();
		String volt = mGpuVoltages.get(i).getCurrentMv();
		pref.setTitle(freq + "MHz: " + volt + "mV");
		if (!lastFreq.equals("") && lastFreq.equals(freq)) {
		    hideCat.removePreference(mGpuVoltagePrefs.get(i-1));
		    mGpuVoltagePrefs.set(i-1, null);
		}
		else {
		    if (freq.equals("0")) {
			hideCat.removePreference(pref);
			mGpuVoltagePrefs.set(i, null);
		    }
		    lastFreq = freq;
		}
	    }
	}
	hideCat = (PreferenceCategory) findPreference("category_lp_voltage");
	if (!Helpers.fileExists(TEGRA_LP_UV_MV_PATH)) {
	    getPreferenceScreen().removePreference(hideCat);
	}
	else {
	    mLpVoltagePrefs = new ArrayList<Preference>();
	    mLpVoltages = readVoltages(TEGRA_LP_UV_MV_PATH);
	    String lastFreq = "";
	    for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		Preference pref = findPreference(PREF_LP_UV_MV+Integer.toString(i));
		mLpVoltagePrefs.add(pref);
		String freq = mLpVoltages.get(i).getFreq();
		String volt = mLpVoltages.get(i).getCurrentMv();
		pref.setTitle(freq + "MHz: " + volt + "mV");
		if (!lastFreq.equals("") && lastFreq.equals(freq)) {
		    hideCat.removePreference(mLpVoltagePrefs.get(i-1));
		    mLpVoltagePrefs.set(i-1, null);
		}
		else {
		    lastFreq = freq;
		}
	    }
	}
	hideCat = (PreferenceCategory) findPreference("category_emc_voltage");
	if (!Helpers.fileExists(TEGRA_EMC_UV_MV_PATH)) {
	    getPreferenceScreen().removePreference(hideCat);
	}
	else {
	    mEmcVoltagePrefs = new ArrayList<Preference>();
	    mEmcVoltages = readVoltages(TEGRA_EMC_UV_MV_PATH);
	    String lastFreq = "";
	    for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		Preference pref = findPreference(PREF_EMC_UV_MV+Integer.toString(i));
		mEmcVoltagePrefs.add(pref);
		String freq = mEmcVoltages.get(i).getFreq();
		String volt = mEmcVoltages.get(i).getCurrentMv();
		pref.setTitle(freq + "MHz: " + volt + "mV");
		if (!lastFreq.equals("") && lastFreq.equals(freq)) {
		    hideCat.removePreference(mEmcVoltagePrefs.get(i-1));
		    mEmcVoltagePrefs.set(i-1, null);
		}
		else {
		    lastFreq = freq;
		}
	    }
	}

	setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	inflater.inflate(R.menu.tegra3_settings_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	switch (item.getItemId()) {
	    case R.id.restore:
		String message = getString(R.string.warning_restore);
		ConfirmRestore(message);
		break;
	}
	return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
	int i;
	final CMDProcessor cmd = new CMDProcessor();
	if ((preference == mGpuVoltageSOB) ||
	    (preference == mLpVoltageSOB) ||
	    (preference == mEmcVoltageSOB)) {
	    CheckBoxPreference cp = (CheckBoxPreference) preference;
	    if (cp.isChecked()) {
		ConfirmSOB(getString(R.string.volt_info), cp);
		return true;
	    }
	} else if (preference == mGpuMaxFreqPref) {
            String title = getString(R.string.gpu_max_freq_title);
	    editDialog(title, mGpuMaxFreq, null, preference, null, TEGRA_GPU_MAX_FREQ_PATH);
	    return true;
	} else {
	    i = checkVoltagePref(mGpuVoltagePrefs, preference);
	    if (i >= 0) {
		String freq = mGpuVoltages.get(i).getFreq();
		String volt = mGpuVoltages.get(i).getCurrentMv();
		String title = "GPU:" + freq + getString(R.string.ps_volt_mhz_voltage);
		editDialog(title, volt, freq, preference, mGpuVoltages, TEGRA_GPU_UV_MV_PATH);
		return true;
	    } else {
		i = checkVoltagePref(mLpVoltagePrefs, preference);
		if (i >= 0) {
		    String freq = mLpVoltages.get(i).getFreq();
		    String volt = mLpVoltages.get(i).getCurrentMv();
		    String title = "LP CORE:" + freq + getString(R.string.ps_volt_mhz_voltage);
		    editDialog(title, volt, freq, preference, mLpVoltages, TEGRA_LP_UV_MV_PATH);
	    	    return true;
		} else {
		    i = checkVoltagePref(mEmcVoltagePrefs, preference);
		    if (i >= 0) {
			String freq = mEmcVoltages.get(i).getFreq();
			String volt = mEmcVoltages.get(i).getCurrentMv();
        		String title = "eMMC:" + freq + getString(R.string.ps_volt_mhz_voltage);
			editDialog(title, volt, freq, preference, mEmcVoltages, TEGRA_EMC_UV_MV_PATH);
			return true;
		    }
		}
	    }
	}
	return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, String key) {
	final SharedPreferences.Editor editor = sharedPreferences.edit();
	if (key.equals(PREF_GPU_MAX_SOB)) {
	    if (sharedPreferences.getBoolean(key, false)) {
		editor.putString(PREF_GPU_MAX_FREQ,
				Helpers.readOneLine(TEGRA_MAX_FREQ_PATH)).apply();
	    } else {
		editor.remove(PREF_GPU_MAX_FREQ).apply();
	    }
	} else if (key.equals(PREF_GPU_VOLT_SOB)) {
	    if (sharedPreferences.getBoolean(key, false)) {
		for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		    Preference pref = mGpuVoltagePrefs.get(i);
		    if (pref != null) {
			editor.putString(pref.getKey(),
				mGpuVoltages.get(i).getCurrentMv()).apply();
		    }
		}
	    }
	    else {
		for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		    Preference pref = mGpuVoltagePrefs.get(i);
		    if (pref != null) {
			editor.remove(pref.getKey()).apply();
		    }
		}
	    }
	} else if (key.equals(PREF_LP_VOLT_SOB)) {
	    if (sharedPreferences.getBoolean(key, false)) {
		for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		    Preference pref = mLpVoltagePrefs.get(i);
		    if (pref != null) {
			editor.putString(pref.getKey(),
				mLpVoltages.get(i).getCurrentMv()).apply();
		    }
		}
	    }
	    else {
		for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		    Preference pref = mLpVoltagePrefs.get(i);
		    if (pref != null) {
			editor.remove(pref.getKey()).apply();
		    }
		}
	    }
	} else if (key.equals(PREF_EMC_VOLT_SOB)) {
	    if (sharedPreferences.getBoolean(key, false)) {
		for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		    Preference pref = mEmcVoltagePrefs.get(i);
		    if (pref != null) {
			editor.putString(pref.getKey(),
				mEmcVoltages.get(i).getCurrentMv()).apply();
		    }
		}
	    }
	    else {
		for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
		    Preference pref = mEmcVoltagePrefs.get(i);
		    if (pref != null) {
			editor.remove(pref.getKey()).apply();
		    }
		}
	    }
	} else if (key.equals(PREF_BACKLIGHT_SOB)) {
	    if (sharedPreferences.getBoolean(key, false)) {
		editor.putString(PREF_MIN_BACKLIGHT, mMinBacklight)
		      .putString(PREF_MAX_BACKLIGHT, mMaxBacklight).apply();
	    } else {
		editor.remove(PREF_MIN_BACKLIGHT)
		      .remove(PREF_MAX_BACKLIGHT).apply();
	    }
	} else if (key.equals(PREF_MIN_BACKLIGHT)) {
	    UpdateBackLight(true, mPanelMinBLPref.getText());
	} else if (key.equals(PREF_MAX_BACKLIGHT)) {
	    UpdateBackLight(false, mPanelMaxBLPref.getText());
	}
    }

    private void ConfirmRestore(String message) {
	new AlertDialog.Builder(context)
	    .setMessage(message)
	    .setNegativeButton(getString(R.string.cancel),
		new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		    }
		})
	    .setPositiveButton(getString(R.string.ok),
		new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			RestoreDefaultVolt();
		    }
		}).create().show();
    }

    private void ConfirmSOB(String message, final CheckBoxPreference pref) {
	new AlertDialog.Builder(context)
	    .setMessage(message)
	    .setNegativeButton(getString(R.string.cancel),
		new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			pref.setChecked(false);
			final SharedPreferences.Editor editor = mPreferences.edit();
			editor.putBoolean(pref.getKey(), false);
			editor.commit();
		    }
		})
	    .setPositiveButton(getString(R.string.ok),
		new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			pref.setChecked(true);
			final SharedPreferences.Editor editor = mPreferences.edit();
			editor.putBoolean(pref.getKey(), true);
			editor.commit();
		    }
		}).create().show();
    }

    private void UpdateTitles() {
	final SharedPreferences.Editor editor = mPreferences.edit();
	for (int i=0;i< MAX_VOLTAGE_PREF;i++) {
	    Preference pref = mGpuVoltagePrefs.get(i);
	    if (pref != null) {
		String freq = mGpuVoltages.get(i).getFreq();
		String volt = mGpuVoltages.get(i).getCurrentMv();
		pref.setTitle(freq + "MHz: " + volt + "mV");
		editor.putString(pref.getKey(), volt).apply();
	    }
	    pref = mLpVoltagePrefs.get(i);
	    if (pref != null) {
		String freq = mLpVoltages.get(i).getFreq();
		String volt = mLpVoltages.get(i).getCurrentMv();
		pref.setTitle(freq + "MHz: " + volt + "mV");
		editor.putString(pref.getKey(), volt).apply();
	    }
	    pref = mEmcVoltagePrefs.get(i);
	    if (pref != null) {
		String freq = mEmcVoltages.get(i).getFreq();
		String volt = mEmcVoltages.get(i).getCurrentMv();
		pref.setTitle(freq + "MHz: " + volt + "mV");
		editor.putString(pref.getKey(), volt).apply();
	    }
	}
    }

    private void RestoreDefaultVolt() {
	for (final Voltage volt : mGpuVoltages) {
	    volt.setCurrentMV(volt.getSavedMV());
	}
	setVoltages(mGpuVoltages, null, null, TEGRA_GPU_UV_MV_PATH);
	for (final Voltage volt : mLpVoltages) {
	    volt.setCurrentMV(volt.getSavedMV());
	}
	setVoltages(mLpVoltages, null, null, TEGRA_LP_UV_MV_PATH);
	for (final Voltage volt : mEmcVoltages) {
	    volt.setCurrentMV(volt.getSavedMV());
	}
	setVoltages(mEmcVoltages, null, null, TEGRA_EMC_UV_MV_PATH);
	UpdateTitles();
    }

    private void UpdateBackLight(boolean min, final String value) {
	int val = Integer.parseInt(value);
	final CMDProcessor cmd = new CMDProcessor();
	if (min) {
	    if (val < MIN_BL_LOWEST) val = MIN_BL_LOWEST;
	    if (val > MIN_BL_HIGHEST) val = MIN_BL_HIGHEST;
	    mMinBacklight = Integer.toString(val);
	    cmd.su.runWaitFor("busybox echo "+mMinBacklight+" > "+TEGRA_MIN_BACKLIGHT_PATH);
	    mPanelMinBLPref.setText(mMinBacklight);
	    String title = getString(R.string.panel_min_title) + ": " + mMinBacklight;
	    mPanelMinBLPref.setTitle(title);
	    final SharedPreferences.Editor editor = mPreferences.edit();
	    editor.putString(PREF_MIN_BACKLIGHT, mMinBacklight);
	    editor.commit();
	}
	else {
	    if (val < MAX_BL_LOWEST) val = MAX_BL_LOWEST;
	    if (val > MAX_BL_HIGHEST) val = MAX_BL_HIGHEST;
	    mMaxBacklight = Integer.toString(val);
	    cmd.su.runWaitFor("busybox echo "+mMaxBacklight+" > "+TEGRA_MAX_BACKLIGHT_PATH);
	    mPanelMaxBLPref.setText(mMaxBacklight);
	    String title = getString(R.string.panel_max_title) + ": " + mMaxBacklight;
	    mPanelMaxBLPref.setTitle(title);
	    final SharedPreferences.Editor editor = mPreferences.edit();
	    editor.putString(PREF_MAX_BACKLIGHT, mMaxBacklight);
	    editor.commit();
	}
    }

    private static final int[] VOLT_STEPS = new int[]{600, 625, 650, 675, 700,
	    725, 750, 775, 800, 825, 850, 875, 900, 925, 950, 975, 1000, 1025,
	    1050, 1075, 1100, 1125, 1150, 1175, 1200, 1225, 1250, 1275, 1300,
	    1325, 1350, 1375, 1400, 1425, 1450, 1475, 1500, 1525, 1550, 1575,
	    1600};

    private static final int[] FREQ_STEPS = new int[]{416, 426, 436, 446, 456,
	    466, 476, 486, 496, 506, 516, 526, 536, 546, 556, 566, 576, 586,
	    596, 606, 616, 626, 636, 646, 656, 666, 676, 686, 696,
	    706, 716, 726, 736, 746, 756, 766};

    private static int getNearestStepIndex(final int value, final int[] STEPS) {
	int index = 0;
	for (int STEP : STEPS) {
	    if (value > STEP) index++;
	    else break;
	}
	return index;
    }

    protected void editDialog(String title, final String currentValue, final String freq,
			final Preference pref, final List<Voltage> volts, final String path) {
	AlertDialog dialog = null;

	final LayoutInflater factory = LayoutInflater.from(context);
	final View voltageDialog = factory.inflate(R.layout.voltage_dialog, null);

	final EditText voltageEdit = (EditText) voltageDialog.findViewById(R.id.voltageEdit);
	final SeekBar voltageSeek = (SeekBar) voltageDialog.findViewById(R.id.voltageSeek);
	final TextView voltageMeter = (TextView) voltageDialog.findViewById(R.id.voltageMeter);
	final int[] STEPS = (freq == null) ? FREQ_STEPS : VOLT_STEPS;
	final String unit = (freq == null) ? " MHz" : " mV";
	final int currentVal = Integer.parseInt(currentValue);
	voltageEdit.setText(currentValue);
	voltageEdit.addTextChangedListener(new TextWatcher() {
	    @Override
	    public void afterTextChanged(Editable arg0) {
	    }

	    @Override
	    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
	    }

	    @Override
	    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
		String text = voltageEdit.getText().toString();
		int value = 0;
		try {
		    value = Integer.parseInt(text);
		    if (value > STEPS[STEPS.length - 1]) {
			value = STEPS[STEPS.length - 1];
			text = String.valueOf(value);
			voltageEdit.setText(text);
		    }
		} catch (NumberFormatException nfe) {
		    return;
		}
		voltageMeter.setText(text + unit);
		final int index = getNearestStepIndex(value, STEPS);
		voltageSeek.setProgress(index);
	    }

	});

	voltageMeter.setText(currentValue + unit);
	voltageSeek.setMax(STEPS.length-1);
	voltageSeek.setProgress(getNearestStepIndex(currentVal, STEPS));
	voltageSeek
		.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
		    @Override
		    public void onProgressChanged(SeekBar sb, int progress,
						  boolean fromUser) {
			if (fromUser) {
			    final String volt = Integer.toString(STEPS[progress]);
			    voltageMeter.setText(volt + unit);
			    voltageEdit.setText(volt);
			}
		    }

		    @Override
		    public void onStartTrackingTouch(SeekBar seekBar) {
			//
		    }

		    @Override
		    public void onStopTrackingTouch(SeekBar seekBar) {
			//
		    }

		});

	dialog = new AlertDialog.Builder(context)
		.setTitle(title)
		.setView(voltageDialog)
		.setPositiveButton(
		    getResources().getString(R.string.ps_volt_save),
		    new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			    dialog.cancel();
			    final String value = voltageEdit.getText().toString();
			    if (!value.equals(currentValue)) {	// do nothing if value no change
				if (freq == null) {
				// write Max Freq to sysfs
				    mGpuMaxFreq = value;
				    new CMDProcessor().su.runWaitFor("busybox echo "+value+" > "+path);
				    pref.setSummary(value + "  MHz");
				} else {
				    setVoltages(volts, freq, value, path);
				    pref.setTitle(freq + "MHz: " + value + " mV");
				}
				final SharedPreferences.Editor editor = mPreferences.edit();
				editor.putString(pref.getKey(), value);
				editor.commit();
				if (DEBUG)
				    Log.d(TAG,"putString:"+pref.getKey()+"="+value);
			    }
			}
		    })
		.setNegativeButton(getString(R.string.cancel),
		    new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			    dialog.cancel();
			}
		    }).create();

	if (dialog != null) {
	    dialog.show();
	}
    }

    private int checkVoltagePref(List<Preference> prefs, Preference pref) {
	if (prefs == null) return -1;
	return prefs.indexOf(pref);
    }

    public void setVoltages(List<Voltage> volts, String freq, String value, String path) {
	// setCurrentMV(volt) if = freq
	// write the voltage to sysfs
	if (volts == null) return;
	final StringBuilder sb = new StringBuilder();
	sb.append("busybox echo ");
	for (final Voltage volt : volts) {
	    if ((freq != null) && volt.getFreq().equals(freq))
		volt.setCurrentMV(value);
	    sb.append(volt.getCurrentMv()).append(" ");
	}
	sb.append("> ").append(path);
	Helpers.shExec(sb, context, true);
    }

    public static List<Voltage> readVoltages(String prefpath) {
	// this is our getVolts()
	final List<Voltage> volts = new ArrayList<Voltage>();
	try {
	    BufferedReader br = new BufferedReader(new FileReader(prefpath), 256);
	    String line = "";
	    while ((line = br.readLine()) != null) {
		final String[] values = line.split("\\s+");
		if (values != null) {
		    if (values.length >= 2) {
			final String freq = values[0].replace("mhz:", "");
			final String currentMv = values[1];
			final String savedMv = currentMv;
			final Voltage voltage = new Voltage();
			voltage.setFreq(freq);
			voltage.setCurrentMV(currentMv);
			voltage.setSavedMV(savedMv);
			volts.add(voltage);
			if (DEBUG)
			    Log.d(TAG,"readVoltages:add "+freq+"MHz="+currentMv);
		    }
		}
	    }
	    br.close();
	} catch (FileNotFoundException e) {
	    Log.d(TAG, prefpath + " does not exist");
	} catch (IOException e) {
	    Log.d(TAG, "Error reading " + prefpath);
	}
	return volts;
    }
}

