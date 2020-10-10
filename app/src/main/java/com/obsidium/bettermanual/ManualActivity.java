package com.obsidium.bettermanual;
//light branch
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.hardware.avio.DisplayManager;
import com.sony.scalar.sysutil.ScalarInput;
import com.sony.scalar.sysutil.didep.Settings;

import java.io.IOException;
import java.util.List;

import static java.lang.String.valueOf;

public class ManualActivity extends BaseActivity implements SurfaceHolder.Callback, CameraEx.ShutterListener, CameraEx.ShutterSpeedChangeListener
{
    private static final boolean LOGGING_ENABLED = false;
    private static final int MESSAGE_TIMEOUT = 1000;

    private SurfaceHolder   m_surfaceHolder;
    private CameraEx        m_camera;
    private CameraEx.AutoPictureReviewControl m_autoReviewControl;
    private int             m_pictureReviewTime;

    private Preferences     m_prefs;

    private TextView        m_tvShutter;
    private TextView        m_tvAperture;
    private TextView        m_tvISO;
    private LinearLayout    m_lExposure;
    private TextView        m_Apsc;
    private TextView        m_tvLog;
    private TextView        m_tvMagnification;
    private TextView        m_tvMsg;
    private HistogramView   m_vHist;
    private TableLayout     m_lInfoBottom;
    private ImageView       m_ivDriveMode;
    private ImageView       m_ivTimelapse;
    private GridView        m_vGrid;
    private TextView        m_tvHint;
    private FocusScaleView  m_focusScaleView;
    private View            m_lFocusScale;

    // Timelapse
    private int             m_autoPowerOffTimeBackup;
    private boolean         m_timelapseActive;
    private int             m_timelapseInterval;    // ms
    private int             m_timelapsePicCount;
    private int             m_timelapsePicsTaken;
    private int             m_countdown;
    private static final int COUNTDOWN_SECONDS = 5;
    private int             m_DisplayStatus=1;

    //ScanCode
    private static final int BUT_FRONT_CW=525;
    private static final int BUT_FRONT_CCW=526;
    private static final int BUT_REAR_CW=528;
    private static final int BUT_REAR_CCW=529;
    private static final int BUT_ISO_CW=635;
    private static final int BUT_ISO_CCW=634;
    private static final int BUT_C1=622;
    private static final int BUT_C2=623;
    private static final int BUT_AEL=638;





    private final Runnable  m_timelapseRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            m_camera.burstableTakePicture();
        }
    };
    private final Runnable  m_countDownRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (--m_countdown > 0)
            {
                m_tvMsg.setText(String.format("Starting in %d...", m_countdown));
                m_handler.postDelayed(this, 1000);
            }
            else
            {
                m_tvMsg.setVisibility(View.GONE);
                if (m_timelapseActive)
                    startShootingTimelapse();

            }
        }
    };

    private final Runnable m_hideFocusScaleRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            m_lFocusScale.setVisibility(View.GONE);
        }
    };

    // ISO
    private int             m_curIso;
    private List<Integer>   m_supportedIsos;

    // Shutter speed
    private boolean         m_notifyOnNextShutterSpeedChange;

    // Aperture
    private boolean         m_notifyOnNextApertureChange;
    private boolean         m_haveApertureControl;

    // Exposure compensation
    private int             m_maxExposureCompensation;
    private int             m_minExposureCompensation;
    private int             m_curExposureCompensation;
    private float           m_exposureCompensationStep;

    // Preview magnification
    private List<Integer>   m_supportedPreviewMagnifications;
    private boolean         m_zoomLeverPressed;
    private int             m_curPreviewMagnification;
    private float           m_curPreviewMagnificationFactor;
    private Pair<Integer, Integer>  m_curPreviewMagnificationPos = new Pair<Integer, Integer>(0, 0);
    private int             m_curPreviewMagnificationMaxPos;
    private PreviewNavView  m_previewNavView;

    enum DialMode {  drive, timelapse, apsc, timelapseSetInterval, timelapseSetPicCount }
    private DialMode        m_dialMode;

    enum SceneMode { manual, aperture, shutter, other }
    private SceneMode       m_sceneMode;

    private final Handler   m_handler = new Handler();
    private final Runnable  m_hideMessageRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            m_tvMsg.setVisibility(View.GONE);
        }
    };

    private boolean         m_takingPicture;
    private boolean         m_shutterKeyDown;

    private boolean         m_haveTouchscreen;

    private static final int VIEW_FLAG_GRID         = 0x01;
    private static final int VIEW_FLAG_HISTOGRAM    = 0x02;
    private static final int VIEW_FLAG_EXPOSURE     = 0x04;
    private static final int VIEW_FLAG_MASK         = 0x07; // all flags combined
    private int             m_viewFlags;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_manual);

        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler))
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler());

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);

        m_surfaceHolder = surfaceView.getHolder();
        m_surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // not needed - appears to be the default font
        //final Typeface sonyFont = Typeface.createFromFile("system/fonts/Sony_DI_Icons.ttf");

        m_tvMsg = (TextView)findViewById(R.id.tvMsg);
        m_tvAperture = (TextView)findViewById(R.id.tvAperture);
        m_tvShutter = (TextView)findViewById(R.id.tvShutter);
        m_tvISO = (TextView)findViewById(R.id.tvISO);
        m_Apsc = (TextView)findViewById(R.id.tvAPSC);
        m_lExposure = (LinearLayout)findViewById(R.id.lExposure);

        m_Apsc.setText("FULL");
        m_Apsc.setVisibility(View.VISIBLE);


        m_tvLog = (TextView)findViewById(R.id.tvLog);
        m_tvLog.setVisibility(LOGGING_ENABLED ? View.VISIBLE : View.GONE);

        m_vHist = (HistogramView)findViewById(R.id.vHist);

        m_tvMagnification = (TextView)findViewById(R.id.tvMagnification);

        m_lInfoBottom = (TableLayout)findViewById(R.id.lInfoBottom);

        m_previewNavView = (PreviewNavView)findViewById(R.id.vPreviewNav);

        m_previewNavView.setVisibility(View.GONE);
        m_lExposure.setVisibility(View.GONE);
        m_ivDriveMode = (ImageView)findViewById(R.id.ivDriveMode);




        m_ivTimelapse = (ImageView)findViewById(R.id.ivTimelapse);
        //noinspection ResourceType
        m_ivTimelapse.setImageResource(SonyDrawables.p_16_dd_parts_43_shoot_icon_setting_drivemode_invalid);

        m_vGrid = (GridView)findViewById(R.id.vGrid);

        m_tvHint = (TextView)findViewById(R.id.tvHint);
        m_tvHint.setVisibility(View.GONE);

        m_focusScaleView = (FocusScaleView)findViewById(R.id.vFocusScale);
        m_lFocusScale = findViewById(R.id.lFocusScale);
        m_lFocusScale.setVisibility(View.GONE);

        //noinspection ResourceType
        ((ImageView)findViewById(R.id.ivFocusRight)).setImageResource(SonyDrawables.p_16_dd_parts_rec_focuscontrol_far);
        //noinspection ResourceType
        ((ImageView)findViewById(R.id.ivFocusLeft)).setImageResource(SonyDrawables.p_16_dd_parts_rec_focuscontrol_near);

        setDialMode(DialMode.timelapse);

        m_prefs = new Preferences(this);

        m_haveTouchscreen = getDeviceInfo().getModel().compareTo("ILCE-5100") == 0;
    }



    private void showMessage(String msg)
    {
        m_tvMsg.setText(msg);
        m_tvMsg.setVisibility(View.VISIBLE);
        m_handler.removeCallbacks(m_hideMessageRunnable);
        m_handler.postDelayed(m_hideMessageRunnable, MESSAGE_TIMEOUT);
    }

    private void log(final String str)
    {
        if (LOGGING_ENABLED)
            m_tvLog.append(str);
    }
    
    private void setIso(int iso)
    {
        //log("setIso: " + String.valueOf(iso) + "\n");
        m_curIso = iso;
        m_tvISO.setText(String.format("\uE488 %s", (iso == 0 ? "AUTO" : String.valueOf(iso))));
        Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setISOSensitivity(iso);
        m_camera.getNormalCamera().setParameters(params);
    }

    private void setSilentShutter() {
        Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setSilentShutterMode(true);
        m_camera.getNormalCamera().setParameters(params);
    }


    private int getPreviousIso(int current)
    {
        int previous = 0;
        for (Integer iso : m_supportedIsos)
        {
            if (iso == current)
                return previous;
            else
                previous = iso;
        }
        return 0;
    }

    private int getNextIso(int current)
    {
        boolean next = false;
        for (Integer iso : m_supportedIsos)
        {
            if (next)
                return iso;
            else if (iso == current)
                next = true;
        }
        return current;
    }



    private void updateShutterSpeed(int n, int d)
    {
        final String text = CameraUtil.formatShutterSpeed(n, d);
        m_tvShutter.setText(text);
        if (m_notifyOnNextShutterSpeedChange)
        {
            showMessage(text);
            m_notifyOnNextShutterSpeedChange = false;
        }
    }





    private void updateViewVisibility()
    {
        m_vHist.setVisibility((m_viewFlags & VIEW_FLAG_HISTOGRAM) != 0 ? View.VISIBLE : View.GONE);
        m_vGrid.setVisibility((m_viewFlags & VIEW_FLAG_GRID) != 0 ? View.VISIBLE : View.GONE);
        m_lExposure.setVisibility((m_viewFlags & VIEW_FLAG_EXPOSURE) != 0 ? View.VISIBLE : View.GONE);
    }

    private void cycleVisibleViews()
    {
        if (++m_viewFlags > VIEW_FLAG_MASK)
            m_viewFlags = 0;
        updateViewVisibility();
    }




    private void toggleDriveMode()
    {
        final Camera normalCamera = m_camera.getNormalCamera();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(normalCamera.getParameters());
        final String driveMode = paramsModifier.getDriveMode();
        final String newMode;
        final String newBurstSpeed;
        if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE))
        {
            newMode = CameraEx.ParametersModifier.DRIVE_MODE_BURST;
            newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH;
        }
        else if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_BURST))
        {
            final String burstDriveSpeed = paramsModifier.getBurstDriveSpeed();
            if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW))
            {
                newMode = CameraEx.ParametersModifier.DRIVE_MODE_SINGLE;
                newBurstSpeed = burstDriveSpeed;
            }
            else
            {
                newMode = driveMode;
                newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW;
            }
        }
        else
        {
            // Anything else...
            newMode = CameraEx.ParametersModifier.DRIVE_MODE_SINGLE;
            newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH;
        }

        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier newParamsModifier = m_camera.createParametersModifier(params);
        newParamsModifier.setDriveMode(newMode);
        newParamsModifier.setBurstDriveSpeed(newBurstSpeed);
        m_camera.getNormalCamera().setParameters(params);

        updateDriveModeImage();
    }

    private void updateDriveModeImage()
    {
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(m_camera.getNormalCamera().getParameters());
        final String driveMode = paramsModifier.getDriveMode();
        if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE))
        {
            //noinspection ResourceType
            m_ivDriveMode.setImageResource(SonyDrawables.p_drivemode_n_001);
        }
        else if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_BURST))
        {
            final String burstDriveSpeed = paramsModifier.getBurstDriveSpeed();
            if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW))
            {
                //noinspection ResourceType
                m_ivDriveMode.setImageResource(SonyDrawables.p_drivemode_n_003);
            }
            else if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH))
            {
                //noinspection ResourceType
                m_ivDriveMode.setImageResource(SonyDrawables.p_drivemode_n_002);
            }
        }
        else //if (driveMode.equals("bracket"))
        {
            // Don't really care about this here
            //noinspection ResourceType
            m_ivDriveMode.setImageResource(SonyDrawables.p_dialogwarning);
        }
    }

    private void dumpList(List list, String name)
    {
        log(name);
        log(": ");
        if (list != null)
        {
            for (Object o : list)
            {
                log(o.toString());
                log(" ");
            }
        }
        else
            log("null");
        log("\n");
    }

    private void togglePreviewMagnificationViews(boolean magnificationActive)
    {
        m_previewNavView.setVisibility(magnificationActive ? View.VISIBLE : View.GONE);
        m_tvMagnification.setVisibility(magnificationActive ? View.VISIBLE : View.GONE);
        m_lInfoBottom.setVisibility(magnificationActive ? View.GONE : View.VISIBLE);
        m_vHist.setVisibility(magnificationActive ? View.GONE : View.VISIBLE);
        setLeftViewVisibility(!magnificationActive);
    }

    private void setSceneMode(String mode)
    {
        Camera.Parameters params = m_camera.createEmptyParameters();
        params.setSceneMode(mode);
        m_camera.getNormalCamera().setParameters(params);
    }

    private void saveDefaults()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        // Scene mode
        m_prefs.setSceneMode(params.getSceneMode());
        // Drive mode and burst speed
        m_prefs.setDriveMode(paramsModifier.getDriveMode());
        m_prefs.setBurstDriveSpeed(paramsModifier.getBurstDriveSpeed());
        // View visibility
        m_prefs.setViewFlags(m_viewFlags);

        // TODO: Dial mode
    }

    private void disableLENR()
    {
        // Disable long exposure noise reduction
        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(m_camera.getNormalCamera().getParameters());
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);
        if (paramsModifier.isSupportedLongExposureNR())
            modifier.setLongExposureNR(false);
        m_camera.getNormalCamera().setParameters(params);


    }

    private void loadDefaults()
    {
        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);
        // Focus mode
        params.setFocusMode(CameraEx.ParametersModifier.FOCUS_MODE_MANUAL);
        // Scene mode
        final String sceneMode = m_prefs.getSceneMode();
        params.setSceneMode(sceneMode);
        // Drive mode and burst speed
        modifier.setDriveMode(m_prefs.getDriveMode());
        modifier.setBurstDriveSpeed(m_prefs.getBurstDriveSpeed());
        // Minimum shutter speed
        if (sceneMode.equals(CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE))
            modifier.setAutoShutterSpeedLowLimit(-1);
        else
            modifier.setAutoShutterSpeedLowLimit(m_prefs.getMinShutterSpeed());
        // Disable self timer
        modifier.setSelfTimer(0);
        // Force aspect ratio to 3:2
        modifier.setImageAspectRatio(CameraEx.ParametersModifier.IMAGE_ASPECT_RATIO_3_2);
        // Apply
        m_camera.getNormalCamera().setParameters(params);
        // View visibility
        m_viewFlags = m_prefs.getViewFlags(VIEW_FLAG_GRID | VIEW_FLAG_HISTOGRAM);
        // TODO: Dial mode?
        setDialMode(DialMode.timelapse);

        disableLENR();
    }



    @Override
    protected void onResume()
    {
        super.onResume();
        m_camera = CameraEx.open(0, null);
        m_surfaceHolder.addCallback(this);
        m_camera.startDirectShutter();
        m_autoReviewControl = new CameraEx.AutoPictureReviewControl();
        m_camera.setAutoPictureReviewControl(m_autoReviewControl);
        // Enable  picture review
        m_pictureReviewTime = m_autoReviewControl.getPictureReviewTime();
        m_autoReviewControl.setPictureReviewTime(10);


        m_vGrid.setVideoRect(getDisplayManager().getDisplayedVideoRect());

        //log(String.format("getSavingBatteryMode %s\n", getDisplayManager().getSavingBatteryMode()));
        //log(String.format("getScreenGainControlType %s\n", getDisplayManager().getScreenGainControlType()));

        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);

        // Preview/Histogram
        m_camera.setPreviewAnalizeListener(new CameraEx.PreviewAnalizeListener()
        {
            @Override
            public void onAnalizedData(CameraEx.AnalizedData analizedData, CameraEx cameraEx)
            {
                if (analizedData != null && analizedData.hist != null && analizedData.hist.Y != null && m_vHist.getVisibility() == View.VISIBLE)
                    m_vHist.setHistogram(analizedData.hist.Y);
            }
        });

        // ISO
        m_camera.setAutoISOSensitivityListener(new CameraEx.AutoISOSensitivityListener()
        {
            @Override
            public void onChanged(int i, CameraEx cameraEx)
            {
                //log("AutoISOChanged " + String.valueOf(i) + "\n");
                m_tvISO.setText("\uE488 " + String.valueOf(i) + (m_curIso == 0 ? "(A)" : ""));
            }
        });

        // Shutter
        m_camera.setShutterSpeedChangeListener(this);
        m_camera.setShutterListener(this);


        // Aperture
        m_camera.setApertureChangeListener(new CameraEx.ApertureChangeListener()
        {
            @Override
            public void onApertureChange(CameraEx.ApertureInfo apertureInfo, CameraEx cameraEx)
            {
                // Disable aperture control if not available
                m_haveApertureControl = apertureInfo.currentAperture != 0;
                m_tvAperture.setVisibility(m_haveApertureControl ? View.VISIBLE : View.GONE);
                /*
                log(String.format("currentAperture %d currentAvailableMin %d currentAvailableMax %d\n",
                        apertureInfo.currentAperture, apertureInfo.currentAvailableMin, apertureInfo.currentAvailableMax));
                */
                final String text = String.format("f%.1f", (float)apertureInfo.currentAperture / 100.0f);
                m_tvAperture.setText(text);
                if (m_notifyOnNextApertureChange)
                {
                    m_notifyOnNextApertureChange = false;
                    showMessage(text);
                }
            }
        });

        m_supportedIsos = (List<Integer>)paramsModifier.getSupportedISOSensitivities();
        m_curIso = paramsModifier.getISOSensitivity();
        m_tvISO.setText(String.format("\uE488 %d", m_curIso));

        m_tvAperture.setText(String.format("f%.1f", (float)paramsModifier.getAperture() / 100.0f));

        Pair<Integer, Integer> sp = paramsModifier.getShutterSpeed();
        updateShutterSpeed(sp.first, sp.second);

        m_supportedPreviewMagnifications = (List<Integer>)paramsModifier.getSupportedPreviewMagnification();
        m_camera.setPreviewMagnificationListener(new CameraEx.PreviewMagnificationListener()
        {
            @Override
            public void onChanged(boolean enabled, int magFactor, int magLevel, Pair coords, CameraEx cameraEx)
            {
                // magnification / 100 = x.y
                // magLevel = value passed to setPreviewMagnification
                /*
                m_tvLog.setText("onChanged enabled:" + String.valueOf(enabled) + " magFactor:" + String.valueOf(magFactor) + " magLevel:" +
                    String.valueOf(magLevel) + " x:" + coords.first + " y:" + coords.second + "\n");
                */
                if (enabled)
                {
                    //log("m_curPreviewMagnificationMaxPos: " + String.valueOf(m_curPreviewMagnificationMaxPos) + "\n");
                    m_curPreviewMagnification = magLevel;
                    m_curPreviewMagnificationFactor = ((float)magFactor / 100.0f);
                    m_curPreviewMagnificationMaxPos = 1000 - (int)(1000.0f / m_curPreviewMagnificationFactor);
                    m_tvMagnification.setText(String.format("\uE012 %.2fx", (float)magFactor / 100.0f));
                    m_previewNavView.update(coords, m_curPreviewMagnificationFactor);
                }
                else
                {
                    m_previewNavView.update(null, 0);
                    m_curPreviewMagnification = 0;
                    m_curPreviewMagnificationMaxPos = 0;
                    m_curPreviewMagnificationFactor = 0;
                }
                togglePreviewMagnificationViews(enabled);
            }

            @Override
            public void onInfoUpdated(boolean b, Pair coords, CameraEx cameraEx)
            {
                // Useless?
                /*
                log("onInfoUpdated b:" + String.valueOf(b) +
                               " x:" + coords.first + " y:" + coords.second + "\n");
                */
            }
        });

        m_camera.setFocusDriveListener(new CameraEx.FocusDriveListener()
        {
            @Override
            public void onChanged(CameraEx.FocusPosition focusPosition, CameraEx cameraEx)
            {
                if (m_curPreviewMagnification == 0)
                {
                    m_lFocusScale.setVisibility(View.VISIBLE);
                    m_focusScaleView.setMaxPosition(focusPosition.maxPosition);
                    m_focusScaleView.setCurPosition(focusPosition.currentPosition);
                    m_handler.removeCallbacks(m_hideFocusScaleRunnable);
                    m_handler.postDelayed(m_hideFocusScaleRunnable, 2000);
                }
            }
        });

        loadDefaults();
        updateDriveModeImage();
        updateViewVisibility();

        //ForceManual();
        InitApsc();

        /* - triggers NPE
        List<Integer> pf = params.getSupportedPreviewFormats();
        if (pf != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("SupportedPreviewFormats: ");
            for (Integer i : pf)
                sb.append(i.toString()).append(",");
            sb.append("\n");
            log(sb.toString());
        }

        /* - return null
        List<Integer> pfr = params.getSupportedPreviewFrameRates();
        if (pfr != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("SupportedPreviewFrameRates: ");
            for (Integer i : pfr)
                sb.append(i.toString()).append(",");
            sb.append("\n");
            log(sb.toString());
        }
        List<Camera.Size> ps = params.getSupportedPreviewSizes();
        if (ps != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("SupportedPreviewSizes: ");
            for (Camera.Size s : ps)
                sb.append(s.toString()).append(",");
            sb.append("\n");
            log(sb.toString());
        }
        */
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        saveDefaults();

        m_surfaceHolder.removeCallback(this);
        m_autoReviewControl.setPictureReviewTime(m_pictureReviewTime);
        m_camera.setAutoPictureReviewControl(null);
        m_camera.getNormalCamera().stopPreview();
        m_camera.release();
        m_camera = null;
    }

    @Override
    public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo shutterSpeedInfo, CameraEx cameraEx)
    {
        updateShutterSpeed(shutterSpeedInfo.currentShutterSpeed_n, shutterSpeedInfo.currentShutterSpeed_d);
    }

    @Override
    public void onShutter(int i, CameraEx cameraEx)
    {
        // i: 0 = success, 1 = canceled, 2 = error
        //log(String.format("onShutter i: %d\n", i));
        if (i != 0)
        {
            //log(String.format("onShutter ERROR %d\n", i));
            m_takingPicture = false;
        }
        m_camera.cancelTakePicture();

        if (m_timelapseActive)
            onShutterTimelapse(i);

    }



    private void onShutterTimelapse(int i)
    {
        if (i == 0)
        {
            ++m_timelapsePicsTaken;
            if (m_timelapsePicCount < 0 || m_timelapsePicCount == 1)
                abortTimelapse();
            else
            {
                if (m_timelapsePicCount != 0)
                    --m_timelapsePicCount;
                if (m_timelapseInterval >= 1000)
                {
                    if (m_timelapsePicCount > 0)
                        showMessage(String.format("%d pictures remaining", m_timelapsePicCount));
                    else
                        showMessage(String.format("%d pictures taken", m_timelapsePicsTaken));
                }
                if (m_timelapseInterval != 0)
                    m_handler.postDelayed(m_timelapseRunnable, m_timelapseInterval);
                else
                    m_camera.burstableTakePicture();
            }
        }
        else
        {
            abortTimelapse();
        }
    }


    private void decrementTimelapseInterval()
    {
        if (m_timelapseInterval > 0)
        {
            if (m_timelapseInterval <= 1000)
                m_timelapseInterval -= 100;
            else
                m_timelapseInterval -= 1000;
        }
        updateTimelapseInterval();
    }

    private void incrementTimelapseInterval()
    {
        if (m_timelapseInterval < 1000)
            m_timelapseInterval += 100;
        else
            m_timelapseInterval += 1000;
        updateTimelapseInterval();
    }

    private Pair<Integer, Integer> getCurrentShutterSpeed()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        return paramsModifier.getShutterSpeed();
    }

    private void updateTimelapseInterval()
    {
        m_tvMsg.setVisibility(View.VISIBLE);
        if (m_timelapseInterval == 0)
            m_tvMsg.setText("No delay");
        else if (m_timelapseInterval < 1000)
            m_tvMsg.setText(String.format("%d msec", m_timelapseInterval));
        else if (m_timelapseInterval == 1000)
            m_tvMsg.setText("1 second");
        else
            m_tvMsg.setText(String.format("%d seconds", m_timelapseInterval / 1000));
    }

    private void updateTimelapsePictureCount()
    {
        m_tvMsg.setVisibility(View.VISIBLE);
        if (m_timelapsePicCount == 0)
            m_tvMsg.setText("No picture limit");
        else
            m_tvMsg.setText(String.format("%d pictures", m_timelapsePicCount));
    }

    private void decrementTimelapsePicCount()
    {
        if (m_timelapsePicCount > 0)
            --m_timelapsePicCount;
        updateTimelapsePictureCount();
    }

    private void incrementTimelapsePicCount()
    {
        ++m_timelapsePicCount;
        updateTimelapsePictureCount();
    }





    private void prepareTimelapse()
    {
        if (m_dialMode == DialMode.timelapseSetInterval || m_dialMode == DialMode.timelapseSetPicCount)
            abortTimelapse();
        else
        {
            setLeftViewVisibility(false);

            setDialMode(DialMode.timelapseSetInterval);
            m_timelapseInterval = 1000;
            updateTimelapseInterval();
            m_tvHint.setText("\uE4CD to set timelapse interval, \uE04C to confirm");
            m_tvHint.setVisibility(View.VISIBLE);

            // Not supported on some camera models
            try
            {
                m_autoPowerOffTimeBackup = Settings.getAutoPowerOffTime();
            }
            catch (NoSuchMethodError e)
            {
            }
        }
    }

    private void setLeftViewVisibility(boolean visible)
    {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        m_ivTimelapse.setVisibility(visibility);
        m_ivDriveMode.setVisibility(visibility);
        //m_ivMode.setVisibility(visibility);
        //m_ivBracket.setVisibility(visibility);
    }


    private void startTimelapseCountdown()
    {
        m_timelapseActive = true;
        m_camera.stopDirectShutter(new CameraEx.DirectShutterStoppedCallback()
        {
            @Override
            public void onShutterStopped(CameraEx cameraEx)
            {
            }
        });
        m_tvHint.setText("\uE04C to abort");
        // Stop preview (doesn't seem to preserve battery life?)
        m_camera.getNormalCamera().stopPreview();

        // Hide some bottom views
        m_prefs.setViewFlags(m_viewFlags);
        m_viewFlags = 0;
        updateViewVisibility();

        // Start countdown
        m_countdown = COUNTDOWN_SECONDS;
        m_tvMsg.setText(String.format("Starting in %d...", m_countdown));
        m_handler.postDelayed(m_countDownRunnable, 1000);
    }



    private void startShootingTimelapse()
    {
        m_tvHint.setVisibility(View.GONE);
        m_tvMsg.setVisibility(View.GONE);
        try
        {
            Settings.setAutoPowerOffTime(m_timelapseInterval / 1000 * 2);
        }
        catch (NoSuchMethodError e)
        {
        }
        m_handler.post(m_timelapseRunnable);
    }

    private void abortTimelapse()
    {
        m_handler.removeCallbacks(m_countDownRunnable);
        m_handler.removeCallbacks(m_timelapseRunnable);
        m_timelapseActive = false;
        showMessage("Timelapse finished");
        setDialMode(DialMode.timelapse);
        m_camera.startDirectShutter();
        m_camera.getNormalCamera().startPreview();

        // Update controls
        m_tvHint.setVisibility(View.GONE);
        setLeftViewVisibility(true);
        updateDriveModeImage();

        m_viewFlags = m_prefs.getViewFlags(m_viewFlags);
        updateViewVisibility();

        try
        {
            Settings.setAutoPowerOffTime(m_autoPowerOffTimeBackup);
        }
        catch (NoSuchMethodError e)
        {
        }
    }

    @Override
    protected boolean onUpperDialChanged(int value)
    {
        //showMessage((valueOf(value)));
        if (m_curPreviewMagnification != 0)
        {
            movePreviewHorizontal(value * (int)(500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        else
        {
            switch (m_dialMode)
            {

                case timelapseSetInterval:
                    if (value < 0)
                        decrementTimelapseInterval();
                    else
                        incrementTimelapseInterval();
                    break;
                case timelapseSetPicCount:
                    if (value < 0)
                        decrementTimelapsePicCount();
                    else
                        incrementTimelapsePicCount();
                    break;

            }
            return true;
        }
    }

    private void setDialMode(DialMode newMode)
    {
        m_dialMode = newMode;


        m_Apsc.setTextColor(newMode == DialMode.apsc ? Color.GREEN : Color.WHITE);

        if (newMode == DialMode.drive)
            m_ivDriveMode.setColorFilter(Color.GREEN);
        else
            m_ivDriveMode.setColorFilter(null);

        if (newMode == DialMode.timelapse)
            m_ivTimelapse.setColorFilter(Color.GREEN);
        else
            m_ivTimelapse.setColorFilter(null);

    }

    private void movePreviewVertical(int delta)
    {
        int newY = m_curPreviewMagnificationPos.second + delta;
        if (newY > m_curPreviewMagnificationMaxPos)
            newY = m_curPreviewMagnificationMaxPos;
        else if (newY < -m_curPreviewMagnificationMaxPos)
            newY = -m_curPreviewMagnificationMaxPos;
        m_curPreviewMagnificationPos = new Pair<Integer, Integer>(m_curPreviewMagnificationPos.first, newY);
        m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
    }

    private void movePreviewHorizontal(int delta)
    {
        int newX = m_curPreviewMagnificationPos.first + delta;
        if (newX > m_curPreviewMagnificationMaxPos)
            newX = m_curPreviewMagnificationMaxPos;
        else if (newX < -m_curPreviewMagnificationMaxPos)
            newX = -m_curPreviewMagnificationMaxPos;
        m_curPreviewMagnificationPos = new Pair<Integer, Integer>(newX, m_curPreviewMagnificationPos.second);
        m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
    }

    @Override
    protected boolean onEnterKeyUp()
    {
        return true;
    }

    @Override
    protected boolean onEnterKeyDown()
    {

        if (m_timelapseActive)
        {
            abortTimelapse();
            return true;
        }

         if (m_dialMode == DialMode.apsc)
        {
            ToggleAApsc();
            return true;
        }

        else if (m_dialMode == DialMode.timelapseSetInterval)
        {
            setDialMode(DialMode.timelapseSetPicCount);
            m_tvHint.setText("\uE4CD to set picture count, \uE04C to confirm");
            m_timelapsePicCount = 0;
            updateTimelapsePictureCount();
            return true;
        }
        else if (m_dialMode == DialMode.timelapseSetPicCount)
        {
            startTimelapseCountdown();
            return true;
        }



        else if (m_dialMode == DialMode.drive)
        {
            toggleDriveMode();
            return true;
        }
        else if (m_dialMode == DialMode.timelapse)
        {
            prepareTimelapse();
            return true;
        }

        return false;
    }

    @Override
    protected boolean onUpKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onUpKeyUp()
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewVertical((int)(-500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        else
        {
            // Toggle visibility of some views
            cycleVisibleViews();
            return true;
        }
    }

    @Override
    protected boolean onDownKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onDownKeyUp()
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewVertical((int)(500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        else
        {
            switch (m_dialMode)
            {

                case drive:
                    setDialMode(DialMode.timelapse);
                    break;
                case timelapse:
                    setDialMode(DialMode.apsc);
                    break;
                case apsc:
                    setDialMode(DialMode.drive);
                    break;

            }
            return true;
        }
    }

    @Override
    protected boolean onLeftKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onLeftKeyUp()
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewHorizontal((int)(-500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRightKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onRightKeyUp()
    {
        ToneGenerator tone= new ToneGenerator(AudioManager.USE_DEFAULT_STREAM_TYPE,100);;
        tone.startTone(ToneGenerator.TONE_CDMA_PIP,1500);
        if (m_curPreviewMagnification != 0)
        {
            movePreviewHorizontal((int)(500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onShutterKeyUp()
    {
        m_shutterKeyDown = false;
        return true;
    }

    @Override
    protected boolean onShutterKeyDown()
    {
        // direct shutter...

        return true;
    }

    @Override
    protected boolean onDeleteKeyUp()
    {
        // Exiting, make sure the app isn't restarted
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getComponentName().getPackageName());
        intent.putExtra("class_name", getComponentName().getClassName());
        intent.putExtra("pullingback_key", new String[] {});
        intent.putExtra("resume_key", new String[] {});
        sendBroadcast(intent);
        onBackPressed();
        return true;
    }

    private void SetDisplayOnOff(int DisplayStatus)
    {
        com.sony.scalar.hardware.avio.DisplayManager Display=getDisplayManager();
        if (DisplayStatus==0)
        {
            m_autoReviewControl.setPictureReviewTime(0);
           Display.switchDisplayOutputTo(DisplayManager.DEVICE_ID_NONE);
        }
        else
        {
            m_autoReviewControl.setPictureReviewTime(10);
            Display.switchDisplayOutputTo(DisplayManager.DEVICE_ID_PANEL);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        final int scanCode = event.getScanCode();
        //showMessage((valueOf(scanCode)));

        if (scanCode==BUT_AEL)
        {
            if (m_DisplayStatus == 1)
                m_DisplayStatus=0;
            else
                m_DisplayStatus=1;
            //showMessage((valueOf(m_DisplayStatus)));
            SetDisplayOnOff(m_DisplayStatus);
        }

        if (m_timelapseActive && scanCode != ScalarInput.ISV_KEY_ENTER)
        {
            return true;
        }
        if ((m_dialMode == DialMode.timelapseSetInterval || m_dialMode == DialMode.timelapseSetPicCount)&&scanCode != ScalarInput.ISV_KEY_ENTER)
        {
            //showMessage((valueOf(m_dialMode)));
            return super.onKeyDown(keyCode, event);
        }

        //ISO Speed
        if (scanCode==BUT_ISO_CW)
        {
            int iso =  getNextIso(m_curIso);
            if (iso== m_supportedIsos.get(m_supportedIsos.size() - 1))
                iso=(m_supportedIsos.get(1));
            setIso(iso);

        }

        if (scanCode==BUT_ISO_CCW)
        {
            final int iso =  getPreviousIso(m_curIso);
            if (iso != 0)
                setIso(iso);
            else
                setIso(m_supportedIsos.get(m_supportedIsos.size() - 1));

        }
        if (scanCode==BUT_FRONT_CW)
            m_camera.incrementShutterSpeed();

        if (scanCode==BUT_FRONT_CCW)
            m_camera.decrementShutterSpeed();

        if (scanCode==BUT_REAR_CW)
            m_camera.incrementAperture();

        if (scanCode==BUT_REAR_CCW)
            m_camera.decrementAperture();


        // TODO: Use m_supportedPreviewMagnifications
        if (m_dialMode != DialMode.timelapseSetInterval && m_dialMode != DialMode.timelapseSetPicCount)
        {
            //original scancode 610
            //622 =C1
            if (scanCode == BUT_C1) // && !m_zoomLeverPressed)
            {
                // zoom lever tele
                m_zoomLeverPressed = true;
                if (m_curPreviewMagnification == 0)
                {
                    m_curPreviewMagnification = 100;
                    m_lFocusScale.setVisibility(View.GONE);
                }
                else
                    m_curPreviewMagnification = 200;
                m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
                return true;
            }
            //original scancode 611
            // 623 = C2
            else if (scanCode == BUT_C2) // && !m_zoomLeverPressed)
            {
                // zoom lever wide
                m_zoomLeverPressed = true;
                if (m_curPreviewMagnification == 200)
                {
                    m_curPreviewMagnification = 100;
                    m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
                }
                else
                {
                    m_curPreviewMagnification = 0;
                    m_camera.stopPreviewMagnification();
                }
                showMessage((valueOf(m_curPreviewMagnification)));
                return true;
            }
            else if (scanCode == 645)
            {
                // zoom lever returned to neutral position
                m_zoomLeverPressed = false;
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try
        {
            Camera cam = m_camera.getNormalCamera();
            cam.setPreviewDisplay(holder);
            cam.startPreview();
        }
        catch (IOException e)
        {
            m_tvMsg.setText("Error starting preview!");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        //log(String.format("surfaceChanged width %d height %d\n", width, height));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    protected void setColorDepth(boolean highQuality)
    {
        super.setColorDepth(false);
    }


    private void ToggleAApsc()
    {

        final Camera.Parameters Currentparams = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier CurrentparamsModifier = m_camera.createParametersModifier(Currentparams);
        String ApscMode= CurrentparamsModifier.getApscMode();

        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);


        if (ApscMode.equals(CameraEx.ParametersModifier.APSC_MODE_ON))
        {
            modifier.setApscMode(CameraEx.ParametersModifier.APSC_MODE_OFF);
            m_Apsc.setText("FULL");
        }
        else
        {
            modifier.setApscMode(CameraEx.ParametersModifier.APSC_MODE_ON);
            m_Apsc.setText("APSC");

        }

        //modifier.setApscMode(CameraEx.ParametersModifier.APSC_MODE_OFF);
        m_camera.getNormalCamera().setParameters(params);


    }

    void InitApsc()
    {

        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);

        modifier.setApscMode(CameraEx.ParametersModifier.APSC_MODE_OFF);
        m_Apsc.setText("FULL");
        m_camera.getNormalCamera().setParameters(params);


    }
}
