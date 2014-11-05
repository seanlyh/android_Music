package com.example.player;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import android.content.Context;


public class PropertyBean {
	public static String[] THEMES;
	public static String DEFAULT_THEME;
	//应用上下文
	private Context context;
	//主题
	private String theme;
	public PropertyBean(Context context) {
		this.context = context;
		//获取array.xml中的主题名称
		THEMES = context.getResources().getStringArray(R.array.theme);
		DEFAULT_THEME = THEMES[2];
		loadTheme();
	}
	/**读取主题。保存在文件"configuration.cfg"*/
	private void loadTheme(){
		Properties properties =new Properties(); 
		try{
			FileInputStream stream = context.openFileInput("configuration.cfg");
			properties.load(stream);
			theme= properties.getProperty("theme").toString();
		} catch (Exception e){
			saveTheme(DEFAULT_THEME);//默认值
		}
	}
	
	/**保存主题。保存在文件"configuration.cfg"中*/
	private boolean saveTheme(String theme){
		Properties properties =new Properties(); 
		properties.put("theme", theme);
		try{
			FileOutputStream stream = context.openFileOutput(
					"configuration.cfg",Context.MODE_WORLD_WRITEABLE);
			properties.store(stream, "");
			return true;
		}catch(Exception e){
			return false;
		}
	}
	
	public String getTheme(){
		return theme;
	}
	public void setAndSaveTheme(String theme){
		this.theme = theme;
		saveTheme(theme);
	}
}