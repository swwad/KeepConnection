package com.monster.keepconnection;

import java.util.HashSet;
import java.util.Set;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.Vibrator;
import android.view.WindowManager;
import com.monster.keepconnection.R;

public class MonitorDeviceService extends Service {

	NotificationManager mNM;
	Camera camera;
	Parameters camera_parameters;
	int iBackCameraID = -1;
	Set<BluetoothDevice> pairedDevices = new HashSet<BluetoothDevice>();
	boolean bStopAllWarning = false;

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

		if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_address), "").length() == 0) {
			stopSelf();
		}

		BluetoothAdapter.getDefaultAdapter().enable();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		try {
			unregisterReceiver(brReceiver);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

		try {
			if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_setting_auto_start), false) || intent.getBooleanExtra(SettingActivity.StartFromActivity, false)) {
				registerReceiver(brReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
				registerReceiver(brReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED));
				// registerReceiver(brReceiver, new
				// IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
				// registerReceiver(brReceiver, new
				// IntentFilter(BluetoothDevice.ACTION_FOUND));
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

	private final BroadcastReceiver brReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			bStopAllWarning = false;
			BluetoothDevice bdDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if ((BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) || (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(intent.getAction()))) {
				if (bdDevice.getAddress().equalsIgnoreCase(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_address), ""))) {

					warningAudio(Integer.valueOf(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_warning_audio), "0")));
					warningFlash(Integer.valueOf(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_warning_flash), "0")));
					warningVibrator(Integer.valueOf(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.pref_warning_vibrator), "0")));
					if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_warning_screen), false)) {
						warningScreen();
					}
					if (getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_warning_popwindow), false)) {
						warningDialog();
					}
				}
			}
			// else if
			// (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction()))
			// {
			// if
			// (bdDevice.getAddress().equalsIgnoreCase(getSharedPreferences(getPackageName(),
			// MODE_PRIVATE).getString(getString(R.string.pref_setting_bt_device_address),
			// ""))) {
			// hFindDevice.removeCallbacksAndMessages(null);
			// }
			// }
		}
	};

	@Override
	public void onDestroy() {
		try {
			unregisterReceiver(brReceiver);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
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