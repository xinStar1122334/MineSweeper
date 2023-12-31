package com.wcm.minesweeper;

import androidx.annotation.LongDef;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements CustomDialog.DialogClickListener{

    private int width = 20;//高度
    private int height = 10; //宽度
    private int mineCnt = 10;//设置地雷数量
    private LinearLayout layout_item; //每一行的布局
    private ImageView img; //创建的每一块的图片
    private ImageView img_item;//监听事件里的图片（点击的）
    private Mine[][] mines= new Mine[1000][1000];//创建一个Mine类，用于确定当前位置是否有地雷以及设置地雷
    private int ID = 0;
    private boolean firstClicked = true;
    private boolean isTimerRunning = false; //是否开始计时
    private int flagCnt = 0;
    private boolean isEnd = false;
    private long startTime;//开始计时事件
    private long pauseTime = 0;//暂停前的已经过的时间
    private Timer timerTips; //显示提示
    private Timer timer;//定时器
    private Timer timerJoker;
    private int faceImg = 0; //设置face的图片，初始是0
    private long gameDuration = 0; //设置游戏持续时间
    private CountDownTimer countDownTimer; //设置倒计时，用于在游戏结束后暂停一段时间后跳转到游戏结束界面
    private int tipCnt = 0;//随机一次的次数
    private int screenWidth; //得到屏幕宽度
    private int screenHeight; //得到屏幕高度
    private int param; //设置到布局中的地雷宽度

    boolean once = false;//设置是否点击了随机一个，保证随机一个提示一次完成后才能继续（避免连续多次点击出现问题）
    private boolean tipsOnce = false; //设置获取提示一次完成后才能继续
    private boolean timeClicked = false;//延迟跳转结束后才能继续


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        minesInit();
        startTimer();//计时器开始计时
        initAboveNum();//初始化时间和地雷显示
        initNewMine();//定义Mine
        initMine(); //初始化地雷位置
        initBlank();//初始化白板位置
        initLayout();//初始化布局
        initTips();//初始化提示按钮（点击显示提示）
    }

    @Override
    public void onContinueClicked() {
        // 继续游戏的逻辑
        startPauseTimer();
        Toast.makeText(this, "继续游戏", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRestartClicked() {
        // 重新开始游戏的逻辑
        timer.cancel();
        recreate();
        minesInit();
    }

    @Override
    public void onExitClicked() {
        // 返回主菜单
        navigateToMainMenu();
    }
    int layoutHeight = 0;
    //获取屏幕的宽高信息
    public void getScreenLength(){
        // 获取屏幕的度量信息
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        // 屏幕的实际像素宽度和高度
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
        //设置地雷的大小属性
        param = Math.min((screenWidth-60) / height,(int)(0.75 * screenHeight/ width));
    }

    public void minesInit(){
        Intent intent = getIntent();
        mineCnt = intent.getIntExtra("minesCount",10);
        width = intent.getIntExtra("minesWidth",9);
        height = intent.getIntExtra("minesHeight",9);

    }

    // 跳转到主菜单界面
    public void navigateToMainMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setIcon(R.drawable.icon);
        builder.setTitle(("提示"));
        builder.setMessage("确认退出吗？");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent(MainActivity.this,Home.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION); //不显示切换动画
                startActivity(intent);
                finish(); // 关闭当前界面
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startPauseTimer(); // 继续计时
            }
        });
        builder.setCancelable(false);//显示对话框后点一下对话框不消失，一直存在
        builder.show();
    }
    //延迟跳转到结束页面
    public void delayJumpToFinish(int t){
        if(!timeClicked){
            timeClicked = true;
            Timer time = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    timeClicked = true;
                    cancelTimer(time);
                    navigateToGameFinish();
                }
            };
            time.schedule(task, t);//延迟t秒后执行 run 中的内容
        }
    }
    // 取消定时任务
    public void cancelTimer(Timer timer) {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }
    // 跳转到游戏结束界面
    public void navigateToGameFinish(){
        Intent intent = new Intent(MainActivity.this,GameFinish.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION); //不显示切换动画
        intent.putExtra("game_duration",gameDuration);
        intent.putExtra("game_status",isWin());
        intent.putExtra("mines_cnt",mineCnt);
        intent.putExtra("flag_cnt",flagCnt);
        intent.putExtra("width",width);
        intent.putExtra("height",height);
        startActivity(intent);
        finish(); // 关闭当前界面
    }


    public void initNewMine(){
        for(int i = 0; i < width; i++){
            for(int j = 0; j < height; j++){
                mines[i][j] = new Mine();
            }
        }
    }
    //初始化提示按钮
    public void initTips(){
        getMinesTips();//获取提示
        showBlanks(); //白块全出
        tipsRandom(); //随机一个
    }

    //白块全出
    public void showBlanks(){
        Button show_blanks = findViewById(R.id.show_blank);
        show_blanks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for(int i = 0; i < width; i++){
                    for(int j = 0; j < height; j++){
                        if(mines[i][j].getBlank()){
                            blankMine(i,j);
                        }
                    }
                }
            }
        });
    }

    //获取提示
    public void getMinesTips(){
        Button button_tips = findViewById(R.id.tips);
        button_tips.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!tipsOnce){//防止多次点击出现错误，只有一次点击执行结束后才会进行下一次点击
                    tipsOnce = true;
                    getTips();
                    // 获得提示后隔1秒隐藏提示
                    timerTips = new Timer();
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            //计时器执行完之后的操作
                            hideTips();
                            tipsOnce = false;
                            cancelTimer(timerTips);
                        }
                    };
                    timerTips.schedule(task, 1000); //每隔1000ms更新一下
                }
            }
        });
    }
    //随机一个
    public void tipsRandom(){
        Button button_tipsRandom = findViewById(R.id.tips_random);
        button_tipsRandom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(tipCnt >= 3){
                    Toast.makeText(MainActivity.this, "剩余次数：0", Toast.LENGTH_SHORT).show();
                    return;
                }
                Random random = new Random();
                int x,y;
                tipCnt++;
                Toast.makeText(MainActivity.this, "剩余次数：" + (3-tipCnt), Toast.LENGTH_SHORT).show();
                while (!once){
                    x = random.nextInt(width);
                    y = random.nextInt(height);
                    //如果随机的这个位置没有被点击过，也没有插上旗，也不是地雷
                    if(!mines[x][y].getClicked() && !mines[x][y].getFlag() && !mines[x][y].getMine()){
                        mines[x][y].setClicked();
                        ImageView img = findViewById(mines[x][y].getID());
                        int n = mineNum(x,y);//该位置周围的地雷数量
                        if(n == 0){ //周围一个地雷都没有
                            blankMine(x,y);
                        }
                        else { //周围有地雷，显示地雷数目的方块
                            mineNumImg(n,img);
                        }
                        //如果胜利，执行胜利动画、震动、跳转
                        if(isWin()){
                            once = true;
                            faceImg = 3;
                            gameDuration = SystemClock.elapsedRealtime() - startTime + pauseTime;
                            //如果结束了，则停止计时
                            timer.cancel();
                            setFaceImg();
                            animateFireworks(view);
                            // 设置震动
                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                // 振动 100 毫秒，设置振动模式
                                VibrationEffect vibrationEffect = VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE);
                                vibrator.vibrate(vibrationEffect);
                            } else {
                                // 在旧版本上使用过时的方法
                                vibrator.vibrate(100);
                            }
                            delayJumpToFinish(2000);
                        }
                        break;
                    }
                }
            }
        });

    }
    public void initLayout(){
        getScreenLength();
        //设置布局属性
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(param, param);
        LinearLayout layout = (LinearLayout) findViewById(R.id.oriLayout);//获取最开始的整体布局
        for(int i = 0; i < width; i++){
            //为每一行创建一个布局
            layout_item = new LinearLayout(this);
            layout_item.setOrientation(LinearLayout.HORIZONTAL); //布局方向是水平的，接下来为每一行添加图片
            for(int j = 0; j < height; j++){
                img = new ImageView(this);
                //设置渐变
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(img, "alpha", 0f, 1f);
                fadeIn.setDuration(800); // 800毫秒的渐变时间
                img.setImageResource(R.drawable.blank);
                fadeIn.start();
                img.setId(ID);
                mines[i][j].setID(ID);
                layout_item.addView(img,lp);
                //长按插旗、取消插旗
                img.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        //如果已经结束，则长按事件不再生效
                        if(isEnd){
                            return true;
                        }
                        img_item = (ImageView) view;
                        int tmp = img_item.getId();
                        int x = tmp / height;
                        int y = tmp % height;
                        //判断是否已经点击过了，如果点击过了，则不能插旗
                        if( !mines[x][y].getClicked()){
                            if(mines[x][y].getFlag()){
                                img_item.setImageResource(R.drawable.blank); //移除小旗
                                mines[x][y].setFlag(false);
                            }
                            else {
                                img_item.setImageResource(R.drawable.flag); //插旗
                                mines[x][y].setFlag(true);
                                flagCnt++;
                            }
                        }
                        showNumbers();
                        return true;
                    }
                });
                img.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //游戏持续事件
                        gameDuration = SystemClock.elapsedRealtime() - startTime + pauseTime;
                        img_item = (ImageView) view;
                        int tmp = img_item.getId();
                        int x = tmp / height;
                        int y = tmp % height;
                        //如果已经结束了，则点击事件不再生效，停止计时
                        if(isEnd){
                            timer.cancel();
                            return;
                        }
                        //如果这个位置已经插旗了，则点击事件不生效
                        if(mines[x][y].getFlag()){
                            return;
                        }
                        //如果是地雷
                        if (mines[x][y].getMine()){
                            faceImg = 2;
                            showMines(view);//显示所有地雷
                            isEnd = true;
                            img_item.setImageResource(R.drawable.blood);//将踩到的地雷设为红色
                            // 设置震动
                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                // 振动 100 毫秒，设置振动模式
                                VibrationEffect vibrationEffect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);
                                vibrator.vibrate(vibrationEffect);
                            } else {
                                // 在旧版本上使用过时的方法
                                vibrator.vibrate(100);
                            }

                            delayJumpToFinish(1050);//跳转到游戏结束界面
                        }
                        else{ //如果不是地雷
                            mines[x][y].setClicked();
                            int n = mineNum(x,y);//该位置周围的地雷数量图片
                            if(n == 0){ //周围一个地雷都没有
                                blankMine(x,y);
                            }
                            else { //周围有地雷
                                mineNumImg(n,img_item);
                            }
                        }
                        if(isWin()){
                            isEnd = true;
                            faceImg = 3;
                            animateFireworks(view);
                            // 设置震动
                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                // 振动 100 毫秒，设置振动模式
                                VibrationEffect vibrationEffect = VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE);
                                vibrator.vibrate(vibrationEffect);
                            } else {
                                // 在旧版本上使用过时的方法
                                vibrator.vibrate(100);
                            }
                            delayJumpToFinish(2000);
                        }
                        setFaceImg();
                    }
                });
                ID++;
            }
            layout.addView(layout_item);
        }
    }

    //初始化地雷
    public void initMine(){
        int x;
        int y;
        Random random = new Random();
        for(int i = 0; i < mineCnt; i++){
            do{
                x = random.nextInt(width);
                y = random.nextInt(height);
                Log.d("cnt", String.valueOf(x) + " " + String.valueOf(y));
            }while (mines[x][y].getMine());//如果这个已经设置为地雷了，则重新找一组随机数
            mines[x][y].setMine(); //将这个位置设置为地雷
        }
    }
    //设置空白的标志
    public void initBlank(){
        for(int i = 0; i < width; i++){
            for(int j = 0; j < height; j++){
                if(mineNum(i,j) == 0 && !mines[i][j].getMine()){
                    mines[i][j].setBlank();
                }
            }
        }
    }

    //初始化最开始的时间和地雷数量和笑脸
    public void initAboveNum(){
        //初始化地雷数量显示
        int one = 0;
        int ten = 0;
        int hundred = 0;
        one = mineCnt % 10;
        ten = (mineCnt % 100) / 10;
        hundred = (mineCnt % 1000) / 100;
        ImageView img_one = findViewById(R.id.num_one);
        setNumImg(one,img_one);
        ImageView img_ten = findViewById(R.id.num_ten);
        setNumImg(ten,img_ten);
        ImageView img_hundred = findViewById(R.id.num_hundred);
        setNumImg(hundred,img_hundred);
        //初始化时间
        ImageView time_one = findViewById(R.id.time_one);
        setNumImg(0,time_one);
        ImageView time_ten = findViewById(R.id.time_ten);
        setNumImg(0,time_ten);
        ImageView time_hundred = findViewById(R.id.time_hundred);
        setNumImg(0,time_hundred);
        //给笑脸添加点击事件，点击后暂停游戏
        LinearLayout layout = findViewById(R.id.face);
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                timer.cancel(); //停止计时器
                pauseTime += SystemClock.elapsedRealtime() - startTime;
                showCustomDialog();
            }
        });
    }
    //点击暂停，显示自定义对话框
    public void showCustomDialog(){
        CustomDialog dialog = new CustomDialog(this, this);
        dialog.show();
        // 设置点击外部空白处不取消对话框
        dialog.setCanceledOnTouchOutside(false);
    }

    //统计周围有几颗地雷
    public int mineNum(int a,int b){
        int num = 0;
        //周围的格子关于x,y的增量坐标
        int[] dx = {-1,-1,-1,0,0,1,1,1};
        int[] dy = {-1,0,1,-1,1,-1,0,1};

        for(int i = 0; i < 8; i++){
            int x = a + dx[i];
            int y = b + dy[i];
            if(x < 0 || y < 0 || x >= width || y >= height){
                continue;
            }
            Mine m = mines[x][y];
            if(m.getMine()){
                num++;
            }
        }
        return num;
    }
    //设置周围雷的个数图片
    public void mineNumImg(int n,ImageView img){
        switch (n){
            case 0:
                img.setImageResource(R.drawable.zero);
                break;
            case 1:
                img.setImageResource(R.drawable.first);
                break;
            case 2:
                img.setImageResource(R.drawable.second);
                break;
            case 3:
                img.setImageResource(R.drawable.three);
                break;
            case 4:
                img.setImageResource(R.drawable.four);
                break;
            case 5:
                img.setImageResource(R.drawable.five);
                break;
            case 6:
                img.setImageResource(R.drawable.six);
                break;
            case 7:
                img.setImageResource(R.drawable.seven);
                break;
        }
    }

    //设置时间和个数的图片
    public void setNumImg(int n ,ImageView img){
        switch (n) {
            case 0:
                img.setImageResource(R.drawable.d0);
                break;
            case 1:
                img.setImageResource(R.drawable.d1);
                break;
            case 2:
                img.setImageResource(R.drawable.d2);
                break;
            case 3:
                img.setImageResource(R.drawable.d3);
                break;
            case 4:
                img.setImageResource(R.drawable.d4);
                break;
            case 5:
                img.setImageResource(R.drawable.d5);
                break;
            case 6:
                img.setImageResource(R.drawable.d6);
                break;
            case 7:
                img.setImageResource(R.drawable.d7);
                break;
            case 8:
                img.setImageResource(R.drawable.d8);
                break;
            case 9:
                img.setImageResource(R.drawable.d9);
                break;
        }
    }

    public void blankMine(int x,int y){
        dfs(x,y);
    }

//    判断显示周围连着的空白个数
    public void dfs(int x,int y){
            mines[x][y].setClicked();
            ImageView newImg = findViewById(mines[x][y].getID());
            newImg.setImageResource(R.drawable.zero);//显示白板图片
            //定义上下左右的 x 偏移量
            int[] dx = {-1,-1,-1,0,0,1,1,1};
            int[] dy = {-1,0,1,-1,1,-1,0,1};
            //空白块周围的地雷全部显示
            for(int u = 0; u < 8; u++){
                int ux = x + dx[u];
                int uy = y + dy[u];
                if(ux >= 0 && uy >= 0 && ux < width && uy < height&& !mines[ux][uy].getClicked()){
                    mines[ux][uy].setClicked();
                    if( mines[ux][uy].getBlank()){
                        dfs(ux,uy);//如果是白板，就继续递归，不然就显示地雷数量的图片
                    }
                    ImageView img = findViewById(mines[ux][uy].getID());
                    mineNumImg(mineNum(ux, uy),img);
                }
            }
    }
    //展示所有地雷
    public void showMines(View view) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                // 如果是地雷并且没有插旗，则将图片设置为地雷图片
                if (mines[i][j].getMine() && !mines[i][j].getFlag()) {
                    ImageView newImg = findViewById(mines[i][j].getID());
                    newImg.setImageResource(R.drawable.mine);
                    animateBoom(newImg);
                }
                // 如果不是地雷但被插旗，则显示地雷错误图片
                if (mines[i][j].getFlag() && !mines[i][j].getMine()) {
                    flagCnt--;
                    ImageView newImg = findViewById(mines[i][j].getID());
                    newImg.setImageResource(R.drawable.mine_error);
                    animateBoom(newImg);
                }
            }
        }
    }

    //添加爆炸动画
    public void animateBoom(ImageView img){
        // 获取目标 ImageView 在窗口上的位置
        int[] locationInWindow = new int[2];
        img.getLocationInWindow(locationInWindow);
        float targetX = locationInWindow[0];
        float targetY = locationInWindow[1];
        // 创建新的 LottieAnimationView 并设置动画文件
        LottieAnimationView explosionAnimationView = new LottieAnimationView(this);
        explosionAnimationView.setLayoutParams(new ViewGroup.LayoutParams(param+10, param+10));
        explosionAnimationView.setAnimation("Animation2.json"); // 替换为你的 JSON 文件的名称
        // 设置动画监听器，在动画结束时执行相应的操作
        explosionAnimationView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // 动画开始时的操作
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束时的操作
                // 在这里可以执行爆炸后的处理，比如显示游戏结束的提示
                //Toast.makeText(MainActivity.this, "你踩到了地雷！", Toast.LENGTH_SHORT).show();
                // 也可以调用结束游戏的方法
                // finishGame();
                // 移除 fireworksAnimationView
                ((ViewGroup) findViewById(android.R.id.content)).removeView(explosionAnimationView);
            }
            @Override
            public void onAnimationCancel(Animator animation) {
                // 动画取消时的操作
            }
            @Override
            public void onAnimationRepeat(Animator animation) {
                // 动画重复时的操作
            }
        });
        // 将 LottieAnimationView 添加到父布局中，位于目标 View 的位置
        ViewGroup parentView = (ViewGroup) findViewById(android.R.id.content);
        parentView.addView(explosionAnimationView);
        // 获取应用程序资源中的DisplayMetrics对象，用于获取与显示相关的信息
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        // 定义一个浮点数，表示屏幕高度的百分比。在此例中，设置为3.5%
        float screenHeightPercent = 0.035f;
        // 计算一个相对于屏幕高度的偏移量
        // 屏幕的实际像素高度乘以百分比，以得到相对于屏幕高度的偏移值
        float screenHeightOffset = displayMetrics.heightPixels * screenHeightPercent;
        // 设置 LottieAnimationView 的位置
        explosionAnimationView.setTranslationX(targetX);
        explosionAnimationView.setTranslationY(targetY-screenHeightOffset);
        explosionAnimationView.playAnimation();
    }

    //添加烟花动画
    public void animateFireworks(View view){
        // 创建一个 FrameLayout 作为容器
        ViewGroup parentView = (ViewGroup) findViewById(android.R.id.content);
        // 创建 LottieAnimationView
        LottieAnimationView fireworksAnimationView = new LottieAnimationView(this);
        parentView.addView(fireworksAnimationView);

        // 设置动画文件
        fireworksAnimationView.setAnimation("fireworks.json");
        // 播放动画
        fireworksAnimationView.playAnimation();
        // 设置动画监听器，在动画结束时执行相应的操作
        fireworksAnimationView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // 动画开始时的操作
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束时的操作
                // 移除 LottieAnimationView
                ((ViewGroup) findViewById(android.R.id.content)).removeView(fireworksAnimationView);
            }
            @Override
            public void onAnimationCancel(Animator animation) {
                // 动画取消时的操作
            }
            @Override
            public void onAnimationRepeat(Animator animation) {
                // 动画重复时的操作
            }
        });
    }

    //判断是否胜利
    public boolean isWin(){
        for(int i = 0; i < width; i++){
            for(int j = 0; j < height; j++){
                //如果这个方块没有被点击过但不是地雷，说明没赢
                if(!mines[i][j].getClicked()){
                    if(!mines[i][j].getMine()){
                        return false;
                    }
                }
            }
        }
        return true;
    }

    //设置剩余地雷数量的显示
    public void showNumbers(){
        int numSet = 0;
        int nums = 0;
        int one = 0;
        int ten = 0;
        int hundred = 0;
        //显示的地雷数量根据插旗的数量显示
        for(int i = 0; i < width; i++){
            for(int j = 0; j < height; j++){
                if(mines[i][j].getFlag()){
                    numSet++;
                }
            }
        }
        nums = mineCnt - numSet;
        one = nums % 10;
        ten = (nums % 100) / 10;
        hundred = (nums % 1000) / 100;
        ImageView img_one = findViewById(R.id.num_one);
        setNumImg(one,img_one);
        ImageView img_ten = findViewById(R.id.num_ten);
        setNumImg(ten,img_ten);
        ImageView img_hundred = findViewById(R.id.num_hundred);
        setNumImg(hundred,img_hundred);
    }
    //获取开始计时的系统时间
    public void startTimer(){
        timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                updateTimerImg(SystemClock.elapsedRealtime() - startTime);
            }
        };
        startTime = SystemClock.elapsedRealtime(); //获取系统启动的时间（毫秒为单位）
        timer.scheduleAtFixedRate(task, 1000, 1000); //每隔1000ms更新一下，以1000ms的速率重复进行
    }
    //暂停后继续计时
    public void startPauseTimer(){
        timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                updateTimerImg(SystemClock.elapsedRealtime() - startTime + pauseTime);
            }
        };
        startTime = SystemClock.elapsedRealtime();
        timer.scheduleAtFixedRate(task, 1000, 1000); //每隔1s更新一下
    }
    //更新时间图片
    public void updateTimerImg(long timeSeconds){
        int one = 0;
        int ten = 0;
        int hundred = 0;
        int seconds = (int) (timeSeconds / 1000);
        //时间超出 999s 后就不再更新图片
        if(seconds >= 999){
            return;
        }
        one = seconds % 10;
        ten = (seconds % 100) / 10;
        hundred = (seconds % 1000) / 100;

        ImageView img_one = findViewById(R.id.time_one);
        setNumImg(one,img_one);
        ImageView img_ten = findViewById(R.id.time_ten);
        setNumImg(ten,img_ten);
        ImageView img_hundred = findViewById(R.id.time_hundred);
        setNumImg(hundred,img_hundred);
    }
    //设置笑脸图片
    public void setFaceImg(){
        ImageView imgFace = findViewById(R.id.face_img);
        if(faceImg == 1){
            imgFace.setImageResource(R.drawable.face2);
            faceImg = 0;
        }
        else if(faceImg == 0){
            imgFace.setImageResource(R.drawable.face0);
            faceImg = 1;
        }
        else if(faceImg == 2){ //输了
            imgFace.setImageResource(R.drawable.face3);
        }
        else if(faceImg == 3){//赢了
            imgFace.setImageResource(R.drawable.face4);
        }
        else if(faceImg == 4) {
            //点击提示就设置成小丑图片
            imgFace.setImageResource(R.drawable.joker3);
        }

    }
    //显示提示信息
    public void getTips(){
        //在获得提示之后将笑脸设置为小丑图片
        faceImg = 4;
        setFaceImg();
        for(int i = 0; i < width; i++){
            for(int j = 0; j < height; j++){
                if(mines[i][j].getMine() && !mines[i][j].getClicked() && !mines[i][j].getFlag() && !isEnd){
                    ImageView img = findViewById(mines[i][j].getID());
                    img.setImageResource(R.drawable.hole);
                }
            }
        }
    }
    //隐藏提示信息
    public void hideTips(){
        for(int i = 0; i < width; i++){
            for(int j = 0; j < height; j++){
                if(mines[i][j].getMine()&& !mines[i][j].getFlag() && !isEnd){
                    ImageView img = findViewById(mines[i][j].getID());
                    img.setImageResource(R.drawable.blank);
                }
            }
        }
    }

}