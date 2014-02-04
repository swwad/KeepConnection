package com.monster.keepconnection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.util.IabHelper;

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
		addPreferencesFromResource(R.xml.setting_preference);
		reStartService();

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
	
	boolean aaa = false;
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (getString(R.string.key_setting_support_me).equals(preference.getKey())) {
			ToastUiThread(this, "PP", Toast.LENGTH_SHORT);

			aaa = !aaa;
			
			Method dataConnSwitchmethod;
			Class telephonyManagerClass;
			Object ITelephonyStub;
			Class ITelephonyClass;

			TelephonyManager telephonyManager = (TelephonyManager) SettingActivity.this.getSystemService(Context.TELEPHONY_SERVICE);

			try {
				updateAPN(this, aaa);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return true;
	}
	
	private static void updateAPN(Context paramContext, boolean enable) {
	    try {
	        ConnectivityManager connectivityManager = (ConnectivityManager) paramContext.getSystemService("connectivity");
	        Method setMobileDataEnabledMethod = ConnectivityManager.class.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
	        setMobileDataEnabledMethod.setAccessible(true);
	        setMobileDataEnabledMethod.invoke(connectivityManager, enable);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
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
		// stopService(new Intent(SettingActivity.this,
		// MonitorDeviceService.class));
		// if (getSharedPreferences(getPackageName(),
		// MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_address),
		// "").length() == 0) {
		// Toast.makeText(SettingActivity.this, R.string.warning_no_btdevice,
		// Toast.LENGTH_SHORT).show();
		// } else {
		// startService(new Intent(SettingActivity.this,
		// MonitorDeviceService.class).putExtra(StartFromActivity, true));
		// }
	}
}