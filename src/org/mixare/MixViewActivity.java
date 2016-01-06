/*
 * Copyright (C) 2010- Peer internet solutions
 * 
 * This file is part of mixare.
 * 
 * This program is free software: you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or 
 * (at your option) any later version. 
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details. 
 * 
 * You should have received a copy of the GNU General Public License along with 
 * this program. If not, see <http://www.gnu.org/licenses/>
 */
package org.mixare;

import static android.hardware.SensorManager.SENSOR_DELAY_GAME;

import java.util.Date;
import java.util.Random;

import org.mixare.R.drawable;
import org.mixare.data.DataSourceList;
import org.mixare.data.DataSourceStorage;
import org.mixare.lib.gui.PaintScreen;
import org.mixare.lib.render.Matrix;
import org.mixare.map.MixMap;
import org.mixare.mgr.HttpTools;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This class is the main application which uses the other classes for different
 * functionalities.
 * It sets up the camera screen and the augmented screen which is in front of the
 * camera screen.
 * It also handles the main sensor events, touch events and location events.
 */
public class MixViewActivity extends MixMenu implements SensorEventListener, OnTouchListener {

	private CameraSurface camScreen;
	private AugmentedView augScreen;
    private View hudGui;
    private TextView positionStatusText; // the textView on the HUD to show information about gpsPosition
    private TextView dataSourcesStatusText; // the textView on the HUD to show information about dataSources
    private TextView sensorsStatusText; // the textView on the HUD to show information about sensors


    private boolean isInited;
	private static boolean isBackground;
	private static PaintScreen paintScreen;
	private static MarkerRenderer markerRenderer;
	private boolean fError;

	/* Different error messages */
	protected static final int UNSUPPORTED_HARDWARE = 0;
	protected static final int GPS_ERROR = 1;
	protected static final int GENERAL_ERROR = 2;
	protected static final int NO_NETWORK_ERROR = 4;
	
	// test
	public static boolean drawTextBlock = true;
	
	//----------
    private MixViewDataHolder mixViewData  ;

	/** TAG for logging */
	public static final String TAG = "Mixare";

	// why use Memory to save a state? MixContext? activity lifecycle?
	// private static MixViewActivity CONTEXT;

	/** string to name & access the preference file in the internal storage */
	public static final String PREFS_NAME = "MyPrefsFileForMenuItems";
    private ProgressBar positionStatusProgress;
    private ProgressBar dataSourcesStatusProgress;
    private ProgressBar sensorsStatusProgress;
    private ImageView positionStatusIcon;
    private ImageView dataSourcesStatusIcon;
    private ImageView sensorsStatusIcon;

	private FrameLayout camera_view;

    /**
	 * Main application Launcher.
	 * Does:
	 * - Lock Screen.
	 * - Initiate Camera View
	 * - Initiate markerRenderer {@link MarkerRenderer#draw(PaintScreen) MarkerRenderer}
	 * - Initiate RangeBar {@link android.widget.SeekBar SeekBar widget}
	 * - Display License Agreement if mixViewActivity first used.
	 * 
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// MixViewActivity.CONTEXT = this;
		try {
			isBackground = false;
			handleIntent(getIntent());

			final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			getMixViewData().setmWakeLock(
					pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
							"My Tag"));

			getMixViewData().setSensorMgr(
					(SensorManager) getSystemService(SENSOR_SERVICE));

			killOnError();
			//requestWindowFeature(Window.FEATURE_NO_TITLE);
			if (getSupportActionBar() != null) {
				getSupportActionBar().hide();
			}


			maintainCamera();
			maintainAugmentR();
            //maintainHudGui();
            maintainRangeBar();

			if (!isInited) {
				// getMixViewData().setMixContext(new MixContext(this));
				// getMixViewData().getMixContext().setDownloadManager(new
				// DownloadManager(mixViewData.getMixContext()));
				setPaintScreen(new PaintScreen());
				setMarkerRenderer(new MarkerRenderer(getMixViewData().getMixContext()));

				/* set the radius in data markerRenderer to the last selected by the user */
				setRangeLevel();
				refreshDownload();
				isInited = true;
			}

			/*
			 * Get the preference file PREFS_NAME stored in the internal memory
			 * of the phone
			 */
			SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

			/* check if the application is launched for the first time */
			if (settings.getBoolean("firstAccess", false) == false) {
				firstAccess(settings);

			}


		} catch (Exception ex) {
            doError(ex, GENERAL_ERROR);
		}
	}

	@Override
	public MixViewDataHolder getMixViewData() {
		if (mixViewData == null && isBackground == false) {
			// TODO: VERY important, only one!
			mixViewData = new MixViewDataHolder(new MixContext(this));
		}
		return mixViewData;
	}

	/**
	 * Part of Android LifeCycle that gets called when "Activity" MixViewActivity is
	 * being navigated away. <br/>
	 * Does: - Release Screen Lock - Unregister Sensors.
	 * {@link android.hardware.SensorManager SensorManager} - Unregister
	 * Location Manager. {@link org.mixare.mgr.location.LocationFinder
	 * LocationFinder} - Switch off Download Thread.
	 * {@link org.mixare.mgr.downloader.DownloadManager DownloadManager} -
	 * Cancel markerRenderer refresh Timer. <br/>
	 * {@inheritDoc}
	 */
	@Override
	protected void onPause() {
		super.onPause();
		try {
			this.getMixViewData().getmWakeLock().release();
			camScreen.surfaceDestroyed(null);
			try {
				getMixViewData().getSensorMgr().unregisterListener(this,
						getMixViewData().getSensorGrav());
				getMixViewData().getSensorMgr().unregisterListener(this,
						getMixViewData().getSensorMag());
				getMixViewData().getSensorMgr().unregisterListener(this,
						getMixViewData().getSensorGyro());
				getMixViewData().getSensorMgr().unregisterListener(this);
				getMixViewData().setSensorGrav(null);
				getMixViewData().setSensorMag(null);
				getMixViewData().setSensorGyro(null);

				getMixViewData().getMixContext().getLocationFinder()
						.switchOff();
				getMixViewData().getMixContext().getDownloadManager()
						.switchOff();

				getMixViewData().getMixContext().getNotificationManager()
						.setEnabled(false);
				getMixViewData().getMixContext().getNotificationManager()
						.clear();
				if (getMarkerRenderer() != null) {
					getMarkerRenderer().cancelRefreshTimer();
				}
			} catch (Exception ignore) {
			}

			if (fError) {
				finish();
			}
		} catch (Exception ex) {
            doError(ex, GENERAL_ERROR);
		}
	}

	/**
	 * Mixare Activities Pipe message communication.
	 * Receives results from other launched activities
	 * and base on the result returned, it either refreshes screen or not.
	 * Default value for refreshing is false
	 * <br/>
	 * {@inheritDoc}
	 */
	protected void onActivityResult(final int requestCode,
			final int resultCode, Intent data) {
		//Log.d(TAG + " WorkFlow", "MixViewActivity - onActivityResult Called");
		// check if the returned is request to refresh screen (setting might be
		// changed)
		
		if (requestCode == 35) {
			if (resultCode == 1) {
				final AlertDialog.Builder dialog = new AlertDialog.Builder(this);

				dialog.setTitle(R.string.launch_plugins);
				dialog.setMessage(R.string.plugins_changed);
				dialog.setCancelable(false);
				
				// Always activate new plugins
				
//				final CheckBox checkBox = new CheckBox(ctx);
//				checkBox.setText(R.string.remember_this_decision);
//				dialog.setView(checkBox);		
				
				dialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int whichButton) {
						startActivity(new Intent(getMixViewData().getMixContext().getApplicationContext(),
								PluginLoaderActivity.class));
						finish();
					}
				});

				dialog.setNegativeButton(R.string.no,new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int whichButton) {
						d.dismiss();
					}
				});

				dialog.show();
			}
		}
		try {
			if (data.getBooleanExtra("RefreshScreen", false)) {
				Log.d(MixViewActivity.TAG + " WorkFlow",
						"MixViewActivity - Received Refresh Screen Request .. about to refresh");
				repaint();
				setRangeLevel();
				refreshDownload();
			}
		} catch (Exception ex) {
			// do nothing do to mix of return results.
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	/**
	 * Part of Android LifeCycle that gets called when "MixViewActivity" resumes.
	 * <br/>
	 * Does:
	 * - Acquire Screen Lock
	 * - Refreshes Data and Downloads
	 * - Initiate four Matrixes that holds user's rotation markerRenderer.
	 * - Re-register Sensors. {@link android.hardware.SensorManager SensorManager}
	 * - Re-register Location Manager. {@link org.mixare.mgr.location.LocationFinder LocationFinder}
	 * - Switch on Download Thread. {@link org.mixare.mgr.downloader.DownloadManager DownloadManager}
	 * - restart markerRenderer refresh Timer.
	 * <br/>
	 * {@inheritDoc}
	 * 
	 */
	@Override
	protected void onResume() {
		super.onResume();
		isBackground = false;
		try {
			this.getMixViewData().getmWakeLock().acquire();
			killOnError();
			getMixViewData().getMixContext().doResume(this);

			HttpTools.setContext(getMixViewData().getMixContext());
			
			//repaint(); //repaint when requested
			setRangeLevel();
			getMarkerRenderer().doStart();
			getMarkerRenderer().clearEvents();
			getMixViewData().getMixContext().getNotificationManager().setEnabled(true);
			refreshDownload();
			
			getMixViewData().getMixContext().getDataSourceManager().refreshDataSources();

			float angleX, angleY;

			int marker_orientation = -90;

			int rotation = Compatibility.getRotation(this);

			// display text from left to right and keep it horizontal
			angleX = (float) Math.toRadians(marker_orientation);
			getMixViewData().getM1().set(1f, 0f, 0f, 0f,
					(float) Math.cos(angleX),
					(float) -Math.sin(angleX), 0f,
					(float) Math.sin(angleX),
					(float) Math.cos(angleX));
			angleX = (float) Math.toRadians(marker_orientation);
			angleY = (float) Math.toRadians(marker_orientation);
			if (rotation == 1) {
				getMixViewData().getM2().set(1f, 0f, 0f, 0f,
						(float) Math.cos(angleX),
						(float) -Math.sin(angleX), 0f,
						(float) Math.sin(angleX),
						(float) Math.cos(angleX));
				getMixViewData().getM3().set((float) Math.cos(angleY), 0f,
						(float) Math.sin(angleY), 0f, 1f, 0f,
						(float) -Math.sin(angleY), 0f,
						(float) Math.cos(angleY));
			} else {
				getMixViewData().getM2().set((float) Math.cos(angleX), 0f,
						(float) Math.sin(angleX), 0f, 1f, 0f,
						(float) -Math.sin(angleX), 0f,
						(float) Math.cos(angleX));
				getMixViewData().getM3().set(1f, 0f, 0f, 0f,
						(float) Math.cos(angleY),
						(float) -Math.sin(angleY), 0f,
						(float) Math.sin(angleY),
						(float) Math.cos(angleY));

			}

			getMixViewData().getM4().toIdentity();

			for (int i = 0; i < getMixViewData().getHistR().length; i++) {
				getMixViewData().getHistR()[i] = new Matrix();
			}

			getMixViewData().addListSensors(getMixViewData().getSensorMgr().getSensorList(
					Sensor.TYPE_ACCELEROMETER));
			if (getMixViewData().getSensor(0).getType() == Sensor.TYPE_ACCELEROMETER ) {
				getMixViewData().setSensorGrav(getMixViewData().getSensor(0));
			}//else report error (unsupported hardware)

			getMixViewData().addListSensors(getMixViewData().getSensorMgr().getSensorList(
					Sensor.TYPE_MAGNETIC_FIELD));
			if (getMixViewData().getSensor(1).getType() == Sensor.TYPE_MAGNETIC_FIELD) {
				getMixViewData().setSensorMag(getMixViewData().getSensor(1));
			}//else report error (unsupported hardware)
			
			if (!getMixViewData().getSensorMgr().getSensorList(Sensor.TYPE_GYROSCOPE).isEmpty()){
				getMixViewData().addListSensors(getMixViewData().getSensorMgr().getSensorList(
						Sensor.TYPE_GYROSCOPE));
				if (getMixViewData().getSensor(2).getType() == Sensor.TYPE_GYROSCOPE) {
					getMixViewData().setSensorGyro(getMixViewData().getSensor(2));
				}
				getMixViewData().getSensorMgr().registerListener(this,
						getMixViewData().getSensorGyro(), SENSOR_DELAY_GAME);
			}
			
				getMixViewData().getSensorMgr().registerListener(this,
						getMixViewData().getSensorGrav(), SENSOR_DELAY_GAME);
				getMixViewData().getSensorMgr().registerListener(this,
						getMixViewData().getSensorMag(), SENSOR_DELAY_GAME);
				
			try {
				GeomagneticField gmf = getMixViewData().getMixContext()
						.getLocationFinder().getGeomagneticField();
				angleY = (float) Math.toRadians(-gmf.getDeclination());
				getMixViewData().getM4().set((float) Math.cos(angleY), 0f,
						(float) Math.sin(angleY), 0f, 1f, 0f,
						(float) -Math.sin(angleY), 0f,
						(float) Math.cos(angleY));
			} catch (Exception ex) {
				doError(ex, GPS_ERROR);
			}

			if (!isNetworkAvailable()) {
				Log.d(MixViewActivity.TAG, "no network");
				doError(null, NO_NETWORK_ERROR);
			} else {
				Log.d(MixViewActivity.TAG, "network");
			}
			
			getMixViewData().getMixContext().getDownloadManager().switchOn();
			getMixViewData().getMixContext().getLocationFinder().switchOn();
		} catch (Exception ex) {
            doError(ex, GENERAL_ERROR);
			try {
				if (getMixViewData().getSensorMgr() != null) {
					getMixViewData().getSensorMgr().unregisterListener(this,
							getMixViewData().getSensorGrav());
					getMixViewData().getSensorMgr().unregisterListener(this,
							getMixViewData().getSensorMag());
					getMixViewData().getSensorMgr().unregisterListener(this,
							getMixViewData().getSensorGyro());
					getMixViewData().setSensorMgr(null);
				}

				if (getMixViewData().getMixContext() != null) {
					getMixViewData().getMixContext().getLocationFinder()
							.switchOff();
					getMixViewData().getMixContext().getDownloadManager()
							.switchOff();
				}
			} catch (Exception ignore) {
			}
		}finally{
			//This does not conflict with registered sensors (sensorMag, sensorGrav)
			//This is a place holder to API returned listed of sensors, we registered
			//what we need, the rest is unnecessary.
			getMixViewData().clearAllSensors();
		}

		Log.d(MixViewActivity.TAG, "resume");
		if (getMarkerRenderer().isFrozen()
				&& getMixViewData().getSearchNotificationTxt() == null) {
			getMixViewData().setSearchNotificationTxt(new TextView(this));
			getMixViewData().getSearchNotificationTxt().setWidth(
					getPaintScreen().getWidth());
			getMixViewData().getSearchNotificationTxt().setPadding(10, 2, 0, 0);
			getMixViewData().getSearchNotificationTxt().setText(
					getString(R.string.search_active_1) + " "
							+ DataSourceList.getDataSourcesStringList()
							+ getString(R.string.search_active_2));
			;
			getMixViewData().getSearchNotificationTxt().setBackgroundColor(
					Color.DKGRAY);
			getMixViewData().getSearchNotificationTxt().setTextColor(
					Color.WHITE);

			getMixViewData().getSearchNotificationTxt()
					.setOnTouchListener(this);
			addContentView(getMixViewData().getSearchNotificationTxt(),
					new LayoutParams(LayoutParams.MATCH_PARENT,
							LayoutParams.WRAP_CONTENT));
		} else if (!getMarkerRenderer().isFrozen()
				&& getMixViewData().getSearchNotificationTxt() != null) {
			getMixViewData().getSearchNotificationTxt()
					.setVisibility(View.GONE);
			getMixViewData().setSearchNotificationTxt(null);
		}
	}

	/**
	 * Customize Activity after switching back to it.
	 * Currently it maintain and ensures markerRenderer creation.
	 * <br/>
	 * {@inheritDoc}
	 */
	protected void onRestart() {
		super.onRestart();
		maintainCamera();
		maintainAugmentR();
        //maintainHudGui();
        maintainRangeBar();
	}
	
	/**
	 * {@inheritDoc}
	 * Deallocate memory and stops threads.
	 * Please don't rely on this function as it's killable, 
	 * and might not be called at all.
	 */
	protected void onDestroy(){
		try{
			
			getMixViewData().getMixContext().getDownloadManager().shutDown();
			getMixViewData().getSensorMgr().unregisterListener(this);
			isBackground = true; //used to enforce garbage MixViewDataHolder
			getMixViewData().setSensorMgr(null);
			mixViewData = null;
			/*
			 * Invoked when the garbage collector has detected that this
			 * instance is no longer reachable. The default implementation does
			 * nothing, but this method can be overridden to free resources.
			 * 
			 * Do we have to create our own finalize?
			 */
		}catch(Exception e){
			//do nothing we are shutting down
		} catch (Throwable e) {
			//finalize error. (this function does nothing but call native API and release 
			//any synchronization-locked messages and threads deadlocks.
			Log.e(MixViewActivity.TAG, e.getMessage());
		}finally{
			super.onDestroy();
		}
	}
	
	/* ********* Operators ***********/ 
	/**
	 * View Repainting.
	 * It deletes viewed data and initiate new one. {@link MarkerRenderer MarkerRenderer}
	 */
	public void repaint() {
		// clear stored data
		getMarkerRenderer().clearEvents();
		setMarkerRenderer(null); //It's smelly code, but enforce garbage collector
							//to release data.
		setMarkerRenderer(new MarkerRenderer(getMixViewData().getMixContext()));
		setPaintScreen(new PaintScreen());
		
	}

	/**
	 * Checks camScreen, if it does not exist, it creates one.
	 */
	private void maintainCamera() {

		camera_view = (FrameLayout)findViewById(R.id.content_frame);

		if (camScreen == null) {
			camScreen = new CameraSurface(this);
			camera_view.addView(camScreen);
		}
		else {
			camera_view.removeView(camScreen);
			camera_view.addView(camScreen);

		}
		//setContentView(camScreen);
	}

	/**
	 * Checks augScreen, if it does not exist, it creates one.
	 */
	private void maintainAugmentR() {
		if (augScreen == null) {
			augScreen = new AugmentedView(this);
			camera_view.addView(augScreen, new LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT));
			//addContentView(augScreen, new LayoutParams(LayoutParams.WRAP_CONTENT,
			//		LayoutParams.WRAP_CONTENT));
		}
		else {
			((ViewGroup) augScreen.getParent()).removeView(augScreen);
			//addContentView(augScreen, new LayoutParams(LayoutParams.WRAP_CONTENT,
			//		LayoutParams.WRAP_CONTENT));
			camera_view.addView(augScreen, new LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT));
		}

	}

	/**
	 * Checks HUD GUI, if it does not exist, it creates one.
	 */
	private void maintainHudGui() {
		if (hudGui == null) {
            LayoutInflater inflater =  (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            hudGui = inflater.inflate(R.layout.hud_gui, null);
		}
        addContentView(hudGui, new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        positionStatusText =(TextView) this.findViewById(R.id.positionStatusText);
        dataSourcesStatusText =(TextView) this.findViewById(R.id.dataSourcesStatusText);
        sensorsStatusText =(TextView) this.findViewById(R.id.sensorsStatusText);
        positionStatusProgress = (ProgressBar) this.findViewById(R.id.positionStatusProgress);
        dataSourcesStatusProgress =(ProgressBar) this.findViewById(R.id.dataSourcesStatusProgress);
        sensorsStatusProgress =(ProgressBar) this.findViewById(R.id.sensorsStatusProgress);
        positionStatusIcon =(ImageView) this.findViewById(R.id.positionStatusIcon);
        dataSourcesStatusIcon =(ImageView) this.findViewById(R.id.dataSourcesStatusIcon);
        sensorsStatusIcon =(ImageView) this.findViewById(R.id.sensorsStatusIcon);
    }

	/**
	 * Creates a range bar and adds it to markerRenderer.
	 */
	private void maintainRangeBar() {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		FrameLayout frameLayout = createRangeBar(settings);
		addContentView(frameLayout, new FrameLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
				Gravity.BOTTOM));
		//camera_view.addView(frameLayout, new FrameLayout.LayoutParams(
		//		LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
		//		Gravity.BOTTOM));
	}

	/**
	 * Refreshes Download TODO refresh downloads
	 */
	public void refreshDownload(){
		getMixViewData().getMixContext().getDownloadManager().switchOn();
//		try {
//			if (getMixViewData().getDownloadThread() != null){
//				if (!getMixViewData().getDownloadThread().isInterrupted()){
//					getMixViewData().getDownloadThread().interrupt();
//					getMixViewData().getMixContext().getDownloadManager().restart();
//				}
//			}else { //if no download thread found
//				getMixViewData().setDownloadThread(new Thread(getMixViewData()
//						.getMixContext().getDownloadManager()));
//				//@TODO Syncronize DownloadManager, call Start instead of run.
//				mixViewData.getMixContext().getDownloadManager().run();
//			}
//		}catch (Exception ex){
//		}
	}
	
	/**
	 * Refreshes Viewed Data.
	 */
	public void refresh(){
		markerRenderer.refresh();
	}

	public void setErrorDialog(int error) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false);
		switch (error) {
		case NO_NETWORK_ERROR:
			builder.setMessage(getString(R.string.connection_error_dialog));
			break;
		case GPS_ERROR:
			builder.setMessage(getString(R.string.gps_error_dialog));
			break;
		case GENERAL_ERROR:
			builder.setMessage(getString(R.string.general_error_dialog));
			break;
		case UNSUPPORTED_HARDWARE:
			builder.setMessage(getString(R.string.unsupportet_hardware_dialog));
			break;
		}

		/*Retry*/
		builder.setPositiveButton(R.string.connection_error_dialog_button1, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// "restart" mixare
				startActivity(new Intent(getMixViewData().getMixContext().getApplicationContext(),
						PluginLoaderActivity.class));
				finish();
			}
		});
		if (error == GPS_ERROR) {
			/* Open settings */
			builder.setNeutralButton(R.string.connection_error_dialog_button2,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							try {
								Intent intent1 = new Intent(
										Settings.ACTION_LOCATION_SOURCE_SETTINGS);
								startActivityForResult(intent1, 42);
							} catch (Exception e) {
								Log.d(TAG, "No Location Settings");
							}
						}
					});
		} else if (error == NO_NETWORK_ERROR) {
			builder.setNeutralButton(R.string.connection_error_dialog_button2,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							try {
								Intent intent1 = new Intent(
										Settings.ACTION_DATA_ROAMING_SETTINGS);
								ComponentName cName = new ComponentName(
										"com.android.phone",
										"com.android.phone.Settings");
								intent1.setComponent(cName);
								startActivityForResult(intent1, 42);
							} catch (Exception e) {
								Log.d(TAG, "No Network Settings");
							}
						}
					});
		}
		/*Close application*/
		builder.setNegativeButton(R.string.connection_error_dialog_button3, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				finish();
			}
		});
		
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Calculate Range Level base 80.
	 * Mixare support ranges between 0-80km and default value of 20km,
	 * {@link android.widget.SeekBar SeekBar} on the other hand, is 0-100 base.
	 * This method handles the Range level conversion between Mixare rangeLevel and SeekBar.
	 * 
	 * @return int Range Level base 80
	 */
	public float calcRangeLevel(){

		int rangeBarProgress = getMixViewData().getRangeBar().getProgress();
		float rangeLevel = 5;

		if (rangeBarProgress <= 26) {
			rangeLevel = rangeBarProgress / 25f;
		} else if (25 < rangeBarProgress && rangeBarProgress < 50) {
			rangeLevel = (1 + (rangeBarProgress - 25)) * 0.38f;
		} else if (25 == rangeBarProgress) {
			rangeLevel = 1;
		} else if (50 == rangeBarProgress) {
			rangeLevel = 10;
		} else if (50 < rangeBarProgress && rangeBarProgress < 75) {
			rangeLevel = (10 + (rangeBarProgress - 50)) * 0.83f;
		} else {
			rangeLevel = (30 + (rangeBarProgress - 75) * 2f);
		}

		return rangeLevel;
	}

	/**
	 * Handle First time users. It display license agreement and store user's
	 * acceptance.
	 * 
	 * @param settings
	 */
	private void firstAccess(SharedPreferences settings) {
		SharedPreferences.Editor editor = settings.edit();
		AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
		builder1.setMessage(getString(R.string.license));
		builder1.setNegativeButton(getString(R.string.close_button),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
		AlertDialog alert1 = builder1.create();
		alert1.setTitle(getString(R.string.license_title));
		alert1.show();
		editor.putBoolean("firstAccess", true);

		// value for maximum POI for each selected OSM URL to be active by
		// default is 5
		editor.putInt("osmMaxObject", 5);
		editor.commit();

		// add the default datasources to the preferences file
		DataSourceStorage.getInstance().fillDefaultDataSources();
	}

	/**
	 * Create range bar and returns FrameLayout. FrameLayout is created to be
	 * hidden and not added to markerRenderer, Caller needs to add the frameLayout to
	 * markerRenderer, and enable visibility when needed.
	 * 
	 * @param settings where setting is stored
	 * @return FrameLayout Hidden Range Bar
	 */
	private FrameLayout createRangeBar(SharedPreferences settings) {
		getMixViewData().setRangeBar(new SeekBar(this));
		getMixViewData().getRangeBar().setMax(100);
		getMixViewData().getRangeBar().setProgress(
				settings.getInt(getString(R.string.pref_rangeLevel), 65));
		getMixViewData().getRangeBar().setOnSeekBarChangeListener(
				onRangeBarChangeListener);
		getMixViewData().getRangeBar().setVisibility(View.INVISIBLE);

		FrameLayout frameLayout = new FrameLayout(this);

		frameLayout.setMinimumWidth(3000);
		LayoutParams pa = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		frameLayout.setLayoutParams(pa);
		frameLayout.addView(getMixViewData().getRangeBar());
		frameLayout.setPadding(10, 0, 10, 10);
		return frameLayout;
	}

	/**
	 * Checks whether a network is available or not
	 * @return True if connected, false if not
	 */
	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager 
	          = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
	}
	
	/* ********* Operator - Menu ***** */
	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		int base = Menu.FIRST;
		/* define the first */
		/*MenuItem item1 = menu.add(base, base, base,
				getString(R.string.menu_item_1));
		MenuItem item2 = menu.add(base, base + 1, base + 1,
				getString(R.string.menu_item_2));
		MenuItem item3 = menu.add(base, base + 2, base + 2,
				getString(R.string.menu_item_3));
		MenuItem item4 = menu.add(base, base + 3, base + 3,
				getString(R.string.menu_item_4));
		MenuItem item5 = menu.add(base, base + 4, base + 4,
				getString(R.string.menu_item_5));
		MenuItem item6 = menu.add(base, base + 5, base + 5,
				getString(R.string.menu_item_6));
		MenuItem item7 = menu.add(base, base + 6, base + 6,
				getString(R.string.menu_item_7));
		MenuItem item8 = menu.add(base, base + 7, base + 7,
				getString(R.string.menu_item_8));

		MenuItem item9 = menu.add(base, base + 8, base + 8, "drawText");
		
		/* assign icons to the menu items */
	/*
		item1.setIcon(drawable.icon_datasource);
		item2.setIcon(drawable.icon_datasource);
		item3.setIcon(android.R.drawable.ic_menu_view);
		item4.setIcon(android.R.drawable.ic_menu_mapmode);
		item5.setIcon(android.R.drawable.ic_menu_zoom);
		item6.setIcon(android.R.drawable.ic_menu_search);
		item7.setIcon(android.R.drawable.ic_menu_info_details);
		item8.setIcon(android.R.drawable.ic_menu_share);

		return true;
	} */

	/*@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		/* Data sources */
		/*case 1:
			if (!getMarkerRenderer().getIsLauncherStarted()) {
				Intent intent = new Intent(MixViewActivity.this, DataSourceList.class);
				startActivityForResult(intent, 40);
			} else {
				markerRenderer.getContext().getNotificationManager()
					.addNotification(getString(R.string.no_website_available));
			}
			break;
			/* Plugin View */
		/*case 2:
			if (!getMarkerRenderer().getIsLauncherStarted()) {
				Intent intent = new Intent(MixViewActivity.this,
						PluginListActivity.class);
				startActivityForResult(intent, 35);
			} else {
				markerRenderer.getContext().getNotificationManager()
					.addNotification(getString(R.string.no_website_available));
			}
			break;
		/* List markerRenderer */
		/*case 3:
			/*
			 * if the list of titles to show in alternative list markerRenderer is not
			 * empty
			 */
			/*if (getMarkerRenderer().getDataHandler().getMarkerCount() > 0) {
				Intent intent1 = new Intent(MixViewActivity.this, MixListView.class);
				intent1.setAction(Intent.ACTION_VIEW);
				startActivityForResult(intent1, 42);
			}
			/* if the list is empty */
			/*else {
				markerRenderer.getContext().getNotificationManager().
				addNotification(getString(R.string.empty_list));
			}
			break;
		/* Map View */
		/*case 4:
			Intent intent2 = new Intent(MixViewActivity.this, MixMap.class);
			startActivityForResult(intent2, 20);
			break;
		/* range level */
		/*case 5:
			getMixViewData().getRangeBar().setVisibility(View.VISIBLE);
			getMixViewData().setRangeBarProgress(
					getMixViewData().getRangeBar().getProgress());
			break;
		/* Search */
		/*case 6:
			onSearchRequested();
			break;
		/* GPS Information */
		/*case 7:
			Location currentGPSInfo = getMixViewData().getMixContext()
					.getLocationFinder().getCurrentLocation();
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.general_info_text) + "\n\n"
					+ getString(R.string.longitude)
					+ currentGPSInfo.getLongitude() + "\n"
					+ getString(R.string.latitude)
					+ currentGPSInfo.getLatitude() + "\n"
					+ getString(R.string.altitude)
					+ currentGPSInfo.getAltitude() + "m\n"
					+ getString(R.string.speed) + currentGPSInfo.getSpeed()
					+ "km/h\n" + getString(R.string.accuracy)
					+ currentGPSInfo.getAccuracy() + "m\n"
					+ getString(R.string.gps_last_fix)
					+ new Date(currentGPSInfo.getTime()).toString() + "\n");
			builder.setNegativeButton(getString(R.string.close_button),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}
					});
			AlertDialog alert = builder.create();
			alert.setTitle(getString(R.string.general_info_title));
			alert.show();
			break;
		/* Case 6: license agreements */
		/*case 8:
			AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
			builder1.setMessage(getString(R.string.license));
			/* Retry */
			/*builder1.setNegativeButton(getString(R.string.close_button),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}
					});
			AlertDialog alert1 = builder1.create();
			alert1.setTitle(getString(R.string.license_title));
			alert1.show();
			break;
		case 9:
			doError(null, new Random().nextInt(3));
		}
		return true;
	}
	*/
	/*@Override
	public void selectItem(int position) {
		switch (position) {
		/* Data sources */
			/*case 0:
				if (!getMarkerRenderer().getIsLauncherStarted()) {
					Intent intent = new Intent(MixViewActivity.this, DataSourceList.class);
					startActivityForResult(intent, 40);
				} else {
					markerRenderer.getContext().getNotificationManager()
							.addNotification(getString(R.string.no_website_available));
				}
				break;
			/* Plugin View */
			/*case 1:
				if (!getMarkerRenderer().getIsLauncherStarted()) {
					Intent intent = new Intent(MixViewActivity.this,
							PluginListActivity.class);
					startActivityForResult(intent, 35);
				} else {
					markerRenderer.getContext().getNotificationManager()
							.addNotification(getString(R.string.no_website_available));
				}
				break;
		/* List markerRenderer */
			/*case 2:
			/*
			 * if the list of titles to show in alternative list markerRenderer is not
			 * empty
			 */
			/*	if (getMarkerRenderer().getDataHandler().getMarkerCount() > 0) {
					Intent intent1 = new Intent(MixViewActivity.this, MixListView.class);
					intent1.setAction(Intent.ACTION_VIEW);
					startActivityForResult(intent1, 42);
				}
			/* if the list is empty */
			/*	else {
					markerRenderer.getContext().getNotificationManager().
							addNotification(getString(R.string.empty_list));
				}
				break;
		/* Map View */
			/*case 3:
				Intent intent2 = new Intent(MixViewActivity.this, MixMap.class);
				startActivityForResult(intent2, 20);
				break;
		/* range level */
			/*case 4:
				getMixViewData().getRangeBar().setVisibility(View.VISIBLE);
				getMixViewData().setRangeBarProgress(
						getMixViewData().getRangeBar().getProgress());
				break;
		/* Search */
			/*case 5:
				onSearchRequested();
				break;
		/* GPS Information */
			/*case 6:
				Location currentGPSInfo = getMixViewData().getMixContext()
						.getLocationFinder().getCurrentLocation();
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getString(R.string.general_info_text) + "\n\n"
						+ getString(R.string.longitude)
						+ currentGPSInfo.getLongitude() + "\n"
						+ getString(R.string.latitude)
						+ currentGPSInfo.getLatitude() + "\n"
						+ getString(R.string.altitude)
						+ currentGPSInfo.getAltitude() + "m\n"
						+ getString(R.string.speed) + currentGPSInfo.getSpeed()
						+ "km/h\n" + getString(R.string.accuracy)
						+ currentGPSInfo.getAccuracy() + "m\n"
						+ getString(R.string.gps_last_fix)
						+ new Date(currentGPSInfo.getTime()).toString() + "\n");
				builder.setNegativeButton(getString(R.string.close_button),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.dismiss();
							}
						});
				AlertDialog alert = builder.create();
				alert.setTitle(getString(R.string.general_info_title));
				alert.show();
				break;
		/* Case 6: license agreements */
			/*case 7:
				AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
				builder1.setMessage(getString(R.string.license));
			/* Retry */
			/*	builder1.setNegativeButton(getString(R.string.close_button),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.dismiss();
							}
						});
				AlertDialog alert1 = builder1.create();
				alert1.setTitle(getString(R.string.license_title));
				alert1.show();
				break;
			case 8:
				doError(null, new Random().nextInt(3));
		}

	}
	*/


	/* ******** Operators - Sensors ****** */
	private SeekBar.OnSeekBarChangeListener onRangeBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

		public void onProgressChanged(SeekBar rangeBar, int progress,
				boolean fromUser) {
			float rangeLevel = calcRangeLevel();

			getMixViewData().setRangeLevel(String.valueOf(rangeLevel));
			getMixViewData().setRangeBarProgress(progress);

			markerRenderer.getContext().getNotificationManager().
			addNotification("Radius: " + String.valueOf(rangeLevel));
		}

		public void onStartTrackingTouch(SeekBar rangeBar) {
			markerRenderer.getContext().getNotificationManager().addNotification("Radius: ");
		}

		public void onStopTrackingTouch(SeekBar rangeBar) {
			SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
			SharedPreferences.Editor editor = settings.edit();
			/* store the range of the range bar selected by the user */
			editor.putInt(getString(R.string.pref_rangeLevel), rangeBar.getProgress());
			editor.commit();
			getMixViewData().getRangeBar().setVisibility(View.INVISIBLE);
			// rangeChanging= false;

			getMixViewData().getRangeBar().setProgress(rangeBar.getProgress());

			markerRenderer.getContext().getNotificationManager().clear();
			//repaint after range level changed.
			repaint();
			setRangeLevel();
			refreshDownload();
			
		}

	};

	public void onSensorChanged(SensorEvent evt) {
		try {
			if (getMixViewData().getSensorGyro() != null) {
				
				if (evt.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
					getMixViewData().setGyro(evt.values);
				}
				
				if (evt.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
					getMixViewData().setGrav(
							getMixViewData().getGravFilter().lowPassFilter(evt.values,
									getMixViewData().getGrav()));
				} else if (evt.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
					getMixViewData().setMag(
							getMixViewData().getMagFilter().lowPassFilter(evt.values,
									getMixViewData().getMag()));
				}
				getMixViewData().setAngle(
						getMixViewData().getMagFilter().complementaryFilter(
								getMixViewData().getGrav(),
								getMixViewData().getGyro(), 30,
								getMixViewData().getAngle()));
				
				SensorManager.getRotationMatrix(
						getMixViewData().getRTmp(),
						getMixViewData().getI(), 
						getMixViewData().getGrav(),
						getMixViewData().getMag());
			} else {
				if (evt.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
					getMixViewData().setGrav(evt.values);
				} else if (evt.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
					getMixViewData().setMag(evt.values);
				}
				SensorManager.getRotationMatrix(
						getMixViewData().getRTmp(),
						getMixViewData().getI(), 
						getMixViewData().getGrav(),
						getMixViewData().getMag());
			}
			
			augScreen.postInvalidate();

			int rotation = Compatibility.getRotation(this);

			if (rotation == 1) {
				SensorManager.remapCoordinateSystem(getMixViewData().getRTmp(),
						SensorManager.AXIS_X, SensorManager.AXIS_MINUS_Z,
						getMixViewData().getRot());
			} else {
				SensorManager.remapCoordinateSystem(getMixViewData().getRTmp(),
						SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_Z,
						getMixViewData().getRot());
			}
			getMixViewData().getTempR().set(getMixViewData().getRot()[0],
					getMixViewData().getRot()[1], getMixViewData().getRot()[2],
					getMixViewData().getRot()[3], getMixViewData().getRot()[4],
					getMixViewData().getRot()[5], getMixViewData().getRot()[6],
					getMixViewData().getRot()[7], getMixViewData().getRot()[8]);

			getMixViewData().getFinalR().toIdentity();
			getMixViewData().getFinalR().prod(getMixViewData().getM4());
			getMixViewData().getFinalR().prod(getMixViewData().getM1());
			getMixViewData().getFinalR().prod(getMixViewData().getTempR());
			getMixViewData().getFinalR().prod(getMixViewData().getM3());
			getMixViewData().getFinalR().prod(getMixViewData().getM2());
			getMixViewData().getFinalR().invert();
			
			getMixViewData().getHistR()[getMixViewData().getrHistIdx()]
					.set(getMixViewData().getFinalR());
			
			int histRLenght = getMixViewData().getHistR().length;
			
			getMixViewData().setrHistIdx(getMixViewData().getrHistIdx() + 1);
			if (getMixViewData().getrHistIdx() >= histRLenght)
				getMixViewData().setrHistIdx(0);

			getMixViewData().getSmoothR().set(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
					0f);
			for (int i = 0; i < histRLenght; i++) {
				getMixViewData().getSmoothR().add(
						getMixViewData().getHistR()[i]);
			}
			getMixViewData().getSmoothR().mult(
					1 / (float) histRLenght);

			getMixViewData().getMixContext().updateSmoothRotation(
					getMixViewData().getSmoothR());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent me) {
		if (getMixViewData().getRangeBar().getVisibility() == View.VISIBLE) {
			getMixViewData().getRangeBar().setVisibility(View.INVISIBLE);
		}
		
		try {
			killOnError();

			float xPress = me.getX();
			float yPress = me.getY();
			if (me.getAction() == MotionEvent.ACTION_UP) {
				getMarkerRenderer().clickEvent(xPress, yPress);
			}// TODO add gesture events (low)

			return true;
		} catch (Exception ex) {
			// doError(ex);
			ex.printStackTrace();
			return super.onTouchEvent(me);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		try {
			killOnError();
			
			if (getMixViewData().getRangeBar().getVisibility() == View.VISIBLE) {
				getMixViewData().getRangeBar().setVisibility(View.INVISIBLE);
				if (keyCode == KeyEvent.KEYCODE_MENU) {
					return super.onKeyDown(keyCode, event);
				}
				return true;
			}

			if (keyCode == KeyEvent.KEYCODE_BACK) {
				if (getMarkerRenderer().isDetailsView()) {
					getMarkerRenderer().keyEvent(keyCode);
					getMarkerRenderer().setDetailsView(false);
					return true;
				} else {
					Intent close = new Intent();
					close.putExtra("closed", "MixViewActivity");
					setResult(0, close);
					finish();
					return super.onKeyDown(keyCode, event);
				}
			} else if (keyCode == KeyEvent.KEYCODE_MENU) {
				return super.onKeyDown(keyCode, event);
			} else {
				getMarkerRenderer().keyEvent(keyCode);
				return false;
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			return super.onKeyDown(keyCode, event);
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD
				&& accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
				&& getMixViewData().getCompassErrorDisplayed() == 0) {
			for (int i = 0; i < 2; i++) {
				markerRenderer.getContext().getNotificationManager().
				addNotification("Compass data unreliable. Please recalibrate compass.");
			}
			getMixViewData().setCompassErrorDisplayed(
                    getMixViewData().getCompassErrorDisplayed() + 1);
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		getMarkerRenderer().setFrozen(false);
		if (getMixViewData().getSearchNotificationTxt() != null) {
			getMixViewData().getSearchNotificationTxt()
					.setVisibility(View.GONE);
			getMixViewData().setSearchNotificationTxt(null);
		}
		return true;
	}

	/* ************ Handlers ************ */

	public void doError(Exception ex1, int error) {
		if (!fError) {
			fError = true;

			setErrorDialog(error);

			try {
				ex1.printStackTrace();
			} catch (Exception ex2) {
				ex2.printStackTrace();
			}
		}

		try {
			augScreen.invalidate();
		} catch (Exception ignore) {
		}
	}

	public void killOnError() throws Exception {
		if (fError)
			throw new Exception();
	}

	private void handleIntent(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			intent.setClass(this, MixListView.class);
			startActivity(intent);
			}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
		handleIntent(intent);
	}


	/* ******* Getter and Setters ********** */

	public boolean isRangeBarVisible() {
		return getMixViewData().getRangeBar() != null
				&& getMixViewData().getRangeBar().getVisibility() == View.VISIBLE;
	}

	public String getRangeLevel() {
		return getMixViewData().getRangeLevel();
	}

	/**
	 * @return the paintScreen
	 */
	static PaintScreen getPaintScreen() {
		return paintScreen;
	}

	/**
	 * @param paintScreen
	 *            the paintScreen to set
	 */
	static void setPaintScreen(PaintScreen paintScreen) {
		MixViewActivity.paintScreen = paintScreen;
	}

	/**
	 * @return the markerRenderer
	 */
	public static MarkerRenderer getMarkerRenderer() {
		return markerRenderer;
	}

	/**
	 * @param markerRenderer
	 *            the markerRenderer to set
	 */
	static void setMarkerRenderer(MarkerRenderer markerRenderer) {
		MixViewActivity.markerRenderer = markerRenderer;
	}

	public int getRangeBarProgress() {
		return getMixViewData().getRangeBarProgress();
	}

	public void setRangeLevel() {
		float rangeLevel = calcRangeLevel();

		getMarkerRenderer().setRadius(rangeLevel);
		getMixViewData().setRangeLevel(String.valueOf(rangeLevel));
		//caller has the to control of rangebar visibility, not setrange
		//mixViewData.getRangeBar().setVisibility(View.INVISIBLE);
		//mixViewData.setRangeLevel(String.valueOf(rangeLevel));
		//setRangeLevel, caller has to call refresh download if needed.
//		mixViewData.setDownloadThread(new Thread(mixViewData.getMixContext().getDownloadManager()));
//		mixViewData.getDownloadThread().start();

	}

    public void updateHud(Location curFix){
        CharSequence relativeTime =  DateUtils.getRelativeTimeSpanString(curFix.getTime(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
        if(positionStatusText !=null) {
            positionStatusText.setText(getResources().getString(R.string.positionStatusText,curFix.getProvider(),curFix.getAccuracy(),relativeTime,curFix.getLatitude(),curFix.getLongitude(),curFix.getAltitude()));
        }
        //setDataSourcesActivity(getMarkerRenderer().dataSourceWorking,false,null);
    }

    public void setDataSourcesActivity(boolean working, boolean problem, String statusText){
        if(statusText!=null && dataSourcesStatusText !=null) {
            dataSourcesStatusText.setText(getResources().getString(R.string.dataSourcesStatusText));
        }
        if(dataSourcesStatusProgress !=null) {
            if(working) {
                dataSourcesStatusProgress.setVisibility(View.VISIBLE);
            } else {
                dataSourcesStatusProgress.setVisibility(View.INVISIBLE);
            }
        }
        if(dataSourcesStatusIcon !=null) {
            if(problem) {
                dataSourcesStatusIcon.setVisibility(View.VISIBLE);
            } else {
                dataSourcesStatusIcon.setVisibility(View.INVISIBLE);
            }
        }
    }

}

