package com.sciaps.android.camera;


import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = new MenuInflater(this);
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_takepix:
                takePicture();
            return true;

            default:
                return super.onOptionsItemSelected(item);
        }
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

            final Matrix stageToPhoto = new Matrix(stageToPreview);
            createTransformMatrix(m, mPreviewSize, mPhotoSize);
            stageToPhoto.postConcat(m);

            Matrix photoToStage = new Matrix(mPhotoToPreview);
            m.setPolyToPoly(destPoints, 0, srcPoints, 0, 4);
            photoToStage.postConcat(m);

            BackgroundTask.runBackgroundTask(new BackgroundTask() {

                public Bitmap mBitmap;

                Quadrilateral quadA = new Quadrilateral();
                Quadrilateral quadB = new Quadrilateral();

                @Override
                public void onBackground() {

                    mBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565);
                    Canvas c = new Canvas(mBitmap);


                    quadA.setRect(new RectF(mXyzStageParams.endLocation[0], mXyzStageParams.startLocation[1], mXyzStageParams.startLocation[0], mXyzStageParams.endLocation[1]));
                    stageToPhoto.mapPoints(quadA.mPoints);

                    quadB.setRect(new RectF(0, 0, 256, 256));
                    Matrix photoToMiniPreview = new Matrix();
                    createTransformMatrix(photoToMiniPreview, quadA, quadB);

                    c.drawBitmap(mPhoto, photoToMiniPreview, mBmpPaint);

                }

                @Override
                public void onAfter() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(CameraCalActivity.this);
                    ImageView imageView = new ImageView(CameraCalActivity.this);
                    imageView.setImageBitmap(mBitmap);
                    builder.setView(imageView);
                    builder.setPositiveButton("OK", null);
                    builder.show();

                }
            });

        }
    };


    private static class Quadrilateral {

        public static final int TOP_LEFTX = 0;
        public static final int TOP_LEFTY = 1;
        public static final int TOP_RIGHTX = 2;
        public static final int TOP_RIGHTY = 3;
        public static final int BOTTOM_RIGHTX = 4;
        public static final int BOTTOM_RIGHTY = 5;
        public static final int BOTTOM_LEFTX = 6;
        public static final int BOTTOM_LEFTY = 7;

        public final float[] mPoints = new float[8];

        public Quadrilateral() {

        }

        public void setRect(RectF rect) {
            mPoints[TOP_LEFTX] = rect.left;
            mPoints[TOP_LEFTY] = rect.top;
            mPoints[TOP_RIGHTX] = rect.right;
            mPoints[TOP_RIGHTY] = rect.top;
            mPoints[BOTTOM_RIGHTX] = rect.right;
            mPoints[BOTTOM_RIGHTY] = rect.bottom;
            mPoints[BOTTOM_LEFTX] = rect.left;
            mPoints[BOTTOM_LEFTY] = rect.bottom;
        }
    }

    private void createTransformMatrix(Matrix matrix, Quadrilateral src, Quadrilateral dst) {
        matrix.setPolyToPoly(src.mPoints, 0, dst.mPoints, 0, 4);
    }

    private void createTransformMatrix(Matrix matrix, RectF src, RectF dst) {
        float[] srcPoints = new float[8];
        float[] destPoints = new float[8];


        //top left
        srcPoints[0] = src.left;
        srcPoints[1] = src.top;
        destPoints[0] = dst.left;
        destPoints[1] = dst.top;

        //top right
        srcPoints[2] = src.right;
        srcPoints[3] = src.top;
        destPoints[2] = dst.right;
        destPoints[3] = dst.top;

        //bottom right
        srcPoints[4] = src.right;
        srcPoints[5] = src.bottom;
        destPoints[4] = dst.right;
        destPoints[5] = dst.bottom;

        //bottom left
        srcPoints[6] = src.left;
        srcPoints[7] = src.bottom;
        destPoints[6] = dst.left;
        destPoints[7] = dst.bottom;

        matrix.setPolyToPoly(srcPoints, 0, destPoints, 0, 4);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mPhoto == null) {
            //takePicture();
        }
    }

    private void takePicture() {
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
