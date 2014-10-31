package com.mediaplayerdemo.activity;



import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;



public class PropertyBean  {
  public static String[] THEMES;
  private static String DEFAULT_THEME;
  private Context context;
  private String theme; 
  
  public PropertyBean(Context context) {
	  this.context = context;
	  THEMES = context.getResources().getStringArray(R.array.theme);
	  DEFAULT_THEME = THEMES[0];
	  loadTheme();
  }
  //读取主题。保存在文件configuration.cfg中
  private void loadTheme() {
	  Properties properties = new Properties(); 
	  try {
		  FileInputStream stream = context.openFileInput("configuration.cfg");
		  properties.load(stream);
		  theme = properties.getProperty("theme").toString();
	  } catch (Exception e) {
		  saveTheme(DEFAULT_THEME);
	  }
  }	
  
  //保存主题。保存在文件configuration.cfg中
	private boolean saveTheme(String theme) {
		Properties properties = new Properties();
		properties.put("theme", theme);
		try {
			FileOutputStream stream = context.openFileOutput(
					"configuration.cfg", Context.MODE_WORLD_WRITEABLE);
			properties.store(stream,"");
			return true;
		} catch (Exception e) {
			return false;
		}
	}  
	

		  public String getTheme() { 
			  return theme;
		  }
		  
		  public void setAndSaveTheme(String theme) {
			  this.theme = theme;
			  saveTheme(theme);
		  }
	 
  

	
}