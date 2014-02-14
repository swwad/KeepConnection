package com.monster.keepconnection;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
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

	final static int BuyFullVersionRequestCode = 721010;
	final static String IABRequestCode = "FullVersionID123456789";
	final static String IABKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAutzaL2p34g8tkLuySwac0dUT30sR5s61nhI02VITWJSdxZ3y4P6NW1vb8d9a+6dfZYpzYQPkebKVwlvJYFG7xwPeHcqyqCNc5EWa3hPaVbPHfeUrM/AI/pe/Go1LeniZpt27M0A7rUckEDryI+W5Eqp1d9+b0ie3L2aUzKKEKQGa+RDPGfXlVD7zuPuIyZZtgwzu2IDz8SZkBGTYQnbZe4vVetw0o/Vz7g4b3XPeGEYxYlpyj3K5yT93u2T2iUKfdRBHapx3p23xWrA0Ojh+GCBHAn0Jr/X83BqtnPGssrIdUHsZdo5KokQbieqOm6OCfgCulqejbqdGqKsECqj0qQIDAQAB";
	final static String FullVersionID = "dontleave.full.version";

	IabHelper mHelper;

	int iRetryCounter = 0;
	boolean bStopAllWarning = false;
	boolean bInternetStatus = false;
	InternetStatus isStatus = InternetStatus.None;
	int iLastStatus = -1;

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
			new Thread(new Runnable() {
				@Override
				public void run() {
					synchronized (this) {
						if (isConnectingToInternet() == false) {
							switch (isStatus) {
							case Inter_Success:
							case None:
								ToastUiThread(SettingActivity.this, "Inter_Success or None", Toast.LENGTH_LONG);
								isStatus = InternetStatus.Internet_Failed;
								break;
							case Internet_Failed:
								ToastUiThread(SettingActivity.this, "Internet_Failed", Toast.LENGTH_LONG);
								if (getConnectType() == ConnectivityManager.TYPE_MOBILE) {
									isStatus = InternetStatus.Mobile_Data_Failed;
								} else if (getConnectType() == ConnectivityManager.TYPE_WIFI) {
									isStatus = InternetStatus.Wifi_Data_Failed;
								} else {
									isStatus = InternetStatus.Internet_Unknow;
								}
								break;
							case Internet_Unknow:
								ToastUiThread(SettingActivity.this, "Internet_Unknow", Toast.LENGTH_LONG);
								((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
								if (getConnectType() != ConnectivityManager.TYPE_MOBILE) {
									updateAPN(SettingActivity.this, true);
									isStatus = InternetStatus.None;
								}
								break;
							case Mobile_Data_Failed:
								ToastUiThread(SettingActivity.this, "Mobile_Data_Failed", Toast.LENGTH_LONG);
								updateAPN(SettingActivity.this, false);
								isStatus = InternetStatus.Mobile_Data_Off;
								break;
							case Wifi_Data_Failed:
								ToastUiThread(SettingActivity.this, "Wifi_Data_Failed", Toast.LENGTH_LONG);
								((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
								isStatus = InternetStatus.Wifi_Data_Off;
								break;
							case Mobile_Data_Off:
								ToastUiThread(SettingActivity.this, "Mobile_Data_Off", Toast.LENGTH_LONG);
								if (getConnectType() != ConnectivityManager.TYPE_MOBILE) {
									updateAPN(SettingActivity.this, true);
									isStatus = InternetStatus.None;
								}
								break;
							case Wifi_Data_Off:
								ToastUiThread(SettingActivity.this, "Wifi_Data_Off", Toast.LENGTH_LONG);
								if (getConnectType() != ConnectivityManager.TYPE_WIFI) {
									((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(true);
									isStatus = InternetStatus.None;
								}
								break;
							default:
							}
						} else {
							isStatus = InternetStatus.Inter_Success;
							iLastStatus = getConnectType();
							ToastUiThread(SettingActivity.this, "Inter_Success", Toast.LENGTH_LONG);
						}
						System.gc();
					}
				}
			}, "KeepConnection_StateMachine_Thread").start();
		}
		return true;
	}

	Handler hCheckInternetStatus = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (isConnectingToInternet() == false) {

				if (iRetryCounter > 3) {
					iRetryCounter = 0;

					switch (isStatus) {
					case Inter_Success:
					case None:
						isStatus = InternetStatus.Internet_Failed;
						break;
					case Internet_Failed:
						if (getConnectType() == ConnectivityManager.TYPE_MOBILE) {
							isStatus = InternetStatus.Mobile_Data_Failed;
						} else if (getConnectType() == ConnectivityManager.TYPE_WIFI) {
							isStatus = InternetStatus.Wifi_Data_Failed;
						}
						break;
					case Mobile_Data_Failed:
						updateAPN(SettingActivity.this, false);
						isStatus = InternetStatus.Mobile_Data_Off;
						break;
					case Wifi_Data_Failed:
						((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
						isStatus = InternetStatus.Wifi_Data_Off;
						break;
					case Mobile_Data_Off:
						if (getConnectType() != ConnectivityManager.TYPE_MOBILE) {
							updateAPN(SettingActivity.this, true);
							isStatus = InternetStatus.None;
						}
						break;
					case Wifi_Data_Off:
						if (getConnectType() != ConnectivityManager.TYPE_WIFI) {
							((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(true);
							isStatus = InternetStatus.None;
						}
						break;
					default:

					}

				} else {
					iRetryCounter++;
				}
				hCheckInternetStatus.sendMessageDelayed(new Message().obtain(), 10000);
			} else {
				iRetryCounter = 0;
				isStatus = InternetStatus.Inter_Success;
				hCheckInternetStatus.sendMessageDelayed(new Message().obtain(), 30000);
			}

		}
	};

	void updateAPN(Context paramContext, boolean enable) {
		try {
			ConnectivityManager connectivityManager = (ConnectivityManager) paramContext.getSystemService(Context.CONNECTIVITY_SERVICE);
			Method setMobileDataEnabledMethod = ConnectivityManager.class.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
			setMobileDataEnabledMethod.setAccessible(true);
			setMobileDataEnabledMethod.invoke(connectivityManager, enable);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	boolean isConnectingToInternet() {
		boolean bRET = false;
		try {
			URLConnection connection = new URL("http://www.google.com.tw").openConnection();
			connection.setConnectTimeout(5 * 1000);
			if (((HttpURLConnection) connection).getResponseCode() == HttpURLConnection.HTTP_OK) {
				bRET = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			bRET = false;
		}
		return bRET;
	}

	int getConnectType() {
		int iType = -1;
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connMgr != null) {
			NetworkInfo info = connMgr.getActiveNetworkInfo();
			if (info != null) {
				iType = info.getType();
			}
		}
		return iType;
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