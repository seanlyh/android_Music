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
	// ��ʾ���
	private ImageButton imgBtn_Previous;
	private ImageButton imgBtn_PlayOrPause;
	private ImageButton imgBtn_Stop;
	private ImageButton imgBtn_Next;
	private ListView list;
	private TextView text_Current;
	private TextView text_Duration;
	private SeekBar seekBar;
	//���½�������Handler
	private Handler seekBarHandler;
	//��ǰ��������ʱ��͵�ǰλ�ã������ڽ�����
	private int duration;
	private int time;
	//���������Ƴ���
	private static final int PROGRESS_INCREASE = 0;
	private static final int PROGRESS_PAUSE = 1;
	private static final int PROGRESS_RESET = 2;
	// ��ǰ��������ţ��±��1��ʼ
	private int number;
	//����״̬
	private int status;
	//�㲥������
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
		//�󶨹㲥�����������Խ��չ㲥
		bindStatusChangedReceiver();
		//��鲥�����Ƿ����ڲ��š�������ڲ��ţ����ϰ󶨵Ľ�������ı�UI
		sendBroadcastOnCommand(PlayerService.COMMAND_CHECK_IS_PLAYING);
		//��ʼ����������Handler
		initSeeBarHandler();
	}
	
	
	

	/**�󶨹㲥������*/
	private void bindStatusChangedReceiver() {
		receiver =new StatusChangedReceiver();
		IntentFilter filter = new IntentFilter(
				PlayerService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
		registerReceiver(receiver,filter);
	}

	/** ��ȡ��ʾ��� */
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

	/** Ϊ��ʾ���ע������� */
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
				// position�±��0��ʼ��number�±��1��ʼ
				number = position + 1;
				sendBroadcastOnCommand(PlayerService.COMMAND_PLAY);
			}
		});
		seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
			@Override
			public void onStopTrackingTouch(SeekBar seekBar){
				//���͹㲥��MusicService,ִ����ת
				sendBroadcastOnCommand(PlayerService.COMMAND_SEEK_TO);
				if(isPlaying()){
					//�������ظ��ƶ�
					seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);
				}
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// ��������ͣ�ƶ�
				seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				time = progress;
				// �����ı�
				text_Current.setText(formatTime(time));	
			}	
		});
	}
	
	private void moveNumberToNext() {
		// �ж��Ƿ񵽴����б�׶�
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
		// �ж��Ƿ񵽴����б���
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
		// ��ʼ�������б�
		initMusicList();
		// ����б�û�и������򲥷Ű�ť�����ã��������û�
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
	
	/** ��ʼ�������б�������ȡ���ּ��͸�����ʾ�б� */
	private void initMusicList() {
		Cursor cursor = getMusicCursor();
		setListContent(cursor);
	}

	/** �����б������ */
	private void setListContent(Cursor musicCursor) {
		CursorAdapter adapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_2, musicCursor, new String[] {
						MediaStore.Audio.AudioColumns.TITLE,
						MediaStore.Audio.AudioColumns.ARTIST }, new int[] {
						android.R.id.text1, android.R.id.text2 });
		list.setAdapter(adapter);
	}

	/** ��ȡϵͳɨ��õ�������ý�弯 */
	private Cursor getMusicCursor() {
		// ��ȡ����ѡ����
		ContentResolver resolver = getContentResolver();
		// ѡ������ý�弯
		Cursor cursor = resolver.query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
				null);
		return cursor;
	}
	
	private void sendBroadcastOnCommand(int command){
		Intent intent = new Intent(PlayerService.BROADCAST_MUSICSERVICE_CONTROL);
		intent.putExtra("command", command);
		//���ݲ�ͬ�����װ��ͬ������
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
		
	/**�Ƿ����ڲ���*/
	private boolean isPlaying()
	{
		return status == PlayerService.STATUS_PLAYING;
	}
	/**�Ƿ���ͣ��������*/
	private boolean isPaused()
	{
		return status == PlayerService.STATUS_PAUSED;
	}
	/**�Ƿ���ֹͣ״̬*/
	private boolean isStopped()
	{
		return status == PlayerService.STATUS_STOPPED;
	}
	
	/**���ڲ�����״̬���µĽ��չ㲥*/
	class StatusChangedReceiver extends BroadcastReceiver
	{
		public void onReceive(Context context, Intent intent) 
		{
			// ��ȡ����״̬
			status = intent.getIntExtra("status", -1);
			//����Activity�ı��������֣���ʾ���ڲ��ŵĸ���
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
				
				MainActivity.this.setTitle("���ڲ��ţ�" + title + "    ^_-");
				break;
			case PlayerService.STATUS_PAUSED:
				seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				MainActivity.this.setTitle("��ͣ���ţ�" + title + "    ^_-");
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
	
	/**��ʽ��������->"mm:ss"*/
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
						//������ǰ��һ��
						seekBar.incrementProgressBy(1000);
						seekBarHandler.sendEmptyMessageDelayed(
								PROGRESS_INCREASE, 1000);
						//�޸���ʾ��ǰ���ȵ��ı�
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
	
	//Menu����
	public static final int MENU_THEME = Menu.FIRST;
	public static final int MENU_ABOUT = Menu.FIRST + 1;
	/**�����˵�*/
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		menu.add(0, MENU_THEME, 0, "����");
		menu.add(0, MENU_ABOUT, 1, "����");
		return super.onCreateOptionsMenu(menu);
	}
	/**����˵�����¼�*/
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case MENU_THEME:
			//��ʾ�б�Ի���
			new AlertDialog.Builder(this)
				.setTitle("ѡ������")
				.setItems(R.array.theme,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
										//��ȡ��array.xml�ж������������
										String theme = PropertyBean.THEMES[which];
										//����Activity����
										setTheme(theme);
										//����ѡ������
										PropertyBean property = new PropertyBean(
												MainActivity.this);
										property.setAndSaveTheme(theme);
												}
								}).show();
					break;
		case MENU_ABOUT:
			//��ʾ�ı��Ի���
			new AlertDialog.Builder(MainActivity.this).setTitle("��Լ")
			.setMessage(MainActivity.this.getString(R.string.about)).show();
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	
	/**����Activity�����⣬�����޸ı���ͼƬ��*/
	private void setTheme(String theme){
		if ("�Ը�".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_sexy);	 		
		}else if ("����".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_leg);	
		}else if ("ˮ��".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_color);
		}else if ("����".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_gt);
		}else if ("��Ψ".equals(theme)){
			root_Layout.setBackgroundResource(R.drawable.bg_tw);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN){   
	        if((System.currentTimeMillis()-exitTime) > 2000){  
	            Toast.makeText(getApplicationContext(), "�ٰ�һ���˳�����", Toast.LENGTH_SHORT).show();                                
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

