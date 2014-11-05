package com.example.player;

import com.example.player.R.layout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	// 显示组件
	private ImageButton imgBtn_Previous;
	private ImageButton imgBtn_PlayOrPause;
	private ImageButton imgBtn_Stop;
	private ImageButton imgBtn_Next;
	private ListView list;
	private TextView text_Current;
	private TextView text_Duration;
	private SeekBar seekBar;
	//更新进度条的Handler
	private Handler seekBarHandler;
	//当前歌曲持续时间和当前位置，作用于进度条
	private int duration;
	private int time;
	//进度条控制常量
	private static final int PROGRESS_INCREASE = 0;
	private static final int PROGRESS_PAUSE = 1;
	private static final int PROGRESS_RESET = 2;
	// 当前歌曲的序号，下标从1开始
	private int number;
	//播放状态
	private int status;
	//广播接收器
	private StatusChangedReceiver receiver;
	
	private RelativeLayout root_Layout ;
	
	private long exitTime;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViews();
		registerListeners();
		number = 1;
		status = PlayerService.STATUS_STOPPED;
		duration = 0;
		time = 0;
		startService(new Intent(this,PlayerService.class));
		//绑定广播接收器，可以接收广播
		bindStatusChangedReceiver();
		//检查播放器是否正在播放。如果正在播放，以上绑定的接收器会改变UI
		sendBroadcastOnCommand(PlayerService.COMMAND_CHECK_IS_PLAYING);
		//初始化进度条的Handler
		initSeeBarHandler();
	}
	
	
	

	/**绑定广播接收器*/
	private void bindStatusChangedReceiver() {
		receiver =new StatusChangedReceiver();
		IntentFilter filter = new IntentFilter(
				PlayerService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
		registerReceiver(receiver,filter);
	}

	/** 获取显示组件 */
	private void findViews() {
		imgBtn_Previous = (ImageButton) findViewById(R.id.imageButton1);
		imgBtn_PlayOrPause = (ImageButton) findViewById(R.id.imageButton2);
		imgBtn_Stop = (ImageButton) findViewById(R.id.imageButton3);
		imgBtn_Next = (ImageButton) findViewById(R.id.imageButton4);
		list = (ListView) findViewById(R.id.listView1);
		seekBar = (SeekBar) findViewById(R.id.seekBar1);
		text_Current = (TextView) findViewById(R.id.textView1);
		text_Duration = (TextView) findViewById(R.id.textView2);
		root_Layout = (RelativeLayout)findViewById(R.id.relativeLayout1);
	}

	/** 为显示组件注册监听器 */
	private void registerListeners() {
		imgBtn_Previous.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				sendBroadcastOnCommand(PlayerService.COMMAND_PREVIOUS);
			}
		});
		imgBtn_PlayOrPause.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				if (isPlaying()) {
					sendBroadcastOnCommand(PlayerService.COMMAND_PAUSE);
				} else if(isPaused()) {
					sendBroadcastOnCommand(PlayerService.COMMAND_RESUME);
				} else if(isStopped()){
					sendBroadcastOnCommand(PlayerService.COMMAND_PLAY);
				}
			}
		});
		imgBtn_Stop.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				sendBroadcastOnCommand(PlayerService.COMMAND_STOP);
			}
		});
		imgBtn_Next.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				sendBroadcastOnCommand(PlayerService.COMMAND_NEXT);
			}
		});
		list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				// position下标从0开始，number下标从1开始
				number = position + 1;
				sendBroadcastOnCommand(PlayerService.COMMAND_PLAY);
			}
		});
		seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
			@Override
			public void onStopTrackingTouch(SeekBar seekBar){
				//发送广播给MusicService,执行跳转
				sendBroadcastOnCommand(PlayerService.COMMAND_SEEK_TO);
				if(isPlaying()){
					//进度条回复移动
					seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);
				}
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// 进度条暂停移动
				seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				time = progress;
				// 更新文本
				text_Current.setText(formatTime(time));	
			}	
		});
	}
	
	private void moveNumberToNext() {
		// 判断是否到达了列表底端
		if ((number + 1) > list.getCount()) {
			number = 1;
			Toast.makeText(MainActivity.this,
					MainActivity.this.getString(R.string.tip_reach_bottom),
					Toast.LENGTH_SHORT).show();
		} else {
			++number;
		}
	}
	private void moveNumberToPrevious() {
		// 判断是否到达了列表顶端
		if (number == 1) {
			number = list.getCount();
			Toast.makeText(MainActivity.this,
					MainActivity.this.getString(R.string.tip_reach_top),
					Toast.LENGTH_SHORT).show();
		} else {
			--number;
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		// 初始化音乐列表
		initMusicList();
		// 如果列表没有歌曲，则播放按钮不可用，并提醒用户
		if (list.getCount() == 0) {
			imgBtn_Previous.setEnabled(false);
			imgBtn_PlayOrPause.setEnabled(false);
			imgBtn_Stop.setEnabled(false);
			imgBtn_Next.setEnabled(false);
			Toast.makeText(this, this.getString(R.string.tip_no_music_file),
					Toast.LENGTH_SHORT).show();
		} else {
			imgBtn_Previous.setEnabled(true);
			imgBtn_PlayOrPause.setEnabled(true);
			imgBtn_Stop.setEnabled(true);
			imgBtn_Next.setEnabled(true);
		}
		PropertyBean property = new PropertyBean(MainActivity.this);
		String theme = property.getTheme();
		setTheme(theme);
		
	}
	
	/** 初始化音乐列表。包括获取音乐集和更新显示列表 */
	private void initMusicList() {
		Cursor cursor = getMusicCursor();
		setListContent(cursor);
	}

	/** 更新列表的内容 */
	private void setListContent(Cursor musicCursor) {
		CursorAdapter adapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_2, musicCursor, new String[] {
						MediaStore.Audio.AudioColumns.TITLE,
						MediaStore.Audio.AudioColumns.ARTIST }, new int[] {
						android.R.id.text1, android.R.id.text2 });
		list.setAdapter(adapter);
	}

	/** 获取系统扫描得到的音乐媒体集 */
	private Cursor getMusicCursor() {
		// 获取数据选择器
		ContentResolver resolver = getContentResolver();
		// 选择音乐媒体集
		Cursor cursor = resolver.query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
				null);
		return cursor;
	}
	
	private void sendBroadcastOnCommand(int command){
		Intent intent = new Intent(PlayerService.BROADCAST_MUSICSERVICE_CONTROL);
		intent.putExtra("command", command);
		//根据不同命令，封装不同的数据
		switch (command){
		case PlayerService.COMMAND_PLAY:
			intent.putExtra("number",number);
			break;
		case PlayerService.COMMAND_PREVIOUS:
			moveNumberToPrevious();
			intent.putExtra("number",number);
			break;
		case PlayerService.COMMAND_NEXT:
			moveNumberToNext();
			intent.putExtra("number",number);
			break;
		case PlayerService.COMMAND_SEEK_TO:
			intent.putExtra("time", time);
			break;
		case PlayerService.COMMAND_PAUSE:
		case PlayerService.COMMAND_STOP:
		case PlayerService.COMMAND_RESUME:
			default:
				break;
			
		}
		sendBroadcast(intent);
	}
		
	/**是否正在播放*/
	private boolean isPlaying()
	{
		return status == PlayerService.STATUS_PLAYING;
	}
	/**是否暂停播放音乐*/
	private boolean isPaused()
	{
		return status == PlayerService.STATUS_PAUSED;
	}
	/**是否是停止状态*/
	private boolean isStopped()
	{
		return status == PlayerService.STATUS_STOPPED;
	}
	
	/**用于播放器状态更新的接收广播*/
	class StatusChangedReceiver extends BroadcastReceiver
	{
		public void onReceive(Context context, Intent intent) 
		{
			// 获取播放状态
			status = intent.getIntExtra("status", -1);
			//设置Activity的标题栏文字，提示正在播放的歌曲
			Cursor cursor = MainActivity.this.getMusicCursor();
			cursor.moveToPosition(number - 1);
			String title = cursor.getString(cursor
					.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE));
			switch (status){
			case PlayerService.STATUS_PLAYING:
				time = intent.getIntExtra("time", 0);
				duration = intent.getIntExtra("duration", 0);
				seekBarHandler.removeMessages(PROGRESS_INCREASE);
				seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);
				seekBar.setMax(duration);
				seekBar.setProgress(time);
				text_Duration.setText(formatTime(duration));
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.pause);
				
				MainActivity.this.setTitle("正在播放：" + title + "    ^_-");
				break;
			case PlayerService.STATUS_PAUSED:
				seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				MainActivity.this.setTitle("暂停播放：" + title + "    ^_-");
				break;
			case PlayerService.STATUS_STOPPED:
				seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				MainActivity.this.setTitle(" -_- ");
				break;
			case PlayerService.STATUS_COMPLETED:
				sendBroadcastOnCommand(PlayerService.COMMAND_NEXT);
				seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				MainActivity.this.setTitle(" -_- ");
				break;
			default:
				break;
				
			}
		}	
	}
	
	/**格式化：毫秒->"mm:ss"*/
	private String formatTime(int msec){
		int minute = (msec / 1000) / 60;
		int second = (msec / 1000) % 60;
		String minuteString;
		String secondString;
		if(minute < 10){
			minuteString = "0" + minute;
		} else{
			minuteString = "" + minute;
		}
		if(second < 10){
			secondString = "0" + second;
		} else {
			secondString = "" + second;
		}
		
		return minuteString + ":" + secondString;
	}
	
	private void initSeeBarHandler() {
		seekBarHandler = new Handler() {
			public void handleMessage(Message msg){
				super.handleMessage(msg);
				
				switch (msg.what){
				case PROGRESS_INCREASE:
					if(seekBar.getProgress() < duration){
						//进度条前进一秒
						seekBar.incrementProgressBy(1000);
						seekBarHandler.sendEmptyMessageDelayed(
								PROGRESS_INCREASE, 1000);
						//修改显示当前进度的文本
						text_Current.setText(formatTime(time));
						time += 1000;
					}
					break;
				case PROGRESS_PAUSE:
					seekBarHandler.removeMessages(PROGRESS_INCREASE);
					break;
				case PROGRESS_RESET:
					seekBarHandler.removeMessages(PROGRESS_INCREASE);
					seekBar.setProgress(0);
					text_Current.setText("00:00");
					break;
				}
			}
		};
		
	}
	
	@Override
	protected void onDestroy(){
		if(isStopped()){
			stopService(new Intent(this,PlayerService.class));
		}
		super.onDestroy();
	}
	
	//Menu常量
	public static final int MENU_THEME = Menu.FIRST;
	public static final int MENU_ABOUT = Menu.FIRST + 1;
	/**创建菜单*/
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		menu.add(0, MENU_THEME, 0, "主题");
		menu.add(0, MENU_ABOUT, 1, "关于");
		return super.onCreateOptionsMenu(menu);
	}
	/**处理菜单点击事件*/
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case MENU_THEME:
			//显示列表对话框
			new AlertDialog.Builder(this)
				.setTitle("选择主题")
				.setItems(R.array.theme,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
										//获取在array.xml中定义的主题名称
										String theme = PropertyBean.THEMES[which];
										//设置Activity主题
										setTheme(theme);
										//保存选择主题
										PropertyBean property = new PropertyBean(
												MainActivity.this);
										property.setAndSaveTheme(theme);
												}
								}).show();
					break;
		case MENU_ABOUT:
			//显示文本对话框
			new AlertDialog.Builder(MainActivity.this).setTitle("简约")
			.setMessage(MainActivity.this.getString(R.string.about)).show();
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	
	/**设置Activity的主题，包括修改背景图片等*/
	private void setTheme(String theme){
		if ("性感".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_sexy);	 		
		}else if ("美腿".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_leg);	
		}else if ("水泡".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_color);
		}else if ("吉他".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_gt);
		}else if ("汤唯".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_tw);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN){   
	        if((System.currentTimeMillis()-exitTime) > 2000){  
	            Toast.makeText(getApplicationContext(), "再按一次退出程序", Toast.LENGTH_SHORT).show();                                
	            exitTime = System.currentTimeMillis();   
	        } else {
	            finish();
	            System.exit(0);
	        }
	        return true;   
	    }
	    return super.onKeyDown(keyCode, event);
	}

}

