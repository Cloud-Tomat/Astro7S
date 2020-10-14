package com.obsidium.bettermanual;

import android.hardware.Camera;

import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.hardware.avio.DisplayManager;

public class cCameraControl
{
    private CameraEx        m_camera;

    cCameraControl(CameraEx Camera)
    {
        m_camera=Camera;
    }

    public void setSceneMode(String mode)
    {
        Camera.Parameters params = m_camera.createEmptyParameters();
        params.setSceneMode(mode);
        m_camera.getNormalCamera().setParameters(params);
    }

    public void disableLENR()
    {
        // Disable long exposure noise reduction
        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(m_camera.getNormalCamera().getParameters());
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);
        if (paramsModifier.isSupportedLongExposureNR())
            modifier.setLongExposureNR(false);
        m_camera.getNormalCamera().setParameters(params);


    }

    public void setSilentShutter()
    {
        Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setSilentShutterMode(true);
        m_camera.getNormalCamera().setParameters(params);
    }



    public void SetApsc(boolean Apsc)
    {

        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);

        if (Apsc)
            modifier.setApscMode(CameraEx.ParametersModifier.APSC_MODE_ON);
        else
            modifier.setApscMode(CameraEx.ParametersModifier.APSC_MODE_OFF);

        m_camera.getNormalCamera().setParameters(params);
    }

    public boolean GetApsc()
    {
        final Camera.Parameters Currentparams = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier CurrentparamsModifier = m_camera.createParametersModifier(Currentparams);
        String ApscMode= CurrentparamsModifier.getApscMode();

        return (ApscMode.equals(CameraEx.ParametersModifier.APSC_MODE_ON));
    }



}
