/*
 * Performance Control - An Android CPU Control application Copyright (C) 2012
 * James Roberts
 * Mali GPU & Tegra3 CPU support (http://github.com/danielhk) 2016/2/24
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

package com.brewcrewfoo.performance.activities;

import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceFrameLayout;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.brewcrewfoo.performance.R;
import com.brewcrewfoo.performance.fragments.Advanced;
import com.brewcrewfoo.performance.fragments.BatteryInfo;
import com.brewcrewfoo.performance.fragments.CPUInfo;
import com.brewcrewfoo.performance.fragments.CPUSettings;
import com.brewcrewfoo.performance.fragments.Gpu;
import com.brewcrewfoo.performance.fragments.DiskInfo;
import com.brewcrewfoo.performance.fragments.OOMSettings;
import com.brewcrewfoo.performance.fragments.TimeInState;
import com.brewcrewfoo.performance.fragments.Tegra3;
import com.brewcrewfoo.performance.fragments.Tools;
import com.brewcrewfoo.performance.fragments.VM;
import com.brewcrewfoo.performance.fragments.VoltageControlSettings;
import com.brewcrewfoo.performance.util.ActivityThemeChangeInterface;
import com.brewcrewfoo.performance.util.Constants;
import com.brewcrewfoo.performance.util.Helpers;
import com.brewcrewfoo.performance.widgets.CustomDrawerLayout;

import java.util.List;
import java.util.ArrayList;

public class MainActivity extends Activity implements Constants, ActivityThemeChangeInterface {

    //==================================
    // Static Fields
    //==================================
    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
    private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";
    private static final String PREF_IS_TABBED = "pref_is_tabbed";

    //==================================
    // Drawer
    //==================================
    private ActionBarDrawerToggle mDrawerToggle;
    private CustomDrawerLayout mDrawerLayout;
    private ListView mDrawerListView;
    private View mFragmentContainerView;
    private int mCurrentSelectedPosition = 0;
    private boolean mFromSavedInstanceState;
    private boolean mUserLearnedDrawer;

    //==================================
    // Fields
    //==================================
    private static boolean mGpuSupported;
    private static boolean mTegra3Supported;
    private static boolean mToolSupported;
    private static boolean mIsLightTheme;
    private static boolean mVoltageExists;
    private SharedPreferences mPreferences;
    private boolean mIsTabbed = true;
    private String[] mTitles;

    //==================================
    // Overridden Methods
    //==================================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVoltageExists = Helpers.voltageFileExists();

	mGpuSupported = Helpers.maliGpuExists();
        mTegra3Supported = Helpers.tegra3Exists();
	mToolSupported = !getResources().getBoolean(R.bool.config_showPerformanceOnly);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mUserLearnedDrawer = mPreferences.getBoolean(PREF_USER_LEARNED_DRAWER, false);

	setTheme();

        if (getResources().getBoolean(R.bool.config_allow_toggle_tabbed))
            mIsTabbed = mPreferences.getBoolean(PREF_IS_TABBED,
                    getResources().getBoolean(R.bool.config_use_tabbed));
        else
            mIsTabbed = getResources().getBoolean(R.bool.config_use_tabbed);

        mTitles = getTitles();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        if (savedInstanceState != null) {
            mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
            mFromSavedInstanceState = true;
        }

        if (!mIsTabbed) {
	    setContentView(R.layout.activity_main);

            mDrawerListView = (ListView) findViewById(R.id.pc_navigation_drawer);
            mDrawerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    selectItem(position);
                }
            });

            mDrawerListView.setAdapter(new ArrayAdapter<String>(
                    getActionBar().getThemedContext(),
                    android.R.layout.simple_list_item_1,
                    android.R.id.text1,
                    mTitles));
            mDrawerListView.setItemChecked(mCurrentSelectedPosition, true);

            setUpNavigationDrawer(
                    findViewById(R.id.pc_navigation_drawer),
                    (CustomDrawerLayout) findViewById(R.id.pc_drawer_layout));
        } else {
	    setContentView(R.layout.activity_main_tabbed);
	    ViewPager mViewPager = (ViewPager) findViewById(R.id.viewpager);
            TitleAdapter titleAdapter = new TitleAdapter(getFragmentManager());
            mViewPager.setAdapter(titleAdapter);
            mViewPager.setCurrentItem(0);

	    PagerTabStrip mPagerTabStrip = (PagerTabStrip) findViewById(R.id.pagerTabStrip);
            mPagerTabStrip.setTabIndicatorColor(getResources().getColor(R.color.pc_blue));
            mPagerTabStrip.setDrawFullUnderline(false);
        }

        checkForSu();

    }
/*
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }
*/
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mIsTabbed) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mIsTabbed) {
            outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
        }
    }

    @Override
    public boolean isThemeChanged() {
        final boolean is_light_theme = mPreferences.getBoolean(PREF_USE_LIGHT_THEME, false);
        return is_light_theme != mIsLightTheme;
    }

    @Override
    public void setTheme() {
        final boolean is_light_theme = mPreferences.getBoolean(PREF_USE_LIGHT_THEME, false);
        mIsLightTheme = is_light_theme;
        setTheme(is_light_theme ? R.style.Theme_Light : R.style.Theme_Dark);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isThemeChanged()) {
            Helpers.restartPC(this);
        }
    }

    //==================================
    // Methods
    //==================================

    /**
     * Users of this fragment must call this method to set up the
     * navigation menu_drawer interactions.
     *
     * @param fragmentContainerView The view of this fragment in its activity's layout.
     * @param drawerLayout          The DrawerLayout containing this fragment's UI.
     */
    public void setUpNavigationDrawer(View fragmentContainerView, CustomDrawerLayout drawerLayout) {
        mFragmentContainerView = fragmentContainerView;
        mDrawerLayout = drawerLayout;

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.drawable.ic_drawer,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        ) {

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                    return;
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                    return;
            }
        };

        // Remove or set it to true, if you want to use home to toggle the menu_drawer
        mDrawerToggle.setDrawerIndicatorEnabled(false);

        if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
            mDrawerLayout.openDrawer(mFragmentContainerView);
        }

        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        selectItem(mCurrentSelectedPosition);
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    /**
     * Restores the action bar after closing the menu_drawer
     */
/*
    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(this.getTitle());
    }

    private ActionBar getActionBar() {
        return this.getActionBar();
    }*/

    private void selectItem(int position) {
        mCurrentSelectedPosition = position;
        if (mDrawerListView != null) {
            mDrawerListView.setItemChecked(position, true);
        }
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mFragmentContainerView);
        }

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.pc_container, PlaceholderFragment.newInstance(getPosition(mTitles[position])))
                .commit();
    }

    /**
     * Gets the position of the item
     *
     * @param item
     *            The item
     * @return the item position
     */
    public int getPosition(String item) {
        if (item.equals(getString(R.string.t_cpu_settings))) {
            return FRAGMENT_ID_CPUSETTINGS;
        }
        if (item.equals(getString(R.string.t_tegra3_settings))) {
            return FRAGMENT_ID_TEGRA3SETTINGS;
        }
        if (item.equals(getString(R.string.t_gpu_settings))) {
            return FRAGMENT_ID_GPUSETTINGS;
        }
        if (item.equals(getString(R.string.t_battery_info))) {
            return FRAGMENT_ID_BATTERYINFO;
        }
        if (item.equals(getString(R.string.t_oom_settings))) {
            return FRAGMENT_ID_OOMSETTINGS;
        }
        if (item.equals(getString(R.string.prefcat_vm_settings))) {
            return FRAGMENT_ID_VM;
        }
        if (item.equals(getString(R.string.t_volt_settings))) {
            return FRAGMENT_ID_VOLTAGECONTROL;
        }
        if (item.equals(getString(R.string.t_adv_settings))) {
            return FRAGMENT_ID_ADVANCED;
        }
        if (item.equals(getString(R.string.t_time_in_state))) {
            return FRAGMENT_ID_TIMEINSTATE;
        }
        if (item.equals(getString(R.string.t_cpu_info))) {
            return FRAGMENT_ID_CPUINFO;
        }
        if (item.equals(getString(R.string.t_disk_info))) {
            return FRAGMENT_ID_DISKINFO;
        }
        if (item.equals(getString(R.string.t_tools))) {
            return FRAGMENT_ID_TOOLS;
        }
        return -1;
    }

    /**
     * Get a list of titles for the tabstrip to display depending on if the
     * voltage control fragment and battery fragment will be displayed. (Depends
     * on the result of Helpers.voltageTableExists() & Helpers.showBattery()
     *
     * @return String[] containing titles
     */
    private String[] getTitles() {
        List<String> titles = new ArrayList<String>();
        titles.add(getString(R.string.t_cpu_settings));
	if (mTegra3Supported)
            titles.add(getString(R.string.t_tegra3_settings));
	else if (mGpuSupported)
		titles.add(getString(R.string.t_gpu_settings));
        titles.add(getString(R.string.t_battery_info));
        titles.add(getString(R.string.t_adv_settings));
        titles.add(getString(R.string.t_time_in_state));
        titles.add(getString(R.string.t_cpu_info));
        titles.add(getString(R.string.t_disk_info));
        if (mVoltageExists) {
            titles.add(getString(R.string.t_volt_settings));
        }
        titles.add(getString(R.string.t_oom_settings));
        titles.add(getString(R.string.prefcat_vm_settings));
        if (mToolSupported) {
            titles.add(getString(R.string.t_tools));
	}
        return titles.toArray(new String[titles.size()]);
    }

    //==================================
    // Internal Classes
    //==================================

    /**
     * Loads our Fragments.
     */
    public static class PlaceholderFragment extends Fragment {

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static Fragment newInstance(int fragmentId) {
            Fragment fragment;
            switch (fragmentId) {
                default:
                case FRAGMENT_ID_CPUSETTINGS:
                    fragment = new CPUSettings();
                    break;
                case FRAGMENT_ID_GPUSETTINGS:
                //case FRAGMENT_ID_TEGRA3SETTINGS:
                    fragment = (mTegra3Supported)? new Tegra3() : new Gpu();
                    break;
                case FRAGMENT_ID_BATTERYINFO:
                    fragment = new BatteryInfo();
                    break;
                case FRAGMENT_ID_OOMSETTINGS:
                    fragment = new OOMSettings();
                    break;
                case FRAGMENT_ID_VM:
                    fragment = new VM();
                    break;
                case FRAGMENT_ID_VOLTAGECONTROL:
                    fragment = new VoltageControlSettings();
                    break;
                case FRAGMENT_ID_ADVANCED:
                    fragment = new Advanced();
                    break;
                case FRAGMENT_ID_TIMEINSTATE:
                    fragment = new TimeInState();
                    break;
                case FRAGMENT_ID_CPUINFO:
                    fragment = new CPUInfo();
                    break;
                case FRAGMENT_ID_DISKINFO:
                    fragment = new DiskInfo();
                    break;
                case FRAGMENT_ID_TOOLS:
                    fragment = new Tools();
                    break;
            }

            return fragment;
        }

        public PlaceholderFragment() {
            // intentionally left blank
        }
    }

    //==================================
    // Adapters
    //==================================
    class TitleAdapter extends FragmentPagerAdapter {
        String titles[] = getTitles();
        private Fragment frags[] = new Fragment[titles.length];

        public TitleAdapter(FragmentManager fm) {
            super(fm);
        	List<Fragment> frag = new ArrayList<Fragment>();
		frag.add(new CPUSettings());
		if (mTegra3Supported)
		    frag.add(new Tegra3());
		else if (mGpuSupported)
			frag.add(new Gpu());
		frag.add(new BatteryInfo());
		frag.add(new Advanced());
		frag.add(new TimeInState());
		frag.add(new CPUInfo());
		frag.add(new DiskInfo());
		if (mVoltageExists) {
		    frag.add(new VoltageControlSettings());
		}
		frag.add(new OOMSettings());
		frag.add(new VM());
                if (mToolSupported) {
                    frag.add(new Tools());
                }
		frags = frag.toArray(new Fragment[frag.size()]);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }

        @Override
        public Fragment getItem(int position) {
            return frags[position];
        }

        @Override
        public int getCount() {
            return frags.length;
        }
    }

    //==================================
    // Dialogs
    //==================================

    /**
     * Check if root access, and prompt the user to grant PC access
     */
    private void checkForSu() {
        if (Helpers.isSystemApp(this)) {
            return;
        }

        boolean firstrun = mPreferences.getBoolean("firstrun", true);
        boolean rootWasCanceled = mPreferences.getBoolean("rootcanceled", false);

        if (firstrun || rootWasCanceled) {
            SharedPreferences.Editor e = mPreferences.edit();
            e.putBoolean("firstrun", false);
            e.commit();
            launchFirstRunDialog();
        }
    }

    /**
     * Alert the user that a check for root will be run
     */
    private void launchFirstRunDialog() {
        String title = getString(R.string.first_run_title);
        final String failedTitle = getString(R.string.su_failed_title);
        LayoutInflater factory = LayoutInflater.from(this);
        final View firstRunDialog = factory.inflate(R.layout.su_dialog, null);
        TextView tv = (TextView) firstRunDialog.findViewById(R.id.message);
        tv.setText(R.string.first_run_message);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(firstRunDialog)
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String message = getString(R.string.su_cancel_message);
                                SharedPreferences.Editor e = mPreferences.edit();
                                e.putBoolean("rootcanceled", true);
                                e.commit();
                                suResultDialog(failedTitle, message);
                            }
                        })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean canSu = Helpers.checkSu();
                        boolean canBb = Helpers.checkBusybox();
                        if (canSu && canBb) {
                            String title = getString(R.string.su_success_title);
                            String message = getString(R.string.su_success_message);
                            SharedPreferences.Editor e = mPreferences.edit();
                            e.putBoolean("rootcanceled", false);
                            e.commit();
                            suResultDialog(title, message);
                        }
                        if (!canSu || !canBb) {
                            String message = getString(R.string.su_failed_su_or_busybox);
                            SharedPreferences.Editor e = mPreferences.edit();
                            e.putBoolean("rootcanceled", true);
                            e.commit();
                            suResultDialog(failedTitle, message);
                        }
                    }
                }).create().show();
    }

    /**
     * Display the result of the check for root access so the user knows what to
     * expect in respect to functionality of the application.
     *
     * @param title   Oops or OK depending on the result
     * @param message Success or fail message
     */
    private void suResultDialog(String title, String message) {
        LayoutInflater factory = LayoutInflater.from(this);
        final View suResultDialog = factory.inflate(R.layout.su_dialog, null);
        TextView tv = (TextView) suResultDialog.findViewById(R.id.message);
        tv.setText(message);
        new AlertDialog.Builder(this).setTitle(title).setView(suResultDialog)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).create().show();
    }
}

