package com.monster.keepconnection;

import java.lang.reflect.Method;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.Vibrator;
import android.view.WindowManager;

enum InternetStatus {
	None, Mobile_Data_Off, Mobile_Data_On, Wifi_Data_Off, Wifi_Data_On, Internet_Failed, Inter_Success;
}

public class MonitorDeviceService extends Service {

	NotificationManager mNM;
	Camera camera;
	Parameters camera_parameters;
	int iBackCameraID = -1;
	int iRetryCounter = 0;
	boolean bStopAllWarning = false;
	boolean bInternetStatus = false;
	InternetStatus isStatus = InternetStatus.None;

	Handler hCheckInternetStatus = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (isConnectingToInternet() == false) {

				if (iRetryCounter > 3) {
					iRetryCounter = 0;

					if (isStatus == InternetStatus.Mobile_Data_Off) {
						updateAPN(MonitorDeviceService.this, true);
						isStatus = InternetStatus.Mobile_Data_On;

					} else if (isStatus == InternetStatus.Mobile_Data_On) {
						updateAPN(MonitorDeviceService.this, false);
						isStatus = InternetStatus.Mobile_Data_Off;
					}

				} else {
					iRetryCounter++;
				}
				hCheckInternetStatus.sendMessageDelayed(new Message().obtain(), 10000);
			} else {
				iRetryCounter = 0;
				hCheckInternetStatus.sendMessageDelayed(new Message().obtain(), 30000);
			}

		}
	};

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

	private boolean isConnectingToInternet() {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connMgr != null) {
			NetworkInfo info = connMgr.getActiveNetworkInfo();
			if (info != null)
				return (info.isConnected());
		}
		return false;
	}

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
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_setting_auto_start), false) || intent.getBooleanExtra(SettingActivity.StartFromActivity, false)) {
				bStopAllWarning = false;
				iRetryCounter = 0;
				isStatus = InternetStatus.None;
				hCheckInternetStatus.sendEmptyMessage(0);
				showNotification();
			} else {
				stopSelf();
			}
		} catch (NullPointerException e) {
			e.printStackTrace();
			stopSelf();
		}
		return super.onStartCommand(intent, flags, startId);
	}

	public void warningDialog() {
		Intent dialogIntent = new Intent(getBaseContext(), SettingActivity.class);
		dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		getApplication().startActivity(dialogIntent);

		AlertDialog.Builder builder = new AlertDialog.Builder(MonitorDeviceService.this);
		builder.setTitle(R.string.app_name);
		builder.setMessage(String.format(getString(R.string.warning_dialog), getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_name), "")));
		builder.setPositiveButton(R.string.option_ok, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bStopAllWarning = true;
			}
		});
		final AlertDialog dialog = builder.create();
		dialog.getWindow().setType((WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
		dialog.show();
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
		notification.setLatestEventInfo(this, getString(R.string.run_in_service),
				getString(R.string.connecting_device) + getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_name), ""), contentIntent);
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNM.notify(R.string.app_name, notification);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
}