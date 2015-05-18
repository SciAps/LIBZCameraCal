package com.sciaps.android.camera;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.devsmart.android.BackgroundTask;
import com.google.inject.Inject;
import com.sciaps.libz.hardware.TakePix;
import com.sciaps.libz.hardware.XYZStageParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Inject
    XYZStageParams mXyzStageParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cameracal);
        mPreviewView = (SurfaceView) findViewById(R.id.preview);
        mPreviewView.setOnTouchListener(mOnPreviewTouch);
        mDoneButton = (Button) findViewById(R.id.done);
        mDoneButton.setOnClickListener(mOnDoneClicked);

        mBmpPaint.setFilterBitmap(true);

    }

    private View.OnClickListener mOnDoneClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            RectF r = new RectF();
            Matrix m = new Matrix();


            float[] srcPoints = new float[8];
            float[] destPoints = new float[8];


            //top left
            srcPoints[0] = mXyzStageParams.endLocation[0];
            srcPoints[1] = mXyzStageParams.startLocation[1];
            destPoints[0] = mReticalPoints[0];
            destPoints[1] = mReticalPoints[1];

            //top right
            srcPoints[2] = mXyzStageParams.startLocation[0];
            srcPoints[3] = mXyzStageParams.startLocation[1];
            destPoints[2] = mReticalPoints[2];
            destPoints[3] = mReticalPoints[3];

            //bottom right
            srcPoints[4] = mXyzStageParams.startLocation[0];
            srcPoints[5] = mXyzStageParams.endLocation[1];
            destPoints[4] = mReticalPoints[4];
            destPoints[5] = mReticalPoints[5];

            //bottom left
            srcPoints[6] = mXyzStageParams.endLocation[0];
            srcPoints[7] = mXyzStageParams.endLocation[1];
            destPoints[6] = mReticalPoints[6];
            destPoints[7] = mReticalPoints[7];



            Matrix stageToPreview = new Matrix();
            stageToPreview.setPolyToPoly(srcPoints, 0, destPoints, 0, 4);

            m.setRectToRect(mPreviewSize, mPhotoSize, Matrix.ScaleToFit.CENTER);
            Matrix stageToPhoto = new Matrix(stageToPreview);
            stageToPhoto.postConcat(m);

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        final Handler handler = new Handler(getMainLooper());
        if(mPhoto == null) {
            BackgroundTask.runBackgroundTask(new BackgroundTask() {

                TakePix mTakePix;

                @Override
                public void onBackground() {
                    mTakePix = mInjector.getInstance(TakePix.class);
                }

                @Override
                public void onAfter() {
                    mTakePix.setCallback(mOnPhoto);
                    mTakePix.takePicture();
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
            mReticalPoints[0] = mPreviewSize.width()/2 - 150;
            mReticalPoints[1] = mPreviewSize.height()/2 - 150;

            //top right
            mReticalPoints[2] = mPreviewSize.width()/2 + 150;
            mReticalPoints[3] = mPreviewSize.height()/2 - 150;

            //bottom right
            mReticalPoints[4] = mPreviewSize.width()/2 + 150;
            mReticalPoints[5] = mPreviewSize.height()/2 + 150;

            //bottom left
            mReticalPoints[6] = mPreviewSize.width()/2 - 150;
            mReticalPoints[7] = mPreviewSize.height()/2 + 150;

            drawFrame();

        }
    };

    private static final float HITBOX_RADIUS = 30;

    private View.OnTouchListener mOnPreviewTouch = new View.OnTouchListener() {



        class DragHandler {

            private final int mCorner;

            public DragHandler(int i, MotionEvent e) {
                mCorner = i;
                mReticalPoints[2*i] = e.getX();
                mReticalPoints[2*i+1] = e.getY();
                drawFrame();
            }

            public void handleMove(MotionEvent e) {
                mReticalPoints[2*mCorner] = e.getX();
                mReticalPoints[2*mCorner+1] = e.getY();
                drawFrame();
            }


        }

        DragHandler mDragHandler;

        private boolean hitCorner(MotionEvent e, int i) {
            float x = e.getX();
            float y = e.getY();

            return x > mReticalPoints[2*i] - HITBOX_RADIUS/2 && x < mReticalPoints[2*i] + HITBOX_RADIUS/2
                    && y > mReticalPoints[2*i+1] - HITBOX_RADIUS/2 && y < mReticalPoints[2*i+1] + HITBOX_RADIUS/2;
        }

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch(motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    for(int i=0;i<4;i++){
                        if(hitCorner(motionEvent, i)) {
                            mDragHandler = new DragHandler(i, motionEvent);
                            return true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if(mDragHandler != null) {
                        mDragHandler.handleMove(motionEvent);
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if(mDragHandler != null) {
                        mDragHandler = null;
                        return true;
                    }
                    break;
            }

            return false;
        }
    };

    private void drawFrame() {
        SurfaceHolder holder = mPreviewView.getHolder();
        Canvas canvas = holder.lockCanvas();
        try {

            canvas.drawBitmap(mPhoto, mPhotoToPreview, mBmpPaint);

            mPaint.setColor(Color.RED);
            mPaint.setStrokeWidth(2.0f);
            mPaint.setStyle(Paint.Style.STROKE);


            for(int i=0;i<4;i++) {
                int x1 = (2*i) % 8;
                int y1 = (2*i+1) % 8;
                int x2 = (2*i+2) % 8;
                int y2 = (2*i+3) % 8;

                canvas.drawCircle(mReticalPoints[x1], mReticalPoints[y1], HITBOX_RADIUS, mPaint);

                canvas.drawLine(mReticalPoints[x1], mReticalPoints[y1], mReticalPoints[x2], mReticalPoints[y2], mPaint);
            }

        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

}
