package com.obsidium.bettermanual;

import android.hardware.Camera;
import android.util.Pair;
import com.sony.scalar.hardware.CameraEx;

import java.util.List;

public class CExposureControl
{
    private CameraEx m_camera;

    //Exposure Parameters
    private int m_curIso;
    private Pair<Integer, Integer> m_ShutterSpeed;
    private int m_aperture;


    private List<Integer> m_supportedIsos;

    // Shutter speed
    private boolean m_notifyOnNextShutterSpeedChange;

    // Aperture
    private boolean m_haveApertureControl;


    CExposureControl(CameraEx Camera)
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
    public int getPreviousIso(int current)
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

    public int getNextIso(int current)
    {
        boolean next = false;
        for (Integer iso : m_supportedIsos)
        {
            if (next)
                return iso;
            else if (iso == current)
                next = true;
        }
        return getFirstManualIso();
    }

    public int getFirstManualIso()
    {
        for (Integer iso : m_supportedIsos)
        {
            if (iso != 0)
                return iso;
        }
        return 0;
    }

    public void setIso(int iso)
    {
        //Avoid switch to Auto Iso
        if (iso==0)
            iso=getFirstManualIso();
        m_curIso = iso;
        Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setISOSensitivity(iso);
        m_camera.getNormalCamera().setParameters(params);
    }

    public int getIso()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        m_curIso = paramsModifier.getISOSensitivity();
        return m_curIso;
    }

    public String getIsoTxt()
    {
        String.format("\uE488 %d", getIso());
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
