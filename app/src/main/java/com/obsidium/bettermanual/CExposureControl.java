package com.obsidium.bettermanual;

import android.hardware.Camera;
import android.os.Handler;
import android.util.Pair;
import com.sony.scalar.hardware.CameraEx;

import java.util.List;
import java.util.Set;

public class cExposureControl implements CameraEx.ShutterSpeedChangeListener
{

    public ManualActivity ma;

    public final Integer NbSet=2;

    private CameraEx m_camera;
    private boolean m_shutterChanged=false;
    private final Handler m_handler = new Handler();

    private Runnable m_shuuterChangeCallback;


    private Pair<Integer, Integer> m_TargetShutterSpeed;
    private int m_TargetAperture;

    private int m_Iso[];
    private Pair<Integer,Integer> m_ShutterSpeed[];
    private int m_Aperture[];

    private int m_CurrentSet=0;

    private List<Integer> m_supportedIsos;

    // Shutter speed
    private boolean m_notifyOnNextShutterSpeedChange;

    // Aperture
    private boolean m_haveApertureControl;

    private String t_PreviewMode;

    private final Runnable  adjustShutterSpeed = new Runnable()
    {
        @Override
        public void run()
        {
        float Delta=CompoareShutterSpeed(m_ShutterSpeed[m_CurrentSet],m_TargetShutterSpeed);
        if (Delta > 0)
            incrementShutterSpeed();
        else if (Delta!=0)
            decrementShutterSpeed();
        }
    };


    cExposureControl(CameraEx Camera)
    {
        m_camera=Camera;

        m_CurrentSet=0;

        m_ShutterSpeed=new Pair[NbSet];
        m_Iso=new int[NbSet];
        m_Aperture=new int[NbSet];

        m_TargetShutterSpeed=new Pair <Integer, Integer>(-1,-1);
        //m_shutterChanged=false;

        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);

        //Init Iso
        m_supportedIsos = (List<Integer>)paramsModifier.getSupportedISOSensitivities();
        m_Iso[0] = paramsModifier.getISOSensitivity();
        // Init Shutter Spped
        m_camera.setShutterSpeedChangeListener(this);
        m_ShutterSpeed[0] = paramsModifier.getShutterSpeed();

        m_TargetShutterSpeed = new Pair<Integer, Integer>(1,30);
        //Aperure
        m_Aperture[0]=  paramsModifier.getAperture();
        m_haveApertureControl=(m_Aperture[0]!=0);

        for (int i=1;i<NbSet;i++)
        {
            m_Aperture[i]=m_Aperture[0];
            m_Iso[i]=m_Iso[0];
            m_ShutterSpeed[i]=new Pair<Integer, Integer>(m_ShutterSpeed[0].first,m_ShutterSpeed[0].second);
        }


        List PreviewMode;
        PreviewMode=m_camera.createParametersModifier(params).getSupportedShootingPreviewModes();
        t_PreviewMode=new String(  PreviewMode.toString());

    }

    public String t_pm()
    {
        ma.showMessage(t_PreviewMode);
        return t_PreviewMode;
    }

    public void pullExposure()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);

        // Iso
        m_Iso[0] = paramsModifier.getISOSensitivity();

        //Aperture
        m_Aperture[0]=  paramsModifier.getAperture();
        m_haveApertureControl=(m_Aperture[0]!=0);

        //Shutter Speed
        m_ShutterSpeed[0] = paramsModifier.getShutterSpeed();

        for (int i=1;i<NbSet;i++)
        {
            m_Aperture[i]=m_Aperture[0];
            m_Iso[i]=m_Iso[0];
            m_ShutterSpeed[i]=new Pair<Integer, Integer>(m_ShutterSpeed[0].first,m_ShutterSpeed[0].second);
        }
    }


    public void ChangeSet(int SetId)
    {

        if (SetId<0) SetId=0;
        if (SetId>(NbSet-1)) SetId=NbSet-1;

        m_CurrentSet=SetId;
        setIso(m_Iso[m_CurrentSet]);
        setShutterSpeed(m_ShutterSpeed[m_CurrentSet]);



    }


    //Iso Control
    public void incrementIso()
    {
        getIso();
        int i=m_supportedIsos.indexOf(m_Iso[m_CurrentSet]);
        if ((i == -1) || (i>m_supportedIsos.size()-2))
            m_Iso[m_CurrentSet]=m_supportedIsos.get(1);
        else
            m_Iso[m_CurrentSet]=m_supportedIsos.get(i+1);

        setIso(m_Iso[m_CurrentSet]);

    }

    public void decrementIso()
    {
        getIso();
        int i=m_supportedIsos.indexOf(m_Iso[m_CurrentSet]);
        if (i == -1)
            m_Iso[m_CurrentSet]=m_supportedIsos.get(1);
        else if (i==1)
            m_Iso[m_CurrentSet]=m_supportedIsos.get(m_supportedIsos.size()-1);
        else
            m_Iso[m_CurrentSet]=m_supportedIsos.get(i-1);

        setIso(m_Iso[m_CurrentSet]);

    }



    public void setIso(int iso)
    {
        //Avoid switch to Auto Iso
        if (iso==0) iso=m_supportedIsos.get(1);;
        m_Iso[m_CurrentSet] = iso;
        Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setISOSensitivity(iso);

        m_camera.getNormalCamera().setParameters(params);

    }







    public int getIso()
    {
        m_Iso[m_CurrentSet] = ReadIso();
        return m_Iso[m_CurrentSet];
    }



    private int ReadIso()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        return  paramsModifier.getISOSensitivity();
    }


    public String getIsoTxt()
    {
        return String.format("\uE488 %d", getIso());
    }

    public void decrementShutterSpeed()
    {
        m_camera.decrementShutterSpeed();
        getShutterSpeed();
    }

    //Shutter Speed
    public void incrementShutterSpeed()
    {
        m_camera.incrementShutterSpeed();
        getShutterSpeed();
    }

    private float CompoareShutterSpeed(Pair<Integer, Integer> sp1,Pair<Integer, Integer>sp2)
    {
        float fsp1,fsp2;
        fsp1=(float)sp1.first/(float)sp1.second;
        fsp2=(float)sp2.first/(float)sp2.second;
        return fsp1-fsp2;
    }

    public void setShutterSpeed(Pair<Integer, Integer> ShutterSpeed)
    {
        m_shutterChanged=true;
        m_TargetShutterSpeed=new Pair<Integer, Integer>(ShutterSpeed.first,ShutterSpeed.second);
        m_handler.removeCallbacks(adjustShutterSpeed);
        m_handler.post(adjustShutterSpeed);
    }

    public Pair<Integer, Integer> getShutterSpeed()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        m_ShutterSpeed[m_CurrentSet]= paramsModifier.getShutterSpeed();
        return m_ShutterSpeed[m_CurrentSet];
    }

    public String getShutterSpeedTxt()
    {
        getShutterSpeed();
        return CameraUtil.formatShutterSpeed(m_ShutterSpeed[m_CurrentSet].first, m_ShutterSpeed[m_CurrentSet].second);
    }

    //Aperture Control
    public void incrementAperture()
    {
        m_camera.incrementAperture();
        getAperture();
    }

    public void decrementAperture()
    {
        m_camera.decrementAperture();
        getAperture();
    }

    public void setAperture()
    {

    }

    public int getAperture()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        m_Aperture[m_CurrentSet]=paramsModifier.getAperture();
        m_haveApertureControl=(m_Aperture[m_CurrentSet]!=0);
        return m_Aperture[m_CurrentSet];
    }

    public String getApertureTxt()
    {

        return String.format("f%.1f", (float)getAperture() / 100.0f);
    }

    public boolean haveApertureControl()
    {
        getAperture();
        return m_haveApertureControl;
    }

    public void setShutterSpeedCallback(Runnable callback)
    {
        m_shuuterChangeCallback=callback;
    }

    @Override
    public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo shutterSpeedInfo, CameraEx cameraEx)
    {
        boolean TargetReached;

        m_ShutterSpeed[m_CurrentSet]=new Pair <Integer, Integer>(shutterSpeedInfo.currentShutterSpeed_n,shutterSpeedInfo.currentShutterSpeed_d);
        //TargetReached=(m_TargetShutterSpeed.first==m_ShutterSpeed[m_CurrentSet].first) && (m_TargetShutterSpeed.second==m_ShutterSpeed[m_CurrentSet].second);
        TargetReached=m_TargetShutterSpeed.equals(m_ShutterSpeed[m_CurrentSet]);

        if (TargetReached)  m_shutterChanged=false;

        //ma.showMessage(String.valueOf(TargetReached)+"/"+String.valueOf(m_shutterChanged));
        //String Sc0,Sc1;
        //Sc0=new String(CameraUtil.formatShutterSpeed(m_ShutterSpeed[m_CurrentSet].first, m_ShutterSpeed[m_CurrentSet].second));
        //Sc1=new String(CameraUtil.formatShutterSpeed(m_TargetShutterSpeed.first, m_TargetShutterSpeed.second));
        //ma.showMessage(Sc0+"/"+Sc1 + "/" + String.valueOf(TargetReached));

        if ( m_shutterChanged)
        {
            m_handler.removeCallbacks(adjustShutterSpeed);
            m_handler.post(adjustShutterSpeed);
        }
        else
        {
            m_shuuterChangeCallback.run();
        }
    }
}
