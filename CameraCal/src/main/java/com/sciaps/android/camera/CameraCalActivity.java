package com.sciaps.android.camera;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;

import com.devsmart.ThreadUtils;
import com.devsmart.android.BackgroundTask;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.sciaps.libz.hardware.HardwareModule;
import com.sciaps.libz.hardware.IHeadlights;
import com.sciaps.libz.hardware.IXYZStage;
import com.sciaps.libz.hardware.XYZStageParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

public class CameraCalActivity extends InjectLifecycleActivity {

    private static Logger logger = LoggerFactory.getLogger(CameraCalActivity.class);
    private SurfaceView mPreviewView;
    private Button mDoneButton;


    private Bitmap mPhoto;
    private RectF mPhotoSize = new RectF();
    private RectF mPreviewSize = new RectF();
    private Matrix mPhotoToPreview = new Matrix();
    private Paint mBmpPaint = new Paint();
    private Paint mPaint = new Paint();
    private float[] mReticalPoints = new float[8];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cameracal);
        mPreviewView = (SurfaceView) findViewById(R.id.preview);
        mDoneButton = (Button) findViewById(R.id.done);

        mBmpPaint.setFilterBitmap(true);

    }

    @Override
    protected void onResume() {
        super.onResume();
        final Handler handler = new Handler(getMainLooper());
        if(mPhoto == null) {
            ThreadUtils.IOThreads.execute(new Runnable() {
                @Override
                public void run() {
                    TakePix pix = mInjector.getInstance(TakePix.class);
                    pix.setMainHandler(handler);
                    pix.setCallback(mOnPhoto);
                    pix.setPreviewDisplay(mPreviewView.getHolder());
                    pix.doIt();
                }
            });
        }
    }

    private TakePix.Callback mOnPhoto = new TakePix.Callback() {
        @Override
        public void onPhoto(Bitmap bmp) {
            mPhoto = bmp;
            mPhotoSize.set(0, 0, bmp.getWidth(), bmp.getHeight());
            mPreviewSize.set(mPreviewView.getHolder().getSurfaceFrame());
            mPhotoToPreview.setRectToRect(mPhotoSize, mPreviewSize, Matrix.ScaleToFit.CENTER);

            //top left
            mReticalPoints[0] = 50;
            mReticalPoints[1] = 50;

            //top right
            mReticalPoints[2] = 150;
            mReticalPoints[3] = 50;

            //bottom right
            mReticalPoints[4] = 150;
            mReticalPoints[5] = 150;

            //bottom left
            mReticalPoints[6] = 50;
            mReticalPoints[7] = 150;

            drawFrame();

        }
    };

    private void drawFrame() {
        SurfaceHolder holder = mPreviewView.getHolder();
        Canvas canvas = holder.lockCanvas();
        try {

            canvas.drawBitmap(mPhoto, mPhotoToPreview, mBmpPaint);

            mPaint.setColor(Color.RED);
            mPaint.setStrokeWidth(5.0f);

            for(int i=0;i<4;i++) {
                int x1 = (2*i) % 8;
                int y1 = (2*i+1) % 8;
                int x2 = (2*i+2) % 8;
                int y2 = (2*i+3) % 8;
                canvas.drawLine(mReticalPoints[x1], mReticalPoints[y1], mReticalPoints[x2], mReticalPoints[y2], mPaint);
            }

        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private static class TakePix implements Camera.PictureCallback {


        private SurfaceHolder mSurfaceHolder;

        public interface Callback {
            void onPhoto(Bitmap bmp);
        }

        @Inject
        IXYZStage mXYZStage;

        @Inject
        XYZStageParams mXyzStageParams;

        @Inject
        IHeadlights mHeadlights;

        @Inject @Named(HardwareModule.INJECT_HARDWARE_FPGAEXECUTOR)
        ScheduledExecutorService mFPGAThread;

        private Camera mCamera;
        private SurfaceTexture mPreviewSurface;
        private Handler mMainHandler;
        private Callback mCallback;

        public void setMainHandler(Handler mainHandler) {
            mMainHandler = mainHandler;
        }

        public void setCallback(Callback cb) {
            mCallback = cb;
        }

        public void setPreviewDisplay(SurfaceHolder holder) {
            mSurfaceHolder = holder;
        }

        public void doIt() {

            mXYZStage.homeStageAxis(IXYZStage.XAXIS, null);
            mXYZStage.homeStageAxis(IXYZStage.YAXIS, null);
            mXYZStage.homeStageAxis(IXYZStage.ZAXIS, null);

            int[] center = new int[3];

            center[0] = (mXyzStageParams.endLocation[0] - mXyzStageParams.startLocation[0]) / 2;
            center[1] = (mXyzStageParams.endLocation[1] - mXyzStageParams.startLocation[1]) / 2;
            center[2] = mXyzStageParams.startLocation[2];

            mXYZStage.moveStageAxis(IXYZStage.XAXIS, center[0], null);
            mXYZStage.moveStageAxis(IXYZStage.YAXIS, center[1], null);
            mXYZStage.moveStageAxis(IXYZStage.ZAXIS, center[2], new Runnable() {
                @Override
                public void run() {
                    mHeadlights.setLED1(0f);
                    mHeadlights.setLED2(0f);
                    mMainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            takePicture();
                        }
                    }, 500);

                }
            });



        }

        private void takePicture() {

            mPreviewSurface = new SurfaceTexture(10);

            mCamera = Camera.open();

            Camera.Parameters params = mCamera.getParameters();
            params.setRotation(90);
            params.setPreviewSize(640, 480);
            params.setPictureSize(640, 480);
            params.setSceneMode("closeup");
            params.setFocusMode("macro");
            params.setPictureFormat(ImageFormat.JPEG);
            mCamera.setParameters(params);

            mCamera.setDisplayOrientation(90);

            try {
                //mCamera.setPreviewTexture(mPreviewSurface);
                mCamera.setPreviewDisplay(mSurfaceHolder);

                mCamera.startPreview();

                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mCamera.takePicture(null, null, null, TakePix.this);
                    }
                }, 500);


            } catch (IOException e) {
                logger.error("", e);
            }
        }

        @Override
        public void onPictureTaken(final byte[] pixdata, Camera camera) {
            /*
            mFPGAThread.execute(new Runnable() {
                @Override
                public void run() {
                    mHeadlights.setLED1(1.0f);
                    mHeadlights.setLED2(1.0f);
                }
            });
            */
            mCamera.release();
            mCamera = null;


            BackgroundTask.runBackgroundTask(new BackgroundTask() {
                public Bitmap mPhoto;

                @Override
                public void onBackground() {
                    mPhoto = BitmapFactory.decodeByteArray(pixdata, 0, pixdata.length);
                }

                @Override
                public void onAfter() {
                    mCallback.onPhoto(mPhoto);
                }
            });

        }
    }

}
