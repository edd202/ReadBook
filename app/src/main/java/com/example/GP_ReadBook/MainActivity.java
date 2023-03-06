package com.example.GP_ReadBook;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Message;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final int PERMISSION_CODE = 2;

    public static final int AUTO_START = 1;
    public static final int AUTO_START_CANCEL = 2;
    public static final int ORC_PROCESS = 3;

    private CountDownTimer countDownTimer;

    private TessBaseAPI tessApi; //Tess API reference
    private ProgressCircleDialog progressCircle = null; // 원형 프로그레스바
    private TextToSpeech ttsOrc; // TTS 변수 선언

    private String mDataPath = ""; //언어데이터가 있는 경로
    private final String[] mLanguageList = {"eng"}; // 언어
    // View
    private Context context;
    private TextView ocrTextView; // 결과 변환 텍스트
    private ImageView ivImage; // 찍은 사진
    public Bitmap bitmap; //사용되는 이미지
    private FrameLayout container;
    private Button btnCamera;
    private EditText etTimer;

    private Camera2BasicFragment fragment;

    private boolean isAutoStart = false;
    private boolean ProgressFlag = false; // 프로그레스바 상태 플래그
    private boolean isOCRComplete = false;

    private ArrayList<String> prevOcrList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_land);

        context = this;

        fragment = Camera2BasicFragment.newInstance();
        container = findViewById(R.id.container);
        etTimer = findViewById(R.id.et_timer);

        btnCamera = findViewById(R.id.btn_camera);
        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clickCameraButton();
            }
        });

        ivImage = findViewById(R.id.iv_image);
        ocrTextView = findViewById(R.id.tvOcr);
        ocrTextView.setText("인식할 문자를 찍어주세요.");

        progressCircle = new ProgressCircleDialog(this);

        ttsOrc = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int state) {
                if (state == TextToSpeech.SUCCESS) {
                    ttsOrc.setLanguage(Locale.ENGLISH);

                    ttsOrc.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onDone(String utteranceId) {
                            weakHandler.sendEmptyMessage(AUTO_START);
                            Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
                            vibrator.vibrate(1000);
                            Uri noti= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                            Ringtone ringtone=RingtoneManager.getRingtone(getApplicationContext(),noti);
                            ringtone.play();
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.d("shet", "status : " + utteranceId);
                        }

                        @Override
                        public void onStart(String utteranceId) {
                        }
                    });
                }
                else {
                    Log.d("shet", "status : " + state);
                }
            }
        });

        PermissionCheck();
        Tesseract();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_CODE:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, fragment)
                        .commit();
                Toast.makeText(this, "권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    public void setBitmapImage(final Bitmap cameraBitmap) {


        ocrTextView.setText("잠시만 기다려주세요.결과가 여기에 나타납니다."); //인식된텍스트 표시

        bitmap = cameraBitmap;

        OCRThread ocrThread = new OCRThread(bitmap);
        ocrThread.setDaemon(true);
        ocrThread.start();
    }

    public void PermissionCheck() {
        /**
         * 6.0 마시멜로우 이상일 경우에는 권한 체크후 권한을 요청한다.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                // 권한 없음
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_CODE);
            } else {
                // 권한 있음
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, fragment)
                        .commit();
            }
        }
    }

    public void Tesseract() {
        //언어파일 경로
        mDataPath = getFilesDir() + "/tesseract/";

        //트레이닝데이터가 카피되어 있는지 체크
        String lang = "";
        for (String Language : mLanguageList) {
            checkFile(new File(mDataPath + "tessdata/"), Language);
            lang += Language + "+";
        }
        tessApi = new TessBaseAPI();
        tessApi.init(mDataPath, lang, TessBaseAPI.OEM_TESSERACT_ONLY);
    }

    /**
     * 언어 데이터 파일을 가져온다
     *
     * @param Language
     */
    private void copyFiles(String Language) {
        try {
            String filepath = mDataPath + "/tessdata/" + Language + ".traineddata";
            AssetManager assetManager = getAssets();
            InputStream instream = assetManager.open("tessdata/" + Language + ".traineddata");
            OutputStream outstream = new FileOutputStream(filepath);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, read);
            }
            outstream.flush();
            outstream.close();
            instream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 언어 데이터 파일이 있는지 확인한다.
     *
     * @param dir
     * @param Language
     */
    private void checkFile(File dir, String Language) {
        //디렉토리가 없으면 디렉토리를 만들고 그후에 파일을 카피
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles(Language);
        }
        //디렉토리가 있지만 파일이 없으면 파일카피 진행
        if (dir.exists()) {
            String datafilepath = mDataPath + "tessdata/" + Language + ".traineddata";
            File datafile = new File(datafilepath);
            if (!datafile.exists()) {
                copyFiles(Language);
            }
        }
    }

    /**
     * OCR 스레드 프로세스
     */
    public class OCRThread extends Thread {
        private Bitmap image;

        OCRThread(Bitmap image) {
            this.image = image;
            if (!ProgressFlag)
                progressCircle = ProgressCircleDialog.show(context, "", "", true);
            ProgressFlag = true;
        }

        @Override
        public void run() {
            super.run();
            // 사진의 글자를 인식해서 옮긴다
            String OCRresult = null;

            Bitmap gray = ColorToGrayscale(image);
            Bitmap convertBitmap = Threshold(gray).copy(Bitmap.Config.ARGB_8888, true);

            tessApi.setImage(convertBitmap);

            OCRresult = StringReplace(tessApi.getUTF8Text());

            Message message = Message.obtain();
            message.what = ORC_PROCESS;
            message.obj = OCRresult;
            weakHandler.sendMessage(message);
        }
    }

    /**
     * 특수문자를 제거한다
     *
     * @param str
     * @return
     */
    public static String StringReplace(String str) {
        String match = "[^\uAC00-\uD7A3xfe0-9a-zA-Z\\s]";
        str = str.replaceAll(match, "");
        return str;
    }

    /**
     * 이미지를 흑백으로 변환한다.
     *
     * @param bm
     * @return
     */
    public static Bitmap ColorToGrayscale(Bitmap bm) {
        Bitmap grayScale = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.RGB_565);

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(cm));

        new Canvas(grayScale).drawBitmap(bm, 0, 0, p);

        return grayScale;
    }

    /**
     * 이미지를 이진화 시킨다
     *
     * @param bm
     * @param threshold
     * @return
     */
    public static Bitmap GrayscaleToBin(Bitmap bm, int threshold) {
        Bitmap bin = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.RGB_565);

        ColorMatrix cm = new ColorMatrix(new float[]{
                85.f, 85.f, 85.f, 0.f, -255.f * threshold,
                85.f, 85.f, 85.f, 0.f, -255.f * threshold,
                85.f, 85.f, 85.f, 0.f, -255.f * threshold,
                0f, 0f, 0f, 1f, 0f
        });

        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(cm));

        new Canvas(bin).drawBitmap(bm, 0, 0, p);

        return bin;
    }

    public static Bitmap Threshold(Bitmap bm) {

        // Get Histogram
        int[] histogram = new int[256];
        for (int i = 0; i < histogram.length; i++) histogram[i] = 0;

        for (int i = 0; i < bm.getWidth(); i++) {
            for (int j = 0; j < bm.getHeight(); j++) {
                histogram[(bm.getPixel(i, j) & 0xFF0000) >> 16]++;
            }
        }

        int total = bm.getHeight() * bm.getWidth();

        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];

        float sumB = 0;
        int wB = 0;
        int wF = 0;

        float varMax = 0;
        int threshold = 0;

        for (int i = 0; i < 256; i++) {
            wB += histogram[i];
            if (wB == 0) continue;
            wF = total - wB;

            if (wF == 0) break;

            sumB += (float) (i * histogram[i]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);

            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = i;
            }
        }

        return GrayscaleToBin(bm, threshold);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (ttsOrc != null) {
            ttsOrc.stop();
            ttsOrc.shutdown();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {

            clickCameraButton();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void onStartCamera() {

        isOCRComplete = false;
        fragment.onCancelEvent();
        ivImage.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);
        btnCamera.setVisibility(View.VISIBLE);
//        ocrTextView.setText("인식할 문자를 찍어주세요.");
    }

    private void clickCameraButton() {
        if (isAutoStart) {
            btnCamera.setText("카메라 인식");
            etTimer.setEnabled(true);
            isAutoStart = false;
            weakHandler.sendEmptyMessage(AUTO_START_CANCEL);
        }
        else {
            int timer = Integer.parseInt(etTimer.getText().toString());

            if (0 < timer && 11 > timer) {
                btnCamera.setText("중단");
                etTimer.setEnabled(false);
                isAutoStart = true;
                fragment.takePicture();
            }
            else {
                Toast.makeText(context, "1~10초 까지 설정해주세요.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final String newLine = System.getProperty("line.separator") + System.getProperty("line.separator");
    private WeakHandler<MainActivity> weakHandler = new WeakHandler<MainActivity>(this) {
        @Override
        protected void weakHandleMessage(MainActivity ref, Message msg) {

            if (AUTO_START == msg.what) {
                isAutoStart = true;
                onStartCamera();

                int timer = Integer.parseInt(etTimer.getText().toString()) * 1000;
                countDownTimer = new CountDownTimer(timer, timer) {
                    @Override
                    public void onTick(long l) { }

                    @Override
                    public void onFinish() {

                        if (isAutoStart)
                            fragment.takePicture();
                        else {
                            ttsOrc.stop();
                            onStartCamera();
                            countDownTimer.cancel();
                        }
                    }
                };
                countDownTimer.start();

            }
            else if (AUTO_START_CANCEL == msg.what) {
                isAutoStart = false;
                ttsOrc.stop();
                onStartCamera();
                countDownTimer.cancel();
            }
            else if (ORC_PROCESS == msg.what) {

                int orgSize = 0;
                int newSize = 0;

                String ocrText = String.valueOf(msg.obj);
                orgSize = ocrText.length();

                for(int i = 0; i < prevOcrList.size(); i++) {
                    ocrText = ocrText.replace(prevOcrList.get(i) + newLine, "");
                    ocrText = ocrText.replace(prevOcrList.get(i), "");
                }

                newSize = ocrText.length();

                if (TextUtils.isEmpty(ocrText)) {
                    //ocr 결과가 비어있으면 리스트만 비운다.
                    ocrText = "";
                    prevOcrList.clear();
                }
                else {
                    //하나라도 있으면 리스트를 새롭게 채운다.
                    String[] ocrArray = ocrText.split(newLine);
                    prevOcrList = new ArrayList<>(Arrays.asList(ocrArray));
                }

                TextView OCRTextView = findViewById(R.id.tvOcr);
                OCRTextView.setText(ocrText); //텍스트 변경

                // 원형 프로그레스바 종료
                if (progressCircle.isShowing() && progressCircle != null)
                    progressCircle.dismiss();

                ProgressFlag = false;
                Toast.makeText(context, "문자인식이 완료되었습니다.", Toast.LENGTH_SHORT).show();

                String utteranceId = this.hashCode() + "";

                String Duplicated="";
                if (orgSize != newSize)
                    Duplicated = "Duplicates Deleted\n\n\n\n\n\n\n";

                ttsOrc.speak(Duplicated + ocrText, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
                Duplicated = "";
                btnCamera.setVisibility(View.VISIBLE);
                isOCRComplete = true;
            }
        }
    };
}
