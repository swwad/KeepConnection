package com.monster.keepconnection;

import android.content.Context;
import android.content.Intent;
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
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.util.IabHelper;
import com.util.IabHelper.OnIabPurchaseFinishedListener;
import com.util.IabHelper.QueryInventoryFinishedListener;
import com.util.IabResult;
import com.util.Inventory;
import com.util.Purchase;

public class SettingActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnPreferenceClickListener {

	public final static String StartFromActivity = "StartFromActivity";

	final static int BuyFullVersionRequestCode = 101072;
	final static String IABRequestCode = "FullVersionID987654321";
	final static String IABKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjMeAGUaTE6wd+Eln+0FcxZSZ7eo2aqcPLMuQu1J3FSMzPrrn8NskHJayJwTokq8cqy8DP/SsyQ5A87FRz3N0MjOrtO7BXB0JJQsu3/7a3NzphMuDImWCohtU6QcWJIhZGrRT9XCjzFDY46WB6JQGeR280IVOFf2CMECQRsp0ujaB+SbBpZ4Nlkzr2l35G+t8Z4rsJWgrMS0ht8Y7RXH1QaDU2+zHgDxGQVocbmE+U3UdnHKSTAW/yy82VIZJKRQsJhcXBXKisaf8Fig2bA6ryBaDYYZVeIB4QGkI8Ojm5FCRx4POCf6XsIL1GfZg0IPUTpHxYH2bQ5Xvwhq4pBRzrQIDAQAB";
	final static String FullVersionID = "keep.connection.full.version";

	IabHelper mHelper;
	private AdView adView;
	private final static String MY_AD_UNIT_ID = "ca-app-pub-2487183748401331/4433379406";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		reStartService();
		if (!getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false)) {

			setContentView(R.layout.activity_setting);

			adView = new AdView(this);
			adView.setAdUnitId(MY_AD_UNIT_ID);
			adView.setAdSize(AdSize.BANNER);
			((LinearLayout) findViewById(R.id.llAd)).addView(adView);
			AdRequest adRequest = new AdRequest.Builder().build();
			adView.loadAd(adRequest);

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
								ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_failed), Toast.LENGTH_SHORT);
								releaseIabHelper();
								return;
							}
							if ((inv.getPurchase(FullVersionID) != null) && inv.getPurchase(FullVersionID).getPurchaseState() == 0) {
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
		addPreferencesFromResource(R.xml.setting_preference);
		getListView().setItemsCanFocus(true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		hSetupDefaultData.sendEmptyMessage(0);
		if (adView != null) {
			adView.resume();
		}
	}

	@Override
	protected void onPause() {
		if (adView != null) {
			adView.pause();
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (adView != null) {
			adView.destroy();
		}
		super.onDestroy();
		releaseIabHelper();
	}

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

		String[] ScanString = getResources().getStringArray(R.array.scan_sec_string);
		String[] ScanValue = getResources().getStringArray(R.array.scan_sec_value);

		if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false)) {
			if (adView != null) {
				adView.pause();
				adView.destroy();
				((LinearLayout) findViewById(R.id.llAd)).removeAllViews();
			}
		}

		CheckBoxPreference cbPref = (CheckBoxPreference) findPreference(getString(R.string.key_setting_auto_start));
		cbPref.setChecked(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_setting_auto_start), false));
		cbPref.setOnPreferenceChangeListener(this);
		cbPref.setEnabled(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false));

		Preference pref = (Preference) findPreference(getString(R.string.key_setting_support_me));
		if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false)) {
			if (pref != null)
				((PreferenceGroup) findPreference(getString(R.string.key_setting_title))).removePreference(pref);
		} else {
			if (pref != null)
				pref.setOnPreferenceClickListener(this);
		}

		pref = (Preference) findPreference(getString(R.string.key_last_uses_times));
		if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_last_connect_time), "").length() > 0) {
			pref.setTitle(R.string.reconnect_last_time);
			pref.setSummary((getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_last_connect_time), "")));
		} else {
			pref.setTitle(R.string.reconnect_not_yet);
			pref.setSummary("");
		}

		ListPreference listPref = (ListPreference) findPreference(getString(R.string.key_scan_sec));
		for (int i = 0; i < ScanValue.length; i++) {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_scan_sec), "30").equalsIgnoreCase(ScanValue[i])) {
				listPref.setSummary(ScanString[i]);
				break;
			}
		}
		listPref.setOnPreferenceChangeListener(this);
		listPref.setEnabled(true);

		listPref = (ListPreference) findPreference(getString(R.string.key_notify_audio));
		for (int i = 0; i < OptionValue.length; i++) {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_notify_audio), "0").equalsIgnoreCase(OptionValue[i])) {
				listPref.setSummary(OptionString[i]);
				break;
			}
		}
		listPref.setOnPreferenceChangeListener(this);
		listPref.setEnabled(true);

		listPref = (ListPreference) findPreference(getString(R.string.key_notify_vibrate));
		for (int i = 0; i < OptionValue.length; i++) {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_notify_vibrate), "0").equalsIgnoreCase(OptionValue[i])) {
				listPref.setSummary(OptionString[i]);
				break;
			}
		}
		listPref.setOnPreferenceChangeListener(this);
		listPref.setEnabled(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false));
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (getString(R.string.key_setting_auto_start).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_setting_auto_start), (Boolean) newValue).commit();
			reStartService();
		} else if (getString(R.string.key_notify_audio).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.key_notify_audio), (String) newValue).commit();
		} else if (getString(R.string.key_notify_vibrate).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.key_notify_vibrate), (String) newValue).commit();
		} else if (getString(R.string.key_scan_sec).equals(preference.getKey())) {
			getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString(getString(R.string.key_scan_sec), (String) newValue).commit();
			if (!((String) newValue).equalsIgnoreCase("0")) {
				reStartService();
			}
		}
		hSetupDefaultData.sendEmptyMessage(0);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
			super.onActivityResult(requestCode, resultCode, data);
		}
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
						ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_failed), Toast.LENGTH_SHORT);
						releaseIabHelper();
					} else {
						mHelper.launchPurchaseFlow(SettingActivity.this, FullVersionID, BuyFullVersionRequestCode, new OnIabPurchaseFinishedListener() {
							@Override
							public void onIabPurchaseFinished(IabResult result, Purchase info) {
								if ((result != null) && (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED)) {
									getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version), true).commit();
									ToastUiThread(SettingActivity.this, getString(R.string.iabhelper_fullversion), Toast.LENGTH_LONG);
								} else {
									getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version), false).commit();
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

	// boolean testBuy = false;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
		// testBuy = !testBuy;
		// getSharedPreferences(getPackageName(),
		// MODE_PRIVATE).edit().putBoolean(getString(R.string.key_full_version),
		// testBuy).commit();
		// hSetupDefaultData.sendEmptyMessage(0);
		// return true;
		// }
		return super.onKeyDown(keyCode, event);
	}

	public void reStartService() {
		stopService(new Intent(SettingActivity.this, MonitorDeviceService.class));
		startService(new Intent(SettingActivity.this, MonitorDeviceService.class).putExtra(StartFromActivity, true));
	}
}