package com.mediaplayerdemo.activity;



import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;


public class MusicService extends Service {
	@Override
	public IBinder onBind(Intent intent) {
		
		return null;
	}
	public static final int COMMAND_UNKNOWN = -1;
	public static final int COMMAND_PLAY = 0;
	public static final int COMMAND_PAUSE = 1;
	public static final int COMMAND_STOP = 2;
	public static final int COMMAND_RESUME = 3;
	public static final int COMMAND_PREVIOUS = 4;
	public static final int COMMAND_NEXT = 5 ;
	public static final int COMMAND_CHECK_IS_PLAYING = 6;
	public static final int COMMAND_SEEK_TO = 7;
	
	public static final int STATUS_PLAYING = 0;
	public static final int STATUS_PAUSED = 1;
	public static final int STATUS_STOPPED = 2;
	public static final int STATUS_COMPLETED = 3;
	
	public static final String BROADCAST_MUSICSERVICE_CONTROL = "MusicService.ACTION_CONTROL" ;
	public static final String BROADCAST_MUSICSERVICE_UPDATE_STATUS = "MusicService.ACTION_UPDATE";
	
	private MediaPlayer player2;
	
	private CommandReceiver receiver;
	
	@Override
	public void onCreate(){
		super.onCreate();
		//绑定广播接收器，可以接收广播
		bindCommandReceiver();
		Toast.makeText(this, "MusicService.onCreate()", Toast.LENGTH_SHORT)
		.show();
	}
	@Override
	public void onStart(Intent intent,int startId){
		super.onStart(intent, startId);
	}
	@Override
	public void onDestroy(){
		//释放播放器资源
		if(player2 !=null){
			player2.release();
		}
		super.onDestroy();
	}
	/**绑定广播接收器*/
	private void bindCommandReceiver(){
		receiver = new CommandReceiver();
		IntentFilter filter = new IntentFilter(BROADCAST_MUSICSERVICE_CONTROL);
		registerReceiver(receiver,filter);
	}
	
	/**接收广播命令，并执行*/
	class CommandReceiver extends BroadcastReceiver{
		public void onReceive(Context context, Intent intent) {
			// 获取命令行
			int command = intent.getIntExtra("command", COMMAND_UNKNOWN);
			//执行命令
			switch (command){
			case COMMAND_SEEK_TO:
				seekTo(intent.getIntExtra("time", 0));
				break;
			case COMMAND_PLAY:
			case COMMAND_PREVIOUS:				
			case COMMAND_NEXT:
				int number = intent.getIntExtra("number", 1);
				Toast.makeText(MusicService.this,"正在播放第" + number + "首" , Toast.LENGTH_SHORT).show();
				play(number);
				break;
			case COMMAND_PAUSE:
				pause();
				break;
			case COMMAND_RESUME:
				resume();
				break;
			case COMMAND_STOP:
				stop();
				break;
			case COMMAND_CHECK_IS_PLAYING:
				if(player2.isPlaying())
				{
					sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
				}
				break;
			case COMMAND_UNKNOWN:
				default:
					break;
			}
		}
		
	}
	/**发送广播，提醒状态改变了*/
	private void sendBroadcastOnStatusChanged(int status){
		Intent intent = new Intent(BROADCAST_MUSICSERVICE_UPDATE_STATUS);
		intent.putExtra("status", status);
		
		if (status == STATUS_PLAYING) {
			intent.putExtra("time", player2.getCurrentPosition());
			intent.putExtra("duration", player2.getDuration());
		}
		
		sendBroadcast(intent);
	}
	
	
	/**读取音乐文件*/
	private void load(int number){
		if (player2 != null){
			player2.release();
		}
		Uri musicUri = Uri.withAppendedPath(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "" + number);
		//读取音乐文件，创建MediaPlayer对象
		player2 = MediaPlayer.create(this, musicUri);
		//注册监听器
		player2.setOnCompletionListener(completionListener);
	}
	
	//播放结束监听器
	OnCompletionListener completionListener = new OnCompletionListener() {
		@Override
		public void onCompletion(MediaPlayer player){
			if(player.isLooping()){
				replay();
			} else {
				sendBroadcastOnStatusChanged(MusicService.STATUS_COMPLETED);
			}
		}
	};
	
	/**播放音乐*/
	private void play(int number){
		//停止当前播放
		if(player2 != null && player2.isPlaying()){
			player2.stop();
		}
		load(number);
		player2.start();
		sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
	}
	
	/**暂停音乐*/
	private void pause(){
		if(player2.isPlaying()){
			player2.pause();
			sendBroadcastOnStatusChanged(MusicService.STATUS_PAUSED);
		}	
	}
	
	private void stop(){
		if(player2 != null){
			player2.stop();
			sendBroadcastOnStatusChanged(MusicService.STATUS_STOPPED);
		}
	}
	
	private void resume(){
		player2.start();
		sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
	}
	
	/**完成之后重新播放*/
	private void replay(){
		player2.start();
		sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
	}
	
	//跳转至播放位置
	private void seekTo(int time) {
		if(player2 != null) {
			player2.seekTo(time);
		}
	}

}