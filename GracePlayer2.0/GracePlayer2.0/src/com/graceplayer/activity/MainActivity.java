package com.graceplayer.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
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
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.graceplayer.data.Music;
import com.graceplayer.data.MusicList;
import com.graceplayer.graceplayer.R;
import com.graceplayer.model.PropertyBean;

public class MainActivity extends Activity {
	
	// 进度条控制常量
	private static final int PROGRESS_INCREASE = 0;
	private static final int PROGRESS_PAUSE = 1;
	private static final int PROGRESS_RESET = 2;
	
	// 显示组件
	private TextView tv_current_time;
	private TextView tv_total_time;
	private ImageButton imgBtn_Previous;
	private ImageButton imgBtn_PlayOrPause;
	private ImageButton imgBtn_Stop;
	private ImageButton imgBtn_Next;
	private SeekBar seekBar;
	private ListView listView;
	private RelativeLayout root_Layout;

	// 当前歌曲的持续时间和当前位置，作用于进度条
	private int total_time;
	private int curent_time;
	// 当前歌曲的序号，下标从0开始
	private int number;
	// 播放状态
	private int status;
	// 广播接收器
	private StatusChangedReceiver receiver;
	// 更新进度条的Handler
	private Handler seekBarHandler;
	
	//歌曲列表对象
	private ArrayList<Music> musicArrayList;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		findViews();
		registerListeners();
		initMusicList();
		initListView();
		checkMusicfile();
		
		startService(new Intent(this, MusicService.class));
		// 绑定广播接收器，可以接收广播
		bindStatusChangedReceiver();
		sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
		// 初始化进度条的Handler
		initSeekBarHandler();
		status = MusicService.COMMAND_STOP;
	}

	void findViews() {
		listView = (ListView) findViewById(R.id.main_listview);
		tv_current_time = (TextView) findViewById(R.id.main_tv_curtime);
		tv_total_time = (TextView) findViewById(R.id.main_tv_totaltime);
		imgBtn_Previous = (ImageButton) findViewById(R.id.main_ibtn_pre);
		imgBtn_PlayOrPause = (ImageButton) findViewById(R.id.main_ibtn_play);
		imgBtn_Previous = (ImageButton) findViewById(R.id.main_ibtn_pre);
		imgBtn_Next = (ImageButton) findViewById(R.id.main_ibtn_next);
		imgBtn_Stop = (ImageButton) findViewById(R.id.main_ibtn_stop);
		seekBar = (SeekBar) findViewById(R.id.main_seekBar);
		root_Layout = (RelativeLayout) findViewById(R.id.relativeLayout1);
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
				switch (status) {
				case MusicService.STATUS_PLAYING:
					sendBroadcastOnCommand(MusicService.COMMAND_PAUSE);
					break;
				case MusicService.STATUS_PAUSED:
					sendBroadcastOnCommand(MusicService.COMMAND_RESUME);
					break;
				case MusicService.COMMAND_STOP:
					sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
				default:
					break;
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
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				number = position;
				sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
			}
		});
		seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (status == MusicService.STATUS_PLAYING) {
					// 发送广播给MusicService，执行跳转
					sendBroadcastOnCommand(MusicService.COMMAND_SEEK_TO);
					// 进度条恢复移动
					seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE,
							1000);
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
				if (status != MusicService.STATUS_STOPPED) {
					curent_time = progress;
					// 更新文本
					tv_current_time.setText(formatTime(curent_time));
				}
			}
		});
	}
	/**初始化音乐列表对象*/
	private void initMusicList() {
		
		musicArrayList = MusicList.getMusicList();
		//避免重复添加音乐
		if(musicArrayList.isEmpty())
		{
			Cursor mMusicCursor = this.getContentResolver().query(
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
					MediaStore.Audio.AudioColumns.TITLE);
			//标题
			int indexTitle = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE);
			//艺术家
			int indexArtist = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST);
			//总时长
			int indexTotalTime = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION);
			//路径
			int indexPath = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATA);

			/**通过mMusicCursor游标遍历数据库，并将Music类对象加载带ArrayList中*/
			for (mMusicCursor.moveToFirst(); !mMusicCursor.isAfterLast(); mMusicCursor
					.moveToNext()) { 
				String strTitle = mMusicCursor.getString(indexTitle);
				String strArtist = mMusicCursor.getString(indexArtist);
				String strTotoalTime = mMusicCursor.getString(indexTotalTime);
				String strPath = mMusicCursor.getString(indexPath);

				if (strArtist.equals("<unknown>"))
					strArtist = "无艺术家";
				Music music = new Music(strTitle, strArtist, strPath, strTotoalTime);
				musicArrayList.add(music);
			}
		}
	}
	/**设置适配器并初始化listView*/
	private void initListView() {
		List<Map<String, String>> list_map = new ArrayList<Map<String, String>>();
		HashMap<String, String> map;
		SimpleAdapter simpleAdapter;
		for (Music music : musicArrayList) {
			map = new HashMap<String, String>();
			map.put("musicName", music.getmusicName());
			map.put("musicArtist", music.getmusicArtist());
			list_map.add(map);
		}

		String[] from = new String[] { "musicName", "musicArtist" };
		int[] to = { R.id.listview_tv_title_item, R.id.listview_tv_artist_item };

		simpleAdapter = new SimpleAdapter(this, list_map, R.layout.listview,from, to);
		listView.setAdapter(simpleAdapter);
	}
	
	/**如果列表没有歌曲，则播放按钮不可用，并提醒用户*/
	private void checkMusicfile()
	{
		if (musicArrayList.isEmpty()) {
			imgBtn_Next.setEnabled(false);
			imgBtn_PlayOrPause.setEnabled(false);
			imgBtn_Previous.setEnabled(false);
			imgBtn_Stop.setEnabled(false);
			Toast.makeText(getApplicationContext(), "当前没有歌曲文件",Toast.LENGTH_SHORT).show();
		} else {
			imgBtn_Next.setEnabled(true);
			imgBtn_PlayOrPause.setEnabled(true);
			imgBtn_Previous.setEnabled(true);
			imgBtn_Stop.setEnabled(true);
		}
	}
	/** 绑定广播接收器 */
	private void bindStatusChangedReceiver() {
		receiver = new StatusChangedReceiver();
		IntentFilter filter = new IntentFilter(
				MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
		registerReceiver(receiver, filter);
	}

	private void initSeekBarHandler() {
		seekBarHandler = new Handler() {
			public void handleMessage(Message msg) {
				super.handleMessage(msg);

				switch (msg.what) {
				case PROGRESS_INCREASE:
					if (seekBar.getProgress() < total_time) {
						// 进度条前进1秒
						seekBar.incrementProgressBy(1000);
						seekBarHandler.sendEmptyMessageDelayed(
								PROGRESS_INCREASE, 1000);
						// 修改显示当前进度的文本
						tv_current_time.setText(formatTime(curent_time));
						curent_time += 1000;
					}
					break;
				case PROGRESS_PAUSE:
					seekBarHandler.removeMessages(PROGRESS_INCREASE);
					break;
				case PROGRESS_RESET:
					// 重置进度条界面
					seekBarHandler.removeMessages(PROGRESS_INCREASE);
					seekBar.setProgress(0);
					tv_current_time.setText("00:00");
					break;
				}
			}
		};
	}
	/** 发送命令，控制音乐播放。参数定义在MusicService类中 */
	private void sendBroadcastOnCommand(int command) {

		Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_CONTROL);
		intent.putExtra("command", command);
		// 根据不同命令，封装不同的数据
		switch (command) {
		case MusicService.COMMAND_PLAY:
			intent.putExtra("number", number);
			break;
		case MusicService.COMMAND_SEEK_TO:
			intent.putExtra("time", curent_time);
			break;
		case MusicService.COMMAND_PREVIOUS:
		case MusicService.COMMAND_NEXT:
		case MusicService.COMMAND_PAUSE:
		case MusicService.COMMAND_STOP:
		case MusicService.COMMAND_RESUME:
		default:
			break;
		}
		sendBroadcast(intent);
	}

	/** 内部类，用于播放器状态更新的接收广播 */
	class StatusChangedReceiver extends BroadcastReceiver {
		public void onReceive(Context context, Intent intent) {
			// 获取播放器状态
			status = intent.getIntExtra("status", -1);
			switch (status) {
			case MusicService.STATUS_PLAYING:
				String musicName = intent.getStringExtra("musicName");
				String musicArtist = intent.getStringExtra("musicArtist");

				seekBarHandler.removeMessages(PROGRESS_INCREASE);
				curent_time = intent.getIntExtra("time", 0);
				total_time = intent.getIntExtra("duration", 0);
				number = intent.getIntExtra("number", number);
				listView.setSelection(number);

				seekBar.setProgress(curent_time);
				seekBar.setMax(total_time);
				seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);

				tv_total_time.setText(formatTime(total_time));
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.pause);
				
				// 设置Activity的标题栏文字，提示正在播放的歌曲
				MainActivity.this.setTitle("正在播放:" + musicName + " "+ musicArtist);
				break;
			case MusicService.STATUS_PAUSED:
				seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
				String string = MainActivity.this.getTitle().toString().replace("正在播放:", "已暂停:");
				MainActivity.this.setTitle(string);
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				break;
			case MusicService.STATUS_STOPPED:
				curent_time = 0;
				total_time = 0;
				tv_current_time.setText(formatTime(curent_time));
				tv_total_time.setText(formatTime(total_time));
				seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
				MainActivity.this.setTitle("GracePlayer");
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				break;
			case MusicService.STATUS_COMPLETED:
				number = intent.getIntExtra("number", 0);
				if(number == MusicList.getMusicList().size()-1) 
				{
					sendBroadcastOnCommand(MusicService.STATUS_STOPPED);
				}
				else
				{
					sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
				}
				
				seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
				MainActivity.this.setTitle("GracePlayer");
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				break;
			default:
				break;
			}
		}
	}

	/** 格式化：毫秒 -> "mm:ss" */
	private String formatTime(int msec) {
		int minute = (msec / 1000) / 60;
		int second = (msec / 1000) % 60;
		String minuteString;
		String secondString;
		if (minute < 10) {
			minuteString = "0" + minute;
		} else {
			minuteString = "" + minute;
		}
		if (second < 10) {
			secondString = "0" + second;
		} else {
			secondString = "" + second;
		}
		return minuteString + ":" + secondString;
	}

	/** 设置Activity的主题，包括修改背景图片等 */
	private void setTheme(String theme) {
		if ("彩色".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_color);
		} else if ("花朵".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_digit_flower);
		} else if ("群山".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_mountain);
		} else if ("小狗".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_running_dog);
		} else if ("冰雪".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_snow);
		} else if ("女孩".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_music_girl);
		} else if ("朦胧".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_blur);
		}
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		
		// 检查播放器是否正在播放。如果正在播放，以上绑定的接收器会改变UI
		sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
		PropertyBean property = new PropertyBean(MainActivity.this);
		String theme = property.getTheme();
		// 设置Activity的主题
		setTheme(theme);
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		if (status == MusicService.STATUS_STOPPED) {
			stopService(new Intent(this, MusicService.class));
		}
		super.onDestroy();
	}

	/** 创建菜单 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	/** 处理菜单点击事件 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_theme:
			// 显示列表对话框
			new AlertDialog.Builder(this)
					.setTitle("请选择主题")
					.setItems(R.array.theme,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									// 获取在array.xml中定义的主题名称
									String theme = PropertyBean.THEMES[which];
									// 设置Activity的主题
									setTheme(theme);
									// 保存选择的主题
									PropertyBean property = new PropertyBean(
											MainActivity.this);
									property.setAndSaveTheme(theme);
								}
							}).show();
			break;
		case R.id.menu_about:
			// 显示文本对话框
			new AlertDialog.Builder(MainActivity.this).setTitle("GracePlayer")
					.setMessage(R.string.about).show();
			break;
		case R.id.menu_quit:
			//退出程序
			new AlertDialog.Builder(MainActivity.this).setTitle("提示")
			.setMessage(R.string.quit_message).setPositiveButton("确定", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					// TODO Auto-generated method stub
					System.exit(0);
				}
			}).setNegativeButton("取消", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					// TODO Auto-generated method stub
					
				}
			}).show();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

}
