package com.sciaps.android.camera;

import android.app.Application;
import android.os.Environment;

import com.sciaps.libz.hardware.HardwareConfigModule;
import com.sciaps.common.LIBZCommonModule;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.sciaps.libz.hardware.HardwareModule;

import java.io.File;


public class CameraCalApp extends Application {

    private Injector mInjector;

    public Injector getInjector() {
        if (mInjector == null) {

            mInjector = Guice.createInjector(
                    new HardwareConfigModule(new File(Environment.getExternalStorageDirectory(), "sciaps")),
                    new HardwareModule(),
                    new LIBZCommonModule()
            );
        }
        return mInjector;
    }
}
