package com.example.student.scout;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MessageActivity extends AppCompatActivity {

    TextView tv_log;
    Button btn_send, btn_reset;
    EditText et_msg;
    BluetoothSocket connSocket;
    boolean bRead = true;
    Handler writeHandler;

    MsgThread msgThread;
    WriteThread writeThread;
    ReadThread readThread;
    StringBuffer sb;

    final int SEND_MESSAGE = 100;
    final int RECEIVED_MESSAGE = 200;

    private MyDBHelper myDBHelper;
    Map<String, String> map;
    long start, current;
    int lastCount, currentCount, sum, avg;
    String state;

    private MyService myService;
    private boolean mBound = false;

    ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myService = ((MyService.LocalBinder)service).getService(); // 서비스 객체 얻기
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MyService.class);
        bindService(intent, mConn, Context.BIND_AUTO_CREATE); // 서비스와 연결하기
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mBound){
            unbindService(mConn);   // 서비스와 연결 해제
            mBound = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        myDBHelper = new MyDBHelper(this);
        map = new HashMap<>();

        start = System.currentTimeMillis();

        tv_log = (TextView)findViewById(R.id.tv_log);
        btn_send = (Button)findViewById(R.id.btn_send);
        btn_reset = (Button) findViewById(R.id.btn_result);
        et_msg = (EditText)findViewById(R.id.et_msg);

        // 서버와 클라이언트가 연결된 bluetoothSocket 객체
        connSocket = BluetoothActivity.target1Socket;

        // 전달할 메시지를 저장할 Stringbuffer 객체
        sb = new StringBuffer();

        // 메시지 송수신을 위한 처리를 담당하는 스레드 생성 및 기동
        msgThread = new MsgThread();
        msgThread.start();

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound){
                    myService.result();
                    Intent intent = getIntent();
                    String result = intent.getStringExtra("result");
                    tv_log.setText(result);
                }
            }
        });

        btn_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBound){
                    myService.reset();
                }
            }
        });

    }

    class MsgThread extends Thread {

        @Override
        public void run() {
            try {
                // 기존에 readThread 가 있다면 중지한다.
                if(readThread != null) {
                    readThread.interrupt();
                }
                // 데이터를 수신하기 위한 readThread 생성
                readThread = new ReadThread(connSocket);
                readThread.start();

                // 기존에 writeThread 가 있다면 중지한다.
                if(writeThread != null) {
                    // writeThread 내부에서 looper를 활용하고 있으므로
                    // looper를 종료해 주어야 한다.
                    writeHandler.getLooper().quit();
                }

                // 데이터를 송신하기 위한 writeThread 생성
                writeThread = new WriteThread(connSocket);
                writeThread.start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 송수신된 메시지를 화면에 TextView에 출력하기 위한 Handler
    Handler msgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case SEND_MESSAGE:
                    try{
                        if(Integer.parseInt((String)msg.obj)>=10 ){
                            sb.append("CO: "+(String)msg.obj+"\n");
                            tv_log.setText(sb);

                            map.put("time", DateFormat.getDateTimeInstance().format(new Date()));
                            map.put("value", (String)msg.obj);
                            insert();
                            Toast.makeText(getApplicationContext(), map.get("time")+", "+map.get("value"), Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                case RECEIVED_MESSAGE:  // DB 데이터 확인
                    sb.append("you > " + (String)msg.obj);
                    tv_log.setText(sb);
                    String result = selectAll();
                    tv_log.setText(result);
                    analyze();
                    break;
            }
        }
    };

    // 작성한 메시지를 전송하는 스레드, 기기 외부와 통신해야 하므로 Thread로 구성한다.
    class WriteThread extends Thread {
        BluetoothSocket socket;
        OutputStream os = null;

        public WriteThread(BluetoothSocket socket) {
            // 통신을 위한 bluetoothSocket 객체를 받는다.
            this.socket = socket;
            try {
                // bluetootsocket객체에서 OutputStream을 생성한다.
                os = socket.getOutputStream();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Looper.prepare();
            // 메시지를 받으면, 처리하는 핸들러
            writeHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    try {
                        // 주어진 데이터를 OutputStream에 전달하여 상대측에 송신한다.
                        os.write(((String)msg.obj).getBytes());
                        os.flush();

                        // 전송한 데이터를 MessageActivity안의 TextView에 출력하기 위해 메시지를 전달한다.
                        Message msg_to_acti = new Message();
                        msg_to_acti.what = RECEIVED_MESSAGE;
                        msg_to_acti.obj = msg.obj;
                        msgHandler.sendMessage(msg_to_acti);
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeHandler.getLooper().quit();
                    }
                }
            };

            Looper.loop();
        }
    }

    // 상대로 부터 메시지를 전달하기 위해 동작하는 스레드
    class ReadThread extends Thread {
        BluetoothSocket socket;
        BufferedInputStream bis = null;

        public ReadThread(BluetoothSocket socket) {
            this.socket = socket;
            try {
                // bluetoothSocket에서 bufferedInputStream을 생성한다.
                bis = new BufferedInputStream(
                        socket.getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted() && bRead) {
                try {
                    // 데이터를 임시로 저장할 버퍼를 만든다.
                    byte[] buf = new byte[1024];
                    // 버퍼에 데이터를 읽어온다.
                    int bytes = bis.read(buf);
                    // 읽어온 문자열 형태로 저장한다.
                    String read_str = new String(buf, 0, bytes);

                    // 읽어온 MessageActivity 안의 listview에 적용하기 위해 핸들러에 메시지를 전달한다
                    Message msg = new Message();
                    msg.what = SEND_MESSAGE;
                    msg.obj = read_str;
                    msgHandler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    bRead = false;
                }
            }
        }

    }

    // DB

    public class MyDBHelper extends SQLiteOpenHelper{
        public MyDBHelper(Context context){
            super(context, "groupCO", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        //    db.execSQL("CREATE TABLE groupCO (sTime TEXT NOT NULL PRIMARY KEY, sValue INTEGER NOT NULL);");
            db.execSQL("CREATE TABLE groupCO ( _ID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, sTime TEXT NOT NULL, sValue INTEGER NOT NULL);");

        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS groupCO;");
            onCreate(db);
        }
    }

    public void insert() {
        SQLiteDatabase db;
        ContentValues values;

        db = myDBHelper.getWritableDatabase();
        values = new ContentValues();
        values.put("sTime", map.get("time"));
        values.put("sValue", map.get("value"));
        db.insert("groupCO", null, values);
        db.close();
    }

    public void analyze(){
        SQLiteDatabase db = myDBHelper.getWritableDatabase();

        state="";
        sum = 0;
        avg = 0;
        Cursor cur;

        // 1분 주기로 결과값 출력
        current = System.currentTimeMillis();
    /*    if(current-start>=60000){
            start = current;
            // 메인으로 intent를 통해 데이터 전달
            Intent intent  = new Intent(getApplicationContext(), MainActivity.class);
            intent.putExtra("Value", avg);
            intent.putExtra("State", state);
            startActivity(intent);

            cur = db.rawQuery("select count (*) from groupCO;", null);
            while(cur.moveToNext()){
                lastCount = cur.getInt(0); // 현재 DB의 데이터 개수 (_ID)
            }
        }*/

        cur = db.rawQuery("select count (*) from groupCO;", null);
        while(cur.moveToNext()){
            lastCount = cur.getInt(0); // 현재 DB의 데이터 개수 (_ID)
        }

        /*cur = db.rawQuery("select count (*) from groupCO;", null);
        while(cur.moveToNext()){
            currentCount = cur.getInt(0);
        }*/
        int duration = lastCount-11;

        cur = db.rawQuery("SELECT sValue from groupCO where _ID >"+duration+";", null);

     //   cur = db.rawQuery("SELECT sValue from groupCO where _ID>0;", null);

        ArrayList<Integer> values = new ArrayList<>(); // 전체 데이터 담은 리스트
        while(cur.moveToNext()){
            values.add(cur.getInt(0));
        }

        ArrayList<Integer> valueA = new ArrayList<>(); // 50ppm 미만 : SAFE 좋음
        ArrayList<Integer> valueB = new ArrayList<>(); // 50ppm 이상 200ppm 미만 : 보통
        ArrayList<Integer> valueC = new ArrayList<>(); // 200ppm 이상 400ppm 미만 : 주의
        ArrayList<Integer> valueD = new ArrayList<>(); // 400ppm 이상 800ppm 미만 : 경고
        ArrayList<Integer> valueE = new ArrayList<>(); // 800ppm 이상 : DANGER 위험

        String[] stateArr = {"SAFE", "NORMAL", "CAUTION", "WARNING", "DANGER" };

        ArrayList<ArrayList<Integer>> countGroupList = new ArrayList<>();
        countGroupList.add(valueA);
        countGroupList.add(valueB);
        countGroupList.add(valueC);
        countGroupList.add(valueD);
        countGroupList.add(valueE);



        // 구간별 데이터 할당
        for(int i=0; i<values.size(); i++){
            if (values.get(i)<50){
                valueA.add(values.get(i));
            } else if (values.get(i)<200){
                valueB.add(values.get(i));
            } else if (values.get(i)<400){
                valueC.add(values.get(i));
            } else if (values.get(i)<800){
                valueD.add(values.get(i));
            } else if (values.get(i)>=800){
                valueE.add(values.get(i));
            }
        }

        int countA = valueA.size();
        int countB = valueB.size();
        int countC = valueC.size();
        int countD = valueD.size();
        int countE = valueE.size();

        int[] countArr = {countA, countB, countC, countD, countE};

        // countArr를 merge sorting하여 가장 큰 count값 찾기 (가장 많은 데이터가 속한 그룹)
        countArr = merge_sort(countArr);

        int resultCount = countArr[countArr.length-1];
        ArrayList<Integer> resultGroup = new ArrayList<>();

        // 가장 큰 count값을 가진 그룹 찾기 (가장 많은 데이터가 속한 그룹)
        for(int i=0; i<countGroupList.size(); i++){
            if(resultCount == countGroupList.get(i).size()){
            //    temp = countArr[i];
                resultGroup = countGroupList.get(i);
                state = stateArr[i];
            }
        }

        // 가장 많은 데이터가 속한 그룹의 평균값 계산
        if(resultGroup.size()>0){
            for(int i=0; i<resultGroup.size(); i++){
                sum += resultGroup.get(i);
            }
            avg = sum/resultGroup.size();
        }

        Toast.makeText(getApplicationContext(), "start: "+start +", current: " +current+", avg: "+avg+", state: "+state+", lastCount: "+lastCount, Toast.LENGTH_LONG).show();

        Intent intent  = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("Value", avg);
        intent.putExtra("State", state);
        startActivity(intent);

    }

    public int[] merge_sort (int[] arr){
        int n= arr.length;
        if(n==1) return arr;

        int[] arr_temp1 = new int[n/2];
        int[] arr_temp2 = new int[n-n/2];

        for (int i=0; i<n/2; i++){
            arr_temp1[i] = arr[i];
        }
        for(int i=0; i<n-n/2; i++){
            arr_temp2[i] = arr[i+n/2];
        }
        merge_sort(arr_temp1);
        merge_sort(arr_temp2);

        merge(arr_temp1, arr_temp2, arr);
    //    Toast.makeText(MessageActivity.this, arr[arr.length-1], Toast.LENGTH_LONG).show();

        return arr;
    }


    public static void merge (int[] arrA, int[] arrB, int[] arrC) {
        int iA = 0;
        int iB = 0;
        int iC = 0;

        while (iA < arrA.length) {
            if (iB < arrB.length) {
                if ( arrA[iA] < arrB[iB]) {
                    arrC[iC] = arrA[iA];
                    iA++;
                } else {
                    arrC[iC] = arrB[iB];
                    iB++;
                }
                iC++;
            } else {
                while (iA < arrA.length) {
                    arrC[iC] = arrA[iA];
                    iA++;
                    iC++;
                }
            }
        }

        while (iB < arrB.length) {
            arrC[iC] = arrB[iB];
            iB++;
            iC++;
        }
    }


    public String selectAll(){
        SQLiteDatabase db;
        ContentValues values;
        String[] projection = {"_ID", "sTime", "sValue"};
        Cursor cur;
        String result = "";

        db = myDBHelper.getReadableDatabase();
        cur = db.rawQuery("SELECT * FROM groupCO;", null);
        while(cur.moveToNext()){
            result += cur.getString(0)+", "+cur.getString(1)+", "+cur.getString(2)+"\n";
        }
        return result;
    }


}
