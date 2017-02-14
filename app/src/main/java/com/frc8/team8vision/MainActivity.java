package com.frc8.team8vision;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";

    private static Mat intrinsicMatrix;

    private static MatOfDouble distCoeffs;

    private static Mat imageRGB, imageHSV;

    private SketchyCameraView mCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV load success");
                    imageHSV = new Mat();
                    mCameraView.enableView();

                    intrinsicMatrix = new Mat(3, 3, CvType.CV_64F);
                    distCoeffs = new MatOfDouble();

                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                        // Galaxy S4 is being used
                        for (int i = 0; i < 3; i++) {
                            for (int j = 0; j < 3; j++) {
                                intrinsicMatrix.put(i, j, Constants.kGalaxyIntrinsicMatrix[i][j]);
                            }
                        }
                        distCoeffs.fromArray(Constants.kGalaxyDistortionCoefficients);
                    } else {
                        // Nexus 5x is being used
                        for (int i = 0; i < 3; i++) {
                            for (int j = 0; j < 3; j++) {
                                intrinsicMatrix.put(i, j, Constants.kNexusIntrinsicMatrix[i][j]);
                            }
                        }
                        distCoeffs.fromArray(Constants.kNexusDistortionCoefficients);
                    }
                } break;
                default: {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCameraView = new SketchyCameraView(this, -1);
        setContentView(mCameraView);
        mCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mCameraView != null) {
            mCameraView.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraView.toggleFlashLight();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();

        if (mCameraView != null) {
            mCameraView.disableView();
            if (imageHSV != null) imageHSV.release();

        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mCameraView.toggleFlashLight();
    }

    @Override
    public void onCameraViewStopped() {}

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat input = inputFrame.rgba();

        // Debug statement

        input = track(input);
        imageHSV.release();
        return input;
    }

    public Mat track(Mat input) {
        imageRGB = input;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        int[] sliderValues = new int[6];

        for (int i = 0; i < 6; i++) {
            sliderValues[i] = preferences.getInt(Constants.kSliderNames[i], Constants.kSliderDefaultValues[i]);
        }

        // Apply HSV thresholding to input
        Mat mask = new Mat();

        // Lower and upper hsv thresholds
        Scalar lower_bound = new Scalar(sliderValues[0], sliderValues[1], sliderValues[2]),
                upper_bound = new Scalar(sliderValues[3], sliderValues[4], sliderValues[5]);

        Imgproc.cvtColor(input, imageHSV, Imgproc.COLOR_RGB2HSV);
        Core.inRange(imageHSV, lower_bound, upper_bound, mask);

        // Detect contours
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours.isEmpty()) return input;

        // Sort contours by area and identify the two largest
        Collections.sort(contours, new Comparator<MatOfPoint>() {
            @Override
            public int compare(MatOfPoint m1, MatOfPoint m2) {
                double area1 = Imgproc.contourArea(m1), area2 = Imgproc.contourArea(m2);
                if (area1 > area2) return -1;
                if (area1 < area2) return 1;
                return 0;
            }
        });
        List<MatOfPoint> largestTwo = new ArrayList<>();
        largestTwo.add(contours.get(0));
        if (contours.size() > 1) largestTwo.add(contours.get(1));

        // Combine largest two contours
        MatOfPoint combined = new MatOfPoint();
        if (largestTwo.size() == 2) {
            MatOfPoint first = largestTwo.get(0), second = largestTwo.get(1);
            combined = concat(contours);
            //combined = first;
        }

        //Track corners of combined contour
        Point[] corners;
        if ((corners = getCorners(combined)) != null) {
            Scalar[] colors = {new Scalar(255, 0, 0), new Scalar(0, 255, 0),
                    new Scalar(0, 0, 255), new Scalar(0, 0, 0)};

            //corners = new Point[] {new Point(250, 750), new Point(1275, 750), new Point(250, 250), new Point(1275, 250)};

            for (int i = 0; i < 4; i++) {
                Imgproc.circle(input, corners[i], 15, colors[i], -1);
            }
            double yaw = getAnglePnP(corners, input);

            Imgproc.putText(input,
                    String.format(Locale.getDefault(), "%.2f",
                    yaw), new Point(0, 700),
                    Core.FONT_HERSHEY_SIMPLEX, 2, new Scalar(0, 255, 0), 3);
        }

        Imgproc.drawContours(input, contours, -1, new Scalar(0, 255, 0), 2);
        return input;
    }

    public MatOfPoint concat(List<MatOfPoint> contours) {
        int sizeThreshold = 50;

        List<Point> points = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            if (Imgproc.contourArea(contour) < sizeThreshold) continue;
            List<Point> contourList = contour.toList();
            points.addAll(contourList);
        }
        MatOfPoint retval = new MatOfPoint();
        retval.fromList(points);
        return retval;
    }

    public Point[] getCorners(MatOfPoint contour) {
        if (contour == null) {
            Log.d(TAG, "Contour is null");
            return null;
        }
        Point[] corners = new Point[4];
        Point[] array = contour.toArray();
        if (array.length == 0) {
            Log.d(TAG, "Empty array");
            return null;
        }
        Arrays.sort(array, new Comparator<Point>() {
            @Override
            public int compare(Point o1, Point o2) {
                return (int)((o1.y - o1.x) - (o2.y - o2.x));
            }
        });
        corners[3] = array[0];
        corners[0] = array[array.length - 1];
        Arrays.sort(array, new Comparator<Point>() {
            @Override
            public int compare(Point o1, Point o2) {
                return (int)((o1.y + o1.x) - (o2.y + o2.x));
            }
        });
        corners[2] = array[0];
        corners[1] = array[array.length - 1];

        return corners;
    }

    public double getAnglePnP(Point[] src, Mat input) {
        double dist = 0, scalar = 100, x = 10.25 * scalar, y = 5 * scalar, z = dist - 2 * scalar;

        //src = new Point[]{new Point(0, 500), new Point(1025, 500), new Point(0, 0), new Point(1025, 0)};
        Scalar[] colors = {new Scalar(255, 0, 0), new Scalar(0, 255, 0),
                new Scalar(0, 0, 255), new Scalar(0, 0, 0)};
        MatOfPoint2f dstPoints = new MatOfPoint2f(src[0], src[1], src[2], src[3]);
        MatOfPoint3f srcPoints = new MatOfPoint3f(new Point3(0, y, dist), new Point3(x, y, dist),
                new Point3(0, 0, dist), new Point3(x, 0, dist));
        MatOfDouble rvecs = new MatOfDouble(), tvecs = new MatOfDouble();
        Calib3d.solvePnP(srcPoints, dstPoints, intrinsicMatrix, distCoeffs, rvecs, tvecs);
        MatOfPoint3f newPoints = new MatOfPoint3f(new Point3(0, 0, 0), new Point3(x, 0, 0), new Point3(x, y, 0), new Point3(0, y, 0),
                                                    new Point3(0, 0, z), new Point3(x, 0, z), new Point3(x, y, z), new Point3(0, y, z));
        MatOfPoint2f result = new MatOfPoint2f();
        Calib3d.projectPoints(newPoints, rvecs, tvecs, intrinsicMatrix, distCoeffs, result);
        Point[] arr = result.toArray();
        Scalar red = new Scalar(255, 0, 0);
        for (int i = 0; i < 4; i++) {
            Imgproc.line(input, arr[i], arr[(i+1) % 4], red, 5);
            Imgproc.line(input, arr[i], arr[i+4], red, 5);
            Imgproc.line(input, arr[i+4], arr[(i+1) % 4+4], new Scalar(0, 0, 255), 5);
        }
        /*Point3[] newSrc = new Point3[4];
        for (int i = 0; i < 4; i++) newSrc[i] = new Point3(src[i].x, src[i].y, 500);
        srcPoints = new MatOfPoint3f();
        srcPoints.fromArray(newSrc);
        dstPoints = new MatOfPoint2f(new Point(0, y), new Point(x, y), new Point(0, 0), new Point(x, 0));
        Calib3d.solvePnP(srcPoints, dstPoints, intrinsicMatrix, distCoeffs, rvecs, tvecs);*/
        double[] angles = rvecs.toArray();
        for (int i = 0; i < 3; i++) {
            angles[i] = Math.toDegrees(angles[i]);
        }
        return angles[1];
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    public void launchSetThresholdActivity(MenuItem item) {
        Intent intent = new Intent(this, SetThresholdActivity.class);
        startActivity(intent);
    }

    public static Mat getImage() { return imageRGB; }

}
