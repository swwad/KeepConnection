package com.monster.keepconnection;

import java.util.HashSet;
import java.util.Set;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import com.util.IabHelper;

public class SettingActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnPreferenceClickListener {

	Set<BluetoothDevice> pairedDevices = new HashSet<BluetoothDevice>();
	public final static String StartFromActivity = "StartFromActivity";

	final static int BuyFullVersionRequestCode = 101072;
	final static String IABRequestCode = "FullVersionID987654321";
	final static String IABKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjMeAGUaTE6wd+Eln+0FcxZSZ7eo2aqcPLMuQu1J3FSMzPrrn8NskHJayJwTokq8cqy8DP/SsyQ5A87FRz3N0MjOrtO7BXB0JJQsu3/7a3NzphMuDImWCohtU6QcWJIhZGrRT9XCjzFDY46WB6JQGeR280IVOFf2CMECQRsp0ujaB+SbBpZ4Nlkzr2l35G+t8Z4rsJWgrMS0ht8Y7RXH1QaDU2+zHgDxGQVocbmE+U3UdnHKSTAW/yy82VIZJKRQsJhcXBXKisaf8Fig2bA6ryBaDYYZVeIB4QGkI8Ojm5FCRx4POCf6XsIL1GfZg0IPUTpHxYH2bQ5Xvwhq4pBRzrQIDAQAB";
	final static String FullVersionID = "keep.connection.full.version";

	IabHelper mHelper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_setting);
		addPreferencesFromResource(R.xml.setting_preference);
		getListView().setItemsCanFocus(true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		hSetupDefaultData.sendEmptyMessage(0);
	}

	Handler hSetupDefaultData = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			SetDefaultData();
		}
	};

	void SetDefaultData() {

		Preference pref = (Preference) findPreference(getString(R.string.key_setting_support_me));
		pref.setOnPreferenceClickListener(this);

	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {

		return true;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (getString(R.string.key_setting_support_me).equals(preference.getKey())) {

			reStartService();
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
		startService(new Intent(SettingActivity.this, MonitorDeviceService.class).putExtra(StartFromActivity, true));
	}
}