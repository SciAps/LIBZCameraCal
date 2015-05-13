package com.sciaps.android.camera;

import android.os.Bundle;

import com.google.inject.Injector;
import com.sciaps.androidcommon.lifecycle.LifecycleActivity;
import com.sciaps.androidcommon.lifecycle.LifecycleHook;


public class InjectLifecycleActivity extends LifecycleActivity {

    protected Injector mInjector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mInjector = ((CameraCalApp)getApplication()).getInjector();
        mInjector.injectMembers(this);

        for (LifecycleHook hook : mLifecycleHooks) {
            mInjector.injectMembers(hook);
        }

        super.onCreate(savedInstanceState);
    }
}
