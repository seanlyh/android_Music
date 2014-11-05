package com.mediaplayerdemo.activity;



import java.util.ArrayList;

import com.graceplayer.data.Music;
import com.graceplayer.data.MusicList;

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

public class Main extends Activity {
	// 显示组件
	//测试
		private ImageButton imgBtn_Previous;
		private ImageButton imgBtn_PlayOrPause;
		private ImageButton imgBtn_Stop;
		private ImageButton imgBtn_Next;
		private ListView list;
		
		private TextView text_Current;
		private TextView text_Duration;
		private SeekBar seekBar;
		//更新进度条的Hander
		private Handler seekBarHandler;
		private int duration;
		private int time;
		//进度条控制常量
		private static final int PROGRESS_INCREASE = 0;
		private static final int PROGRESS_PAUSE = 1;
		private static final int PROGRESS_RESET = 2;
		
		//播放模式常量
		private static final int MODE_LIST_SEQUENCE = 0;
		private static final int MODE_SINGLE_CYCLE = 1;
		private static final int MODE_LIST_CYCLE = 2;
		private int playmode;
		
		//歌曲列表对象
		private ArrayList<Music> musicArrayList;

		//主题
		private RelativeLayout root_Layout;
		
		// 当前歌曲的序号，下标从1开始
		private int number = 0;
		//播放状态
		private int status;
		//广播接收器
		private StatusChangedReceiver receiver;

		/** Called when the activity is first created. */
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.main);

			findViews();
			registerListeners();
			number = 1;
			status = MusicService.STATUS_STOPPED;
			duration = 0;
			time = 0;
			startService(new Intent(this,MusicService.class));
			//绑定广播接收器，可以接收广播
			bindStatusChangedReceiver();
			//检查播放器是否正在播放。如果正在播放，以上绑定的接收器会改变UI
			sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
			//初始化进度条
			initSeekBarHandler();
			
			//默认播放模式是顺讯播放
			//playmode = Main.MODE_LIST_SEQUENCE;
		}
		
		/**绑定广播接收器*/
		private void bindStatusChangedReceiver() {
			receiver =new StatusChangedReceiver();
			IntentFilter filter = new IntentFilter(
					MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
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
					sendBroadcastOnCommand(MusicService.COMMAND_PREVIOUS);
				}
			});
			imgBtn_PlayOrPause.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					if (isPlaying()) {
						sendBroadcastOnCommand(MusicService.COMMAND_PAUSE);
					} else if(isPaused()) {
						sendBroadcastOnCommand(MusicService.COMMAND_RESUME);
					} else if(isStopped()){
						sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
					}
				}
			});
			imgBtn_Stop.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					sendBroadcastOnCommand(MusicService.COMMAND_STOP);
				}
			});
			imgBtn_Next.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
				}
			});
			list.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					// position下标从0开始，number下标从1开始
					number = position + 1;
					sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
				}
			});
			
			seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
					//发送广播给MusicService，执行跳转
					sendBroadcastOnCommand(MusicService.COMMAND_SEEK_TO);
					if(isPlaying()) {
						seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);
					}
					
				}
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
					//进度条恢复移动
					seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
					
				}
				@Override
				public void onProgressChanged(SeekBar seekBar,int progress,
						boolean fromUser) {
					time = progress;
					//更新文本
					text_Current.setText(formatTime(time));
					
				}
			});
		}
		
		private void moveNumberToNext() {
			// 判断是否到达了列表底端
			if ((number + 1) > list.getCount()) {
				number = 1;
				Toast.makeText(Main.this,
						Main.this.getString(R.string.tip_reach_bottom),
						Toast.LENGTH_SHORT).show();
			} else {
				++number;
			}
		}
		private void moveNumberToPrevious() {
			// 判断是否到达了列表顶端
			if (number == 1) {
				number = list.getCount();
				Toast.makeText(Main.this,
						Main.this.getString(R.string.tip_reach_top),
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
			//设置主题
			PropertyBean property = new PropertyBean(Main.this);
			String theme = property.getTheme();
			setTheme(theme);
		}
		
		//** 初始化音乐列表。包括获取音乐集和更新显示列表 */
		private void initMusicList() {
			Cursor cursor = getMusicCursor();
			setListContent(cursor);
		}

		//** 更新列表的内容 */
		private void setListContent(Cursor musicCursor) {
			CursorAdapter adapter = new SimpleCursorAdapter(this,
					android.R.layout.simple_list_item_2, musicCursor, new String[] {
							MediaStore.Audio.AudioColumns.TITLE,
							MediaStore.Audio.AudioColumns.ARTIST }, new int[] {
							android.R.id.text1, android.R.id.text2 });
			list.setAdapter(adapter);
		}

		//** 获取系统扫描得到的音乐媒体集 */
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
			Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_CONTROL);
			intent.putExtra("command", command);
			//根据不同命令，封装不同的数据
			switch (command){
			case MusicService.COMMAND_PLAY:
				intent.putExtra("number",number);
				break;
			case MusicService.COMMAND_PREVIOUS:
				moveNumberToPrevious();
				intent.putExtra("number",number);
				break;
			case MusicService.COMMAND_NEXT:
				moveNumberToNext();
				intent.putExtra("number",number);
				break;
			case MusicService.COMMAND_SEEK_TO:
				intent.putExtra("time", time);
				break;
			case MusicService.COMMAND_PAUSE:
			case MusicService.COMMAND_STOP:
			case MusicService.COMMAND_RESUME:
				default:
					break;
			}
			sendBroadcast(intent);
		}
		
		
		/**是否正在播放*/
		private boolean isPlaying()
		{
			return status == MusicService.STATUS_PLAYING;
		}
		/**是否暂停播放音乐*/
		private boolean isPaused()
		{
			return status == MusicService.STATUS_PAUSED;
		}
		/**是否是停止状态*/
		private boolean isStopped()
		{
			return status == MusicService.STATUS_STOPPED;
		}
		
		/**用于播放器状态更新的接收广播*/
		class StatusChangedReceiver extends BroadcastReceiver
		{
			public void onReceive(Context context, Intent intent) 
			{
				// 获取播放状态
				status = intent.getIntExtra("status", -1);
				switch(status) {
				case MusicService.STATUS_PLAYING:
					time = intent.getIntExtra("time", 0);
					duration = intent.getIntExtra("duration", 0);
					seekBarHandler.removeMessages(PROGRESS_INCREASE);
					seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);
					seekBar.setMax(duration);
					seekBar.setProgress(time);
					text_Duration.setText(formatTime(duration));
					imgBtn_PlayOrPause.setBackgroundResource(R.drawable.pause);
					//设置Activity的标题栏文字，提示正在播放的歌曲
					Cursor cursor = Main.this.getMusicCursor();
					cursor.moveToPosition(number - 1);
					String title = cursor.getString(cursor
							.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE));
					Main.this.setTitle("正在播放：" + title + "  --- ");
					break;
				case MusicService.STATUS_PAUSED:
					seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
					imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
					break;
				case MusicService.STATUS_STOPPED:
					seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
					imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
					break;
				case MusicService.STATUS_COMPLETED:
					//sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
					number = intent.getIntExtra("number", 0);
					if(playmode == Main.MODE_LIST_SEQUENCE)		//顺序模式：到达列表末端时发送停止命令，否则播放下一首
					{
						if(number == MusicList.getMusicList().size()-1) 											
							sendBroadcastOnCommand(MusicService.STATUS_STOPPED);
						else
							sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
					}
					else if(playmode == Main.MODE_SINGLE_CYCLE)								//单曲循环
						sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
					else if(playmode == Main.MODE_LIST_CYCLE)			//列表循环：到达列表末端时，把要播放的音乐设置为第一首，
					{																															//					然后发送播放命令。			
						if(number == list.getCount()-1)
						{
							number = 0;
							sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
						}
						else sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
					}
					
					seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
					imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
					break;
					default:
						break;
				}
			}
			
			/** 根据播放起的状态，更新UI */
			private void updateUI(int status)
			{
				switch (status)
				{
				case MusicService.STATUS_PLAYING:
					imgBtn_PlayOrPause.setBackgroundResource(R.drawable.pause);
					break;
				case MusicService.STATUS_PAUSED:
				case MusicService.STATUS_STOPPED:
				case MusicService.STATUS_COMPLETED:
					imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
					break;
				default:
						break;
				}
			}
		}
		
		//格式化：毫秒>> "mm:ss"
		private String formatTime(int msec) {
			int minute = (msec / 1000) / 60;
			int second = (msec / 1000) % 60;
			String minuteString;
			String secondString;
			if(minute < 10) {
				minuteString = "0" + minute;
			} else {
				minuteString = "" + minute;
			}
			if(second < 10) {
				secondString = "0" + second;
			} else {
				secondString = "" +second;
			}
			return minuteString + ":" + secondString;
		}
		
		//格式化时间
		private void initSeekBarHandler() {
			seekBarHandler = new Handler() {
				public void handleMessage(Message msg) {
					super.handleMessage(msg);
					
					switch (msg.what) {
					case PROGRESS_INCREASE:
					    if(seekBar.getProgress() < duration) {
					    	seekBar.incrementProgressBy(1000);
					    	seekBarHandler.sendEmptyMessageDelayed(
					    			PROGRESS_INCREASE,1000 );
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
				stopService(new Intent(this,MusicService.class));
			}
			super.onDestroy();
		}
		
		//Menu常量
		private static final int MENU_THEME = Menu.FIRST;
		private static final int MENU_ABOUT = Menu.FIRST +1;
		private static final int MENU_PLAYMODE = Menu.FIRST +2;

		//创建菜单
		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
			menu.add(0, MENU_THEME, 0, "主题");
			menu.add(0, MENU_ABOUT, 1, "关于");
			menu.add(0, MENU_PLAYMODE, 2, "播放模式");
			return super.onCreateOptionsMenu(menu);		
		}
		
		//处理菜单点击事件
		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch(item.getItemId()) {
			case MENU_THEME:
				new AlertDialog.Builder(this).setTitle("请选择主题")
				        .setItems(R.array.theme, new DialogInterface.OnClickListener() {
				        	public void onClick(DialogInterface dialog, int which) {
					/*String[] themes = Main.this.getResources()
							.getStringArray(R.array.theme);
					setTheme(themes[which]);*/
				        		String theme = PropertyBean.THEMES[which];
				        		setTheme(theme);
				        		PropertyBean property = new PropertyBean(
				        				Main.this);
				        		property.setAndSaveTheme(theme);
				}
			}).show();
			break;
			case MENU_ABOUT:
				new AlertDialog.Builder(this).setTitle("I am DJ")
				       .setMessage(Main.this.getString(R.string.about)).show();
				break;
				
			case MENU_PLAYMODE:
				String[] mode = new String[] { "顺序播放", "单曲循环", "列表循环" };
				AlertDialog.Builder builder = new AlertDialog.Builder(
						Main.this);
				builder.setTitle("播放模式");
				builder.setSingleChoiceItems(mode, playmode,						//设置单选项，这里第二个参数是默认选择的序号，这里根据playmode的值来确定
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								// TODO Auto-generated method stub
								playmode = arg1;
							}
						});
				builder.setPositiveButton("确定",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								// TODO Auto-generated method stub
								switch (playmode) {
								case 0:
									playmode = Main.MODE_LIST_SEQUENCE;
									Toast.makeText(getApplicationContext(), R.string.sequance, Toast.LENGTH_SHORT).show();
									break;
								case 1:
									playmode = Main.MODE_SINGLE_CYCLE;
									Toast.makeText(getApplicationContext(), R.string.singlecycle, Toast.LENGTH_SHORT).show();
									break;
								case 2:
									playmode = Main.MODE_LIST_CYCLE;
									Toast.makeText(getApplicationContext(), R.string.listcycle, Toast.LENGTH_SHORT).show();
									break;
								default:
									break;
								}
							}
						});
				builder.create().show(); 
				break;		
			}
			return super.onOptionsItemSelected(item);
		} 
		
		private void setTheme(String theme) {
			if("泡泡".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_color);
			} else if ("字母".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_a);
			} else if ("树木".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_b);
			} else if ("卡通".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_c);
			} else if ("铁塔".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_d);
			} else if ("水果".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_e);
			}
		}
	
}