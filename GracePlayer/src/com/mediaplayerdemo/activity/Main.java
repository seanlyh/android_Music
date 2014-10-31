package com.mediaplayerdemo.activity;


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
	// ��ʾ���
		private ImageButton imgBtn_Previous;
		private ImageButton imgBtn_PlayOrPause;
		private ImageButton imgBtn_Stop;
		private ImageButton imgBtn_Next;
		private ListView list;
		
		private TextView text_Current;
		private TextView text_Duration;
		private SeekBar seekBar;
		//���½������Hander
		private Handler seekBarHandler;
		private int duration;
		private int time;
		//��������Ƴ���
		private static final int PROGRESS_INCREASE = 0;
		private static final int PROGRESS_PAUSE = 1;
		private static final int PROGRESS_RESET = 2;
		//����
		private RelativeLayout root_Layout;
		
		// ��ǰ�������ţ��±��1��ʼ
		private int number;
		//����״̬
		private int status;
		//�㲥������
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
			//�󶨹㲥�����������Խ��չ㲥
			bindStatusChangedReceiver();
			//��鲥�����Ƿ����ڲ��š�������ڲ��ţ����ϰ󶨵Ľ�������ı�UI
			sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
			//��ʼ�������
			initSeekBarHandler();
		}
		
		/**�󶨹㲥������*/
		private void bindStatusChangedReceiver() {
			receiver =new StatusChangedReceiver();
			IntentFilter filter = new IntentFilter(
					MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
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
					// position�±��0��ʼ��number�±��1��ʼ
					number = position + 1;
					sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
				}
			});
			
			seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
					//���͹㲥��MusicService��ִ����ת
					sendBroadcastOnCommand(MusicService.COMMAND_SEEK_TO);
					if(isPlaying()) {
						seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);
					}
					
				}
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
					//������ָ��ƶ�
					seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
					
				}
				@Override
				public void onProgressChanged(SeekBar seekBar,int progress,
						boolean fromUser) {
					time = progress;
					//�����ı�
					text_Current.setText(formatTime(time));
					
				}
			});
		}
		
		private void moveNumberToNext() {
			// �ж��Ƿ񵽴����б�׶�
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
			// �ж��Ƿ񵽴����б?��
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
			// ��ʼ�������б�
			initMusicList();
			// ����б�û�и����򲥷Ű�ť�����ã��������û�
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
			//��������
			PropertyBean property = new PropertyBean(Main.this);
			String theme = property.getTheme();
			setTheme(theme);
		}
		
		/** ��ʼ�������б?������ȡ���ּ��͸�����ʾ�б� */
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
			// ��ȡ���ѡ����
			ContentResolver resolver = getContentResolver();
			// ѡ������ý�弯
			Cursor cursor = resolver.query(
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
					null);
			return cursor;
		}

		
		private void sendBroadcastOnCommand(int command){
			Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_CONTROL);
			intent.putExtra("command", command);
			//��ݲ�ͬ�����װ��ͬ�����
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
		
		
		/**�Ƿ����ڲ���*/
		private boolean isPlaying()
		{
			return status == MusicService.STATUS_PLAYING;
		}
		/**�Ƿ���ͣ��������*/
		private boolean isPaused()
		{
			return status == MusicService.STATUS_PAUSED;
		}
		/**�Ƿ���ֹͣ״̬*/
		private boolean isStopped()
		{
			return status == MusicService.STATUS_STOPPED;
		}
		
		/**���ڲ�����״̬���µĽ��չ㲥*/
		class StatusChangedReceiver extends BroadcastReceiver
		{
			public void onReceive(Context context, Intent intent) 
			{
				// ��ȡ����״̬
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
					//����Activity�ı��������֣���ʾ���ڲ��ŵĸ���
					Cursor cursor = Main.this.getMusicCursor();
					cursor.moveToPosition(number - 1);
					String title = cursor.getString(cursor
							.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE));
					Main.this.setTitle("���ڲ��ţ�" + title + "  --- ");
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
					sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
					seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
					imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
					break;
					default:
						break;
				}
			}
			
			/** ��ݲ������״̬������UI */
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
		
		//��ʽ��������>> "mm:ss"
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
		
		//��ʽ��ʱ��
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
		
		//Menu����
		private static final int MENU_THEME = Menu.FIRST;
		private static final int MENU_ABOUT = Menu.FIRST +1;
		//�����˵�
		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
			menu.add(0, MENU_THEME, 0, "����");
			menu.add(0, MENU_ABOUT, 1, "����");
			return super.onCreateOptionsMenu(menu);		
		}
		
		//����˵�������¼�
		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch(item.getItemId()) {
			case MENU_THEME:
				new AlertDialog.Builder(this).setTitle("��ѡ������")
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
			}
			return super.onOptionsItemSelected(item);
		} 
		
		private void setTheme(String theme) {
			if("����".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_color);
			} else if ("��ĸ".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_a);
			} else if ("��ľ".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_b);
			} else if ("��ͨ".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_c);
			} else if ("����".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_d);
			} else if ("ˮ��".equals(theme)) {
				root_Layout.setBackgroundResource(R.drawable.bg_e);
			}
		}
	
}