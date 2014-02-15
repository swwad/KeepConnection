package com.monster.keepconnection;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.Vibrator;
import android.widget.Toast;

enum InternetStatus {
	None, Mobile_Data_Failed, Mobile_Data_Off, Wifi_Data_Failed, Wifi_Data_Off, AIR_PLANE, Internet_Unknow, Internet_Failed, Inter_Success;
}

public class MonitorDeviceService extends Service {

	NotificationManager mNM;
	Camera camera;
	Parameters camera_parameters;
	int iBackCameraID = -1;

	int iRetryCounter = 0;
	boolean bStopAllWarning = false;
	boolean bMonitorServiceThread = true;;
	boolean bInternetStatus = false;
	InternetStatus isStatus = InternetStatus.None;
	int iLastStatus = -1;

	@Override
	public void onCreate() {
		for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
				iBackCameraID = i;
				break;
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		try {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.key_setting_auto_start), false) || intent.getBooleanExtra(SettingActivity.StartFromActivity, false)) {
				bStopAllWarning = false;
				iRetryCounter = 0;
				isStatus = InternetStatus.None;
				showNotification();
				bMonitorServiceThread = true;
				thStateMachine.start();
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
			Toast.makeText(MonitorDeviceService.this, msg.getData().getString("Message"), Toast.LENGTH_LONG).show();
		}
	};

	Message setStringMessage(String strMessage) {
		Bundle bData = new Bundle();
		// bData.putString("Message", "Inter_Success or None");
		bData.putString("Message", strMessage);
		Message msMessage = new Message().obtain();
		msMessage.setData(bData);
		return msMessage;
	}

	Thread thStateMachine = new Thread(new Runnable() {
		@Override
		public void run() {
			while (bMonitorServiceThread) {
				if (isConnectingToInternet() == false) {
					switch (isStatus) {
					case Inter_Success:
					case None:
						hToastMessage.sendMessage(setStringMessage("Inter_Success or None"));
						isStatus = InternetStatus.Internet_Failed;
						break;
					case Internet_Failed:
						hToastMessage.sendMessage(setStringMessage("Internet_Failed"));
						if (getConnectType() == ConnectivityManager.TYPE_MOBILE) {
							isStatus = InternetStatus.Mobile_Data_Failed;
						} else if (getConnectType() == ConnectivityManager.TYPE_WIFI) {
							isStatus = InternetStatus.Wifi_Data_Failed;
						} else {
							isStatus = InternetStatus.Internet_Unknow;
						}
						break;
					case Internet_Unknow:
						hToastMessage.sendMessage(setStringMessage("Internet_Unknow"));
						((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
						if (getConnectType() != ConnectivityManager.TYPE_MOBILE) {
							updateAPN(MonitorDeviceService.this, true);
							isStatus = InternetStatus.None;
						}
						break;
					case Mobile_Data_Failed:
						hToastMessage.sendMessage(setStringMessage("Mobile_Data_Failed"));
						updateAPN(MonitorDeviceService.this, false);
						isStatus = InternetStatus.Mobile_Data_Off;
						break;
					case Wifi_Data_Failed:
						hToastMessage.sendMessage(setStringMessage("Wifi_Data_Failed"));
						((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
						isStatus = InternetStatus.Wifi_Data_Off;
						break;
					case Mobile_Data_Off:
						hToastMessage.sendMessage(setStringMessage("Mobile_Data_Off"));
						if (getConnectType() != ConnectivityManager.TYPE_MOBILE) {
							updateAPN(MonitorDeviceService.this, true);
							isStatus = InternetStatus.None;
						}
						break;
					case Wifi_Data_Off:
						hToastMessage.sendMessage(setStringMessage("Wifi_Data_Off"));
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
					hToastMessage.sendMessage(setStringMessage("Inter_Success"));
				}
				System.gc();
				try {
					Thread.sleep(10000);
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

	public void warningScreen() {
		new Thread() {
			public void run() {
				synchronized (this) {
					PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
					WakeLock wakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "TAG");
					wakeLock.acquire();
				}
			}
		}.start();
	}

	public void warningFlash(final int iSec) {
		camera = Camera.open(iBackCameraID);
		camera_parameters = camera.getParameters();
		new Thread() {
			public void run() {
				synchronized (this) {
					if (iBackCameraID != -1) {
						for (int i = 0; i < iSec; i++) {
							if (bStopAllWarning)
								break;
							camera_parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
							camera.setParameters(camera_parameters);
							SystemClock.sleep(500);
							camera_parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
							camera.setParameters(camera_parameters);
							SystemClock.sleep(500);
						}
						camera.stopPreview();
						camera.release();
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
		// notification.flags = Notification.FLAG_NO_CLEAR;
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MonitorDeviceService.class), 0);
		notification.setLatestEventInfo(this, getString(R.string.run_in_service), getString(R.string.iabhelper_fullversion), contentIntent);
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNM.notify(R.string.app_name, notification);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}