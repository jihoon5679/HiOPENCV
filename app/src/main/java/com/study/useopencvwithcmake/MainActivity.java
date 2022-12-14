package com.study.useopencvwithcmake;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

import microsoft.aspnet.signalr.client.Platform;
import microsoft.aspnet.signalr.client.http.android.AndroidPlatformComponent;

import java.time.LocalDate;
import java.time.ZoneId;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "opencv";
    private Mat matInput;
    private Mat matResult;

    // private CurrentDateTime currentDateTime;


    private CameraBridgeViewBase mOpenCvCameraView;

    // public native void ConvertRGBtoGray(long matAddrInput, long matAddrResult);
    // OpenCV ???????????? ?????????????????? C++????????? ????????? ?????????????????? ??????

    public native long loadCascade(String cascadeFileName);

    public native int detect(long cascadeClassifier_face,
                             long cascadeClassifier_eye, long matAddrInput, long matAddrResult);

    public long cascadeClassifier_face = 0;
    public long cascadeClassifier_eye = 0;





    //??????????????? ???????????? ?????? ??????

    private final Semaphore writeLock = new Semaphore(1);

    public void getWriteLock() throws InterruptedException {
        writeLock.acquire();
    }

    public void releaseWriteLock() {
        writeLock.release();
    }


    private void copyFile(String filename) {

        AssetManager assetManager = this.getAssets();
        File outputFile = new File(getFilesDir() + "/" + filename);

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d(TAG, "copyFile :: ?????? ????????? ???????????? " + outputFile.toString());
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: ?????? ?????? ??? ?????? ?????? " + e.toString());
        }

    }


    private void read_cascade_file() {
        copyFile("haarcascade_frontalface_alt.xml");
        copyFile("haarcascade_eye_tree_eyeglasses.xml");

        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_face = loadCascade(getFilesDir().getAbsolutePath() + "/haarcascade_frontalface_alt.xml");
        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_eye = loadCascade(getFilesDir().getAbsolutePath() + "/haarcascade_eye_tree_eyeglasses.xml");
    }

    static {

        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    // ???????????? ??? ?????? ????????? ????????? ???????????????
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    //????????? ?????????????????? ??????????????? ?????? ??? onCreate????????? ?????? ????????????.
    // ?????? ??????????????? ???????????? ??? ?????? ???????????? ?????? ???????????? ?????? ???????????? ????????? ?????????????????? ??????????????? ????????? ?????? ??????????????? ????????? ?????? ?????? ????????? ??????. ?????? ?????? ????????? ?????? ???????????? ??????????? savedInstanceState??? ???????????? ?????? ??????.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(1); // front-camera(1),  back-camera(0)

    }


    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    public void onDestroy() {
        super.onDestroy();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    // implements CameraBridgeViewBase.CvCameraViewListener2 ?????????
    /* camera started : ????????? ???????????? ???????????? ????????????.
       camera viewstopped : ????????? ???????????? ?????? ????????? ????????? ????????????.
       camera frame : ????????? ????????? ????????? ?????? ?????? ??????.
    */
    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    //???????????? ?????? ?????????
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        try {
            getWriteLock();
            matInput = inputFrame.rgba();

            if (matResult == null)

                matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());


            Core.flip(matInput, matInput, 1);

            int faceSize = detect(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());


            if (faceSize > 0) //&& (CurrentDateTime - previousTime) > 3000)
            {
                // ??????
                saveImage();


            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        releaseWriteLock();
        return matResult;
    }


    //
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }


    //??????????????? ????????? ?????? ?????????
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;


    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase : cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();

                read_cascade_file();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean havePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
                havePermission = false;
            }
        }
        if (havePermission) {
            onCameraPermissionGranted();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted();
        } else {
            showDialogForPermission("?????? ??????????????? ???????????? ????????????????????????.");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("??????");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("???", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("?????????", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }



    void saveImage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //?????? ??????
                try {
                    getWriteLock();

                    String date = dateName(System.currentTimeMillis());
                    //?????? ????????? ????????? ?????? ?????? ???????????? , ????????? ??? ?????? ???,??? ??? ???????????? ,if(??? ??????)
                    // if(??? ??????) ????????? ??? ????????? ??????  if ( ??? date - ??? ?????? >  3){}
                    File path = new File(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
                    path.mkdirs();
                    File file = new File(path,date + ".jpg");
                    String fileName = file.toString();


                    Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                    boolean ret = Imgcodecs.imwrite(fileName, matResult);
                    if (ret) {
                        Log.d(TAG, "SUCCESS");
                    } else {
                        Log.d(TAG, "FAIL");
                    }

                    //???????????? ?????? ??????
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);



                } catch (InterruptedException e) {
                    Toast.makeText(getApplicationContext(), "?????? ?????? ??? ?????? ??????", Toast.LENGTH_SHORT).show();
                }

                //?????? ??????
                releaseWriteLock();

            }
        }).start();
    }

    private String dateName(long dateTaken){
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        return dateFormat.format(date);
    }





}
