package com.obsidium.bettermanual;

import android.hardware.Camera;
import android.util.Pair;
import com.sony.scalar.hardware.CameraEx;

import java.util.List;

public class cExposureControl
{    private CameraEx m_camera;

    //Exposure Parameters
    private int m_curIso;
    private Pair<Integer, Integer> m_ShutterSpeed;
    private int m_aperture;


    private List<Integer> m_supportedIsos;

    // Shutter speed
    private boolean m_notifyOnNextShutterSpeedChange;

    // Aperture
    private boolean m_haveApertureControl;


    cExposureControl(CameraEx Camera)
    {
        m_camera=Camera;

        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);

        //Init Iso
        m_supportedIsos = (List<Integer>)paramsModifier.getSupportedISOSensitivities();
        m_curIso = paramsModifier.getISOSensitivity();

        // Init Shutter Spped
        //m_camera.setShutterSpeedChangeListener(this);
        //m_camera.setShutterListener(this);
        m_ShutterSpeed = paramsModifier.getShutterSpeed();

        //Aperure
        m_aperture=  paramsModifier.getAperture();
        m_haveApertureControl=(m_aperture!=0);


    }


    //Iso Control
    public void incrementIso()
    {
        getIso();
        int i=m_supportedIsos.indexOf(m_curIso);
        if ((i == -1) || (i>m_supportedIsos.size()-2))
            m_curIso=m_supportedIsos.get(1);
        else
            m_curIso=m_supportedIsos.get(i+1);

        setIso(m_curIso);
        while (m_curIso!=ReadIso());
    }

    public void decrementIso()
    {
        getIso();
        int i=m_supportedIsos.indexOf(m_curIso);
        if (i == -1)
            m_curIso=m_supportedIsos.get(1);
        else if (i==1)
            m_curIso=m_supportedIsos.get(m_supportedIsos.size()-1);
        else
            m_curIso=m_supportedIsos.get(i-1);

        setIso(m_curIso);
        while (m_curIso!=ReadIso());
    }



    public void setIso(int iso)
    {
        //Avoid switch to Auto Iso
        if (iso==0) iso=1;
        m_curIso = iso;
        Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setISOSensitivity(iso);
        m_camera.getNormalCamera().setParameters(params);
    }


    public int getIso()
    {
        //m_curIso = ReadIso();
        return m_curIso;
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

    public void setShutterSpeed(Pair<Integer, Integer> ShuuterSpped)
    {

    }

    public Pair<Integer, Integer> getShutterSpeed()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        m_ShutterSpeed= paramsModifier.getShutterSpeed();
        return m_ShutterSpeed;
    }

    public String getShutterSpeedTxt()
    {
        getShutterSpeed();
        return CameraUtil.formatShutterSpeed(m_ShutterSpeed.first, m_ShutterSpeed.second);
    }

    //Aperture Control
    public void incrementAperture()
    {
        m_camera.incrementAperture();
    }

    public void decrementAperture()
    {
        m_camera.decrementAperture();
    }

    public void setAperture()
    {

    }

    public int getAperture()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        m_aperture=paramsModifier.getAperture();
        m_haveApertureControl=(m_aperture!=0);
        return m_aperture;
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

}
