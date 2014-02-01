package com.monster.keepconnection;

import java.util.HashSet;
import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.widget.Toast;

import com.monster.keepconnection.R;
import com.util.IabHelper;
import com.util.IabHelper.OnIabPurchaseFinishedListener;
import com.util.IabHelper.QueryInventoryFinishedListener;
import com.util.IabResult;
import com.util.Inventory;
import com.util.Purchase;

public class SettingActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnPreferenceClickListener {

	Set<BluetoothDevice> pairedDevices = new HashSet<BluetoothDevice>();
	public final static String StartFromActivity = "StartFromActivity";

	final static int BuyFullVersionRequestCode = 721010;
	final static String IABRequestCode = "FullVersionID123456789";
	final static String IABKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAutzaL2p34g8tkLuySwac0dUT30sR5s61nhI02VITWJSdxZ3y4P6NW1vb8d9a+6dfZYpzYQPkebKVwlvJYFG7xwPeHcqyqCNc5EWa3hPaVbPHfeUrM/AI/pe/Go1LeniZpt27M0A7rUckEDryI+W5Eqp1d9+b0ie3L2aUzKKEKQGa+RDPGfXlVD7zuPuIyZZtgwzu2IDz8SZkBGTYQnbZe4vVetw0o/Vz7g4b3XPeGEYxYlpyj3K5yT93u2T2iUKfdRBHapx3p23xWrA0Ojh+GCBHAn0Jr/X83BqtnPGssrIdUHsZdo5KokQbieqOm6OCfgCulqejbqdGqKsECqj0qQIDAQAB";
	final static String FullVersionID = "dontleave.full.version";

	IabHelper mHelper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		BluetoothAdapter.getDefaultAdapter().enable();
		hCheckBtDeviceStatus.sendEmptyMessage(0);
		addPreferencesFromResource(R.xml.setting_preference);
		reStartService();

		if (!getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false)) {
			releaseIabHelper();
			if (mHelper == null)
				mHelper = new IabHelper(this, IABKey);
			mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
				public void onIabSetupFinished(IabResult result) {
					if (!result.isSuccess()) {
						releaseIabHelper();
						ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_failed), Toast.LENGTH_SHORT);
						return;
					}
					mHelper.queryInventoryAsync(new QueryInventoryFinishedListener() {
						@Override
						public void onQueryInventoryFinished(IabResult result, Inventory inv) {
							if (result.isFailure()) {
								releaseIabHelper();
								ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_failed), Toast.LENGTH_SHORT);
								return;
							}
							if (inv.getPurchase(FullVersionID) != null) {
								getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version), true).commit();
								ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_fullversion), Toast.LENGTH_LONG);
								hSetupDefaultData.sendEmptyMessage(0);
								releaseIabHelper();
							}
						}
					});
				}
			});
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		hSetupDefaultData.sendEmptyMessage(0);
	}

	Handler hCheckBtDeviceStatus = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (BluetoothAdapter.getDefaultAdapter() != null) {
				pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
				ListPreference listPref = (ListPreference) findPreference(getString(R.string.key_setting_select_btdevice));
				if (pairedDevices.size() > 0) {
					listPref.setSummary("");
					String[] entryDeviceValues = new String[pairedDevices.size()];
					String[] entryDevice = new String[pairedDevices.size()];
					int i = 0;
					for (BluetoothDevice device : pairedDevices) {
						entryDeviceValues[i] = device.getAddress();
						entryDevice[i] = device.getName();
						if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_address), "").equalsIgnoreCase(device.getAddress()))
							listPref.setSummary(device.getName());
						i++;
					}
					listPref.setEntries(entryDevice);
					listPref.setEntryValues(entryDeviceValues);
					listPref.setOnPreferenceChangeListener(SettingActivity.this);
					listPref.setEnabled(true);
					hCheckBtDeviceStatus.removeCallbacksAndMessages(null);
				} else {
					listPref.setEnabled(false);
					hCheckBtDeviceStatus.sendMessageDelayed(new Message().obtain(), 500);
				}
			}

		}
	};

	Handler hSetupDefaultData = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			SetDefaultData();
		}
	};

	void SetDefaultData() {
		String[] OptionString = getResources().getStringArray(R.array.warning_option_string);
		String[] OptionValue = getResources().getStringArray(R.array.warning_option_value);

		CheckBoxPreference cbPref = (CheckBoxPreference) findPreference(getString(R.string.key_setting_auto_start));
		cbPref.setChecked(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_setting_auto_start), false));
		cbPref.setOnPreferenceChangeListener(this);
		cbPref.setEnabled(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false));

		Preference pref = (Preference) findPreference(getString(R.string.key_setting_support_me));
		if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false)) {
			if (pref != null)
				((PreferenceGroup) findPreference(getString(R.string.key_setting_title))).removePreference(pref);
		} else {
			pref.setOnPreferenceClickListener(this);
		}

		ListPreference listPref = (ListPreference) findPreference(getString(R.string.key_notify_audio));
		for (int i = 0; i < OptionValue.length; i++) {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_warning_audio), "0").equalsIgnoreCase(OptionValue[i])) {
				listPref.setSummary(OptionString[i]);
				break;
			}
		}
		listPref.setOnPreferenceChangeListener(this);
		listPref.setEnabled(true);

		listPref = (ListPreference) findPreference(getString(R.string.key_notify_vibrate));
		for (int i = 0; i < OptionValue.length; i++) {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_warning_vibrator), "0").equalsIgnoreCase(OptionValue[i])) {
				listPref.setSummary(OptionString[i]);
				break;
			}
		}
		listPref.setOnPreferenceChangeListener(this);
		listPref.setEnabled(true);

		listPref = (ListPreference) findPreference(getString(R.string.key_notify_flash));
		for (int i = 0; i < OptionValue.length; i++) {
			String sss = getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_warning_flash), "0");
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_warning_flash), "0").equalsIgnoreCase(OptionValue[i])) {
				listPref.setSummary(OptionString[i]);
				break;
			}
		}
		listPref.setOnPreferenceChangeListener(this);
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
			listPref.setEnabled(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false));
		} else {
			listPref.setEnabled(false);
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.pref_warning_flash), "0").commit();
		}

		cbPref = (CheckBoxPreference) findPreference(getString(R.string.key_notify_screen));
		cbPref.setChecked(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_warning_screen), false));
		cbPref.setOnPreferenceChangeListener(this);
		cbPref.setEnabled(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false));

		cbPref = (CheckBoxPreference) findPreference(getString(R.string.key_notify_popwindow));
		cbPref.setChecked(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_warning_popwindow), false));
		cbPref.setOnPreferenceChangeListener(this);
		cbPref.setEnabled(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false));
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (getString(R.string.key_setting_select_btdevice).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.pref_setting_bt_device_address), (String) newValue).commit();

			for (BluetoothDevice device : pairedDevices) {
				if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_address), "").equalsIgnoreCase(device.getAddress())) {
					((ListPreference) findPreference(getString(R.string.key_setting_select_btdevice))).setSummary(device.getName());
					getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.pref_setting_bt_device_name), device.getName()).commit();
					break;
				}
			}
			reStartService();
		} else if (getString(R.string.key_setting_auto_start).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.pref_setting_auto_start), (Boolean) newValue).commit();
			reStartService();
		} else if (getString(R.string.key_notify_audio).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.pref_warning_audio), (String) newValue).commit();
		} else if (getString(R.string.key_notify_flash).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.pref_warning_flash), (String) newValue).commit();
		} else if (getString(R.string.key_notify_popwindow).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.pref_warning_popwindow), (Boolean) newValue).commit();
		} else if (getString(R.string.key_notify_screen).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.pref_warning_screen), (Boolean) newValue).commit();
		} else if (getString(R.string.key_notify_vibrate).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.pref_warning_vibrator), (String) newValue).commit();
		}
		hSetupDefaultData.sendEmptyMessage(0);
		return true;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (getString(R.string.key_setting_support_me).equals(preference.getKey())) {
			releaseIabHelper();
			if (mHelper == null)
				mHelper = new IabHelper(this, IABKey);

			mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
				public void onIabSetupFinished(IabResult result) {
					if (!result.isSuccess()) {
						releaseIabHelper();
						ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_failed), Toast.LENGTH_SHORT);
					} else {
						mHelper.launchPurchaseFlow(SettingActivity.this, FullVersionID, BuyFullVersionRequestCode, new OnIabPurchaseFinishedListener() {
							@Override
							public void onIabPurchaseFinished(IabResult result, Purchase info) {
								if (result.isFailure()) {
									if (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
										getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version), true).commit();
										ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_fullversion), Toast.LENGTH_LONG);
									} else {
										getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version), false).commit();
										ToastUiThread(SettingActivity.this, result.getMessage(), Toast.LENGTH_SHORT);
									}
								} else if ((result.isSuccess()) && (info.getSku().equals(FullVersionID))) {
									getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version), true).commit();
									ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_fullversion), Toast.LENGTH_LONG);
								} else {
									getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version), false).commit();
									ToastUiThread(SettingActivity.this, result.getMessage(), Toast.LENGTH_SHORT);
								}
								releaseIabHelper();
								hSetupDefaultData.sendEmptyMessage(0);
							}
						}, IABRequestCode);
					}
				}
			});
		}
		return true;
	}

	public void ToastUiThread(final Context context, final String strMessage, final int duration) {
		runOnUiThread(new Runnable() {
			public void run() {
				Toast.makeText(context, strMessage, duration).show();
			}
		});
	}

	private void releaseIabHelper() {
		if (mHelper != null)
			mHelper.dispose();
		mHelper = null;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		releaseIabHelper();
	}

	public void reStartService() {
		stopService(new Intent(SettingActivity.this, MonitorDeviceService.class));
		if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_address), "").length() == 0) {
			Toast.makeText(SettingActivity.this, R.string.warning_no_btdevice, Toast.LENGTH_SHORT).show();
		} else {
			startService(new Intent(SettingActivity.this, MonitorDeviceService.class).putExtra(StartFromActivity, true));
		}
	}
}