package com.monster.keepconnection;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.widget.Toast;

enum InternetStatus {
	None, Mobile_Data_Failed, Mobile_Data_Off, AIR_PLANE, Internet_Unknow, Internet_Failed, Internet_Success;
}

public class MonitorDeviceService extends Service {

	NotificationManager mNM;

	boolean bStopAllWarning = false;
	boolean bMonitorServiceThread = false;;
	boolean bInternetStatus = false;
	InternetStatus isStatus = InternetStatus.None;
	int iTimeout = 5000;
	static String LogDir = Environment.getExternalStorageDirectory() + System.getProperty("file.separator") + "KeepConnection_Log";

	@Override
	public void onCreate() {

	}

	@Override
	public void onDestroy() {
		bMonitorServiceThread = false;
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		try {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_setting_auto_start), false) || intent.getBooleanExtra(SettingActivity.StartFromActivity, false)) {
				isStatus = InternetStatus.None;
				showNotification();
				if (bMonitorServiceThread == false) {
					bMonitorServiceThread = true;
					thStateMachine.start();
				}
			} else {
				stopSelf();
			}
		} catch (NullPointerException e) {
			e.printStackTrace();
			stopSelf();
		}
		return super.onStartCommand(intent, flags, startId);
	}

	Handler hToastMessage = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (BuildConfig.DEBUG) {
				AddLog("Log.txt", msg.getData().getString("Message"));
				Toast.makeText(MonitorDeviceService.this, msg.getData().getString("Message"), Toast.LENGTH_LONG).show();
				}
		}
	};

	Message setStringMessage(String strMessage) {
		Bundle bData = new Bundle();
		bData.putString("Message", strMessage);
		Message msMessage = new Message().obtain();
		msMessage.setData(bData);
		return msMessage;
	}

	Thread thStateMachine = new Thread(new Runnable() {
		@Override
		public void run() {
			while (bMonitorServiceThread) {

				if ((getConnectType() == ConnectivityManager.TYPE_WIFI) || (getConnectType() == ConnectivityManager.TYPE_WIMAX)) {
					// Wifi 不做事
					hToastMessage.sendMessage(setStringMessage("Wifi do nothing"));
				} else {
					if (isConnectingToInternet() == false) {
						switch (isStatus) {
						case Internet_Success:
						case None:
							isStatus = InternetStatus.Internet_Failed;
							hToastMessage.sendMessage(setStringMessage("Internet_Failed"));
							warningVibrator(Integer.valueOf(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_notify_vibrate), "0")));
							// warningAudio(Integer.valueOf(getSharedPreferences(getPackageName(),
							// MODE_PRIVATE).getString(getString(R.string.key_notify_audio),
							// "0")));
							break;
						case Internet_Failed:
							if (getConnectType() == ConnectivityManager.TYPE_MOBILE) {
								isStatus = InternetStatus.Mobile_Data_Failed;
								hToastMessage.sendMessage(setStringMessage("Internet_Failed -> Mobile_Data_Failed"));
							} else {
								isStatus = InternetStatus.Internet_Unknow;
								hToastMessage.sendMessage(setStringMessage("Internet_Failed -> Internet_Unknow"));
							}
							break;
						case Internet_Unknow:
							((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
							isStatus = InternetStatus.Mobile_Data_Failed;
							hToastMessage.sendMessage(setStringMessage("Internet_Unknow -> Mobile_Data_Failed"));
							break;
						case Mobile_Data_Failed:
							updateAPN(MonitorDeviceService.this, false);
							isStatus = InternetStatus.Mobile_Data_Off;
							hToastMessage.sendMessage(setStringMessage("Mobile_Data_Failed -> Mobile_Data_Off"));
							break;
						// case Wifi_Data_Failed:
						// ((WifiManager)
						// getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
						// isStatus = InternetStatus.Wifi_Data_Off;
						// hToastMessage.sendMessage(setStringMessage("Wifi_Data_Failed -> Wifi_Data_Off"));
						// break;
						case Mobile_Data_Off:
							int iType = getConnectType();
							if (iType != ConnectivityManager.TYPE_MOBILE) {
								updateAPN(MonitorDeviceService.this, true);
								getSharedPreferences(getPackageName(), MODE_PRIVATE).edit()
										.putString(getString(R.string.key_last_connect_time), new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date())).commit();
								isStatus = InternetStatus.None;
								hToastMessage.sendMessage(setStringMessage("Mobile_Data_Off  -> Turn On APN [" + String.valueOf(iType) + "]"));
							} else {
								hToastMessage.sendMessage(setStringMessage("Still Mobile_Data_Off\nNow is ConnectivityManager.TYPE_MOBILE"));
							}
							break;
						// case Wifi_Data_Off:
						// int iType1 = getConnectType();
						// if (iType1 != ConnectivityManager.TYPE_WIFI) {
						// ((WifiManager)
						// getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(true);
						// isStatus = InternetStatus.None;
						// hToastMessage.sendMessage(setStringMessage("Wifi_Data_Off  -> InternetStatus_None,\nType is "
						// + String.valueOf(iType1)));
						// } else {
						// iRetryCounter++;
						// hToastMessage.sendMessage(setStringMessage("Still Wifi_Data_Off\nNow is ConnectivityManager.TYPE_WIFI"));
						// }
						// break;
						default:
						}
					} else {
						isStatus = InternetStatus.Internet_Success;
						String strTmp = "Internet_Success";
						hToastMessage.sendMessage(setStringMessage(strTmp));
					}
				}

				try {
					if ((isStatus == InternetStatus.Internet_Success) || (getConnectType() == ConnectivityManager.TYPE_WIFI) || (getConnectType() == ConnectivityManager.TYPE_WIMAX)) {
						if (Integer.valueOf(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_scan_sec), "30")) == 0) {
							bMonitorServiceThread = false;
							stopSelf();
						} else {
							Thread.sleep(Integer.valueOf(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.key_scan_sec), "30")) * 1000);
						}
					} else {
						Thread.sleep(5000);
					}

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}, "KeepConnection_StateMachine_Thread");

	boolean isConnectingToInternet() {
		boolean bRET = false;
		try {
			URLConnection connection = new URL("http://www.google.com.tw").openConnection();
			connection.setConnectTimeout(iTimeout);
			connection.setReadTimeout(iTimeout);
			if (((HttpURLConnection) connection).getResponseCode() == HttpURLConnection.HTTP_OK) {
				bRET = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			bRET = false;
		}
		return bRET;
	}

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

	public void warningVibrator(final int iSec) {
		new Thread() {
			public void run() {
				synchronized (this) {
					for (int i = 0; i < iSec; i++) {
						if (bStopAllWarning)
							break;
						((Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(1000);
						try {
							Thread.sleep(1050);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}.start();
	}

	public void warningAudio(final int iSec) {
		AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
		audioManager.setSpeakerphoneOn(true);
		// setVolumeControlStream(AudioManager.STREAM_MUSIC);
		audioManager.setMode(AudioManager.STREAM_MUSIC);
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
		final MediaPlayer playerSound = MediaPlayer.create(this, R.raw.bb_1);
		new Thread() {
			public void run() {
				synchronized (this) {
					for (int i = 0; i < iSec; i++) {
						if (bStopAllWarning)
							break;
						playerSound.start();
						try {
							Thread.sleep(1100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}.start();
	}

	private void showNotification() {
		Notification notification = new Notification(R.drawable.ic_launcher, getString(R.string.run_in_service), System.currentTimeMillis());
		notification.flags = Notification.FLAG_NO_CLEAR;
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MonitorDeviceService.class), 0);

		if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_full_version), false)) {
			notification.setLatestEventInfo(this, getString(R.string.run_in_service), getString(R.string.iabhelper_fullversion), contentIntent);
		} else {
			notification.setLatestEventInfo(this, getString(R.string.run_in_service), "", contentIntent);
		}
		
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNM.notify(R.string.app_name, notification);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public static void AddLog(String strFileName, String strLog) {
		try {
			if (!new File(LogDir).exists()) {
				new File(LogDir).mkdirs();
			}
			File testWrite = new File(LogDir, strFileName);
			Writer writer;
			writer = new BufferedWriter(new FileWriter(testWrite, true));
			Date now = new Date();
			writer.write(String.format("%s/%s/%s %02d:%02d:%02d", now.getYear() + 1900, now.getMonth() + 1, now.getDate(), now.getHours(), now.getMinutes(), now.getSeconds()) + " -> " + strLog + "\n");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}