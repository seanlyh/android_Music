package com.example.player;

import java.io.IOException;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.Toast;

public class PlayerService extends Service {

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
	
	public static final String BROADCAST_MUSICSERVICE_CONTROL = "PlayerService.ACTION_CONTROL" ;
	public static final String BROADCAST_MUSICSERVICE_UPDATE_STATUS = "PlayerService.ACTION_UPDATE";
	
	private MediaPlayer player2;
	
	private CommandReceiver receiver;
	
	@Override
	public void onCreate(){
		super.onCreate();
		//�󶨹㲥�����������Խ��չ㲥
		bindCommandReceiver();
		Toast.makeText(this, "PlayerService.onCreate()", Toast.LENGTH_SHORT)
		.show();
	}
	@Override
	public void onStart(Intent intent,int startId){
		super.onStart(intent, startId);
	}
	@Override
	public void onDestroy(){
		//�ͷŲ�������Դ
		if(player2 !=null){
			player2.release();
		}
		super.onDestroy();
	}
	/**�󶨹㲥������*/
	private void bindCommandReceiver(){
		receiver = new CommandReceiver();
		IntentFilter filter = new IntentFilter(BROADCAST_MUSICSERVICE_CONTROL);
		registerReceiver(receiver,filter);
	}
	
	/**���չ㲥�����ִ��*/
	class CommandReceiver extends BroadcastReceiver{
		public void onReceive(Context context, Intent intent) {
			// ��ȡ������
			int command = intent.getIntExtra("command", COMMAND_UNKNOWN);
			//ִ������
			switch (command){
			case COMMAND_SEEK_TO:
				seekTo(intent.getIntExtra("time",0));
				break;
			case COMMAND_PLAY:
			case COMMAND_PREVIOUS:				
			case COMMAND_NEXT:
				int number = intent.getIntExtra("number", 1);
				Toast.makeText(PlayerService.this,"���ڲ��ŵ�" + number + "��" , Toast.LENGTH_SHORT).show();
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
					sendBroadcastOnStatusChanged(PlayerService.STATUS_PLAYING);
				}
				break;
			case COMMAND_UNKNOWN:
				default:
					break;
			}
		}
		
	}
	/**���͹㲥������״̬�ı���*/
	private void sendBroadcastOnStatusChanged(int status){
		Intent intent = new Intent(BROADCAST_MUSICSERVICE_UPDATE_STATUS);
		intent.putExtra("status", status);
		if(status == STATUS_PLAYING){
			intent.putExtra("time", player2.getCurrentPosition());
			intent.putExtra("duration", player2.getDuration());
		}
		sendBroadcast(intent);
	}
	
	
	/**��ȡ�����ļ�*/
	/*private void load(int number){
		if (player2 != null){
			player2.release();
		}
		Uri musicUri = Uri.withAppendedPath(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "" + number);
		//��ȡ�����ļ�������MediaPlayer����
		player2 = MediaPlayer.create(this, musicUri);
		
		//ע�������
		player2.setOnCompletionListener(completionListener);
	}
	*/
	private void load(int number)
	{
		if(player2!=null)
		{
			player2.release();
		}
		String path=null;
		ContentResolver reslover=getContentResolver();
    	Cursor cursor=reslover.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,null, null,null);
		cursor.move(number);
		path=cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)); 		       
			
		try{
		player2=new MediaPlayer();
		player2.setDataSource(path);

        player2.prepare();

        player2.start();
		}catch (IllegalArgumentException e) {

            e.printStackTrace();

         } catch (IllegalStateException e) {

            e.printStackTrace();

         } catch (IOException e) {

            e.printStackTrace();

         }
		player2.setOnCompletionListener(completionListener);
	}

	//���Ž���������
	OnCompletionListener completionListener = new OnCompletionListener() {
		@Override
		public void onCompletion(MediaPlayer player){
			if(player.isLooping()){
				replay();
			} else {
				sendBroadcastOnStatusChanged(PlayerService.STATUS_COMPLETED);
			}
		}
	};
	
	/**��������*/
	private void play(int number){
		//ֹͣ��ǰ����
		if(player2 != null && player2.isPlaying()){
			player2.stop();
		}
		load(number);
		player2.start();
		sendBroadcastOnStatusChanged(PlayerService.STATUS_PLAYING);
	}
	
	/**��ͣ����*/
	private void pause(){
		if(player2.isPlaying()){
			player2.pause();
			sendBroadcastOnStatusChanged(PlayerService.STATUS_PAUSED);
		}	
	}
	
	private void stop(){
		if(player2 != null){
			player2.stop();
			sendBroadcastOnStatusChanged(PlayerService.STATUS_STOPPED);
		}
	}
	
	private void resume(){
		player2.start();
		sendBroadcastOnStatusChanged(PlayerService.STATUS_PLAYING);
	}
	
	/**���֮�����²���*/
	private void replay(){
		player2.start();
		sendBroadcastOnStatusChanged(PlayerService.STATUS_PLAYING);
	}
	
	/**��ת������λ��*/
	private void seekTo(int time){
		if (player2 != null){
			player2.seekTo(time);
		}
	}

}

