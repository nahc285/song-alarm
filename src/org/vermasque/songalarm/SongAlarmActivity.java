package org.vermasque.songalarm;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TimePicker;
import android.widget.Toast;

public class SongAlarmActivity extends PreferenceActivity implements OnPreferenceClickListener, OnTimeSetListener, OnPreferenceChangeListener
{
	private static final long NO_ALARM_TIME_SET = -1;
	
	private static final int RESULT_ID_SONG = 0;
	
	private Preference mAlarmTimePref, mAlarmEnabledPref;

	private long alarmTimestamp;	
	
	private PendingIntent lastAlarmIntent;

	private Preference mSongPref;
	
	public SongAlarmActivity()
	{
		alarmTimestamp  = NO_ALARM_TIME_SET;
		lastAlarmIntent = null;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Add views of preferences to existing empty layout
		// Content view must have been set in superclass onCreate
		addPreferencesFromResource(R.xml.preferences);

		mAlarmTimePref = findPreference("alarm_time");
		mAlarmEnabledPref = findPreference("alarm_enabled");
		mSongPref = findPreference("song");
		
		mAlarmTimePref.setOnPreferenceClickListener(this);		
		mAlarmEnabledPref.setOnPreferenceChangeListener(this);
		mSongPref.setOnPreferenceClickListener(this);
	}
	
	@Override
	public boolean onPreferenceClick(Preference pref)
	{
		if (pref == mAlarmTimePref) {
			Resources res = getResources();
			
			new TimePickerDialog(
				this, // context 
				this, // time set handler
				res.getInteger(R.integer.default_hour), 
				res.getInteger(R.integer.default_minutes), 
				false // not 24-hr clock format
			).show();
			
			return true;
		}
		else if (pref == mSongPref) {
			Intent innerIntent = new Intent(android.content.Intent.ACTION_GET_CONTENT);
			
			innerIntent.setType("audio/*");
			innerIntent.addCategory(Intent.CATEGORY_OPENABLE);
			
			startActivityForResult(Intent.createChooser(innerIntent, null), RESULT_ID_SONG);
		}
	
		return false;
	}
	
	/* (non-Javadoc)
	 * @see android.preference.PreferenceActivity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent)
	{
		if (RESULT_OK == resultCode) {
			switch (requestCode)
			{
			case RESULT_ID_SONG:
				Uri songUri = resultIntent.getData();
								
				// assume external storage because audio files are not tied
				// to a specific application and should be world-readable and exportable
				Uri contentUri = Audio.Media.EXTERNAL_CONTENT_URI;
								
				String[] columns = {
					Audio.AudioColumns.ARTIST,   Audio.AudioColumns.ALBUM, 
					Audio.AudioColumns.DURATION, Audio.AudioColumns.TITLE
				};
				
				String whereClause = BaseColumns._ID + "=?";
				
				String[] selectArgs = {songUri.getLastPathSegment()}; //{"LIMIT 1"}; ?
				
				Cursor cursor = managedQuery(contentUri, columns, whereClause, selectArgs, null);
							
				if (cursor.moveToFirst()) {
					
					String artist, album, title;
					long duration;
					
					try {
						artist = getColumnStringValue(cursor, Audio.AudioColumns.ARTIST);
						album  = getColumnStringValue(cursor, Audio.AudioColumns.ALBUM);
						title  = getColumnStringValue(cursor, Audio.AudioColumns.TITLE);
					
						duration = cursor.getLong(cursor.getColumnIndexOrThrow(Audio.AudioColumns.DURATION));
					
						// TODO make this output more user-friendly
					
						mSongPref.setSummary(artist + ", " + album + ", " + title + ", " + duration);	
					}
					catch(IllegalArgumentException e) {
						Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
					}
				}
				
				break;
			default:
				break;
			}
		}
	}

	private String getColumnStringValue(Cursor cursor, String columnName)
	{
		return cursor.getString(cursor.getColumnIndexOrThrow(columnName));
	}

	@Override
	public void onTimeSet(TimePicker picker, int hourOfDay, int minutes)
	{
		Calendar cal = Calendar.getInstance();
		
		// guarantee current time set to avoid side effects of previous calls to this function
		cal.setTimeInMillis(System.currentTimeMillis()); 
		
		int currentHourOfDay = cal.get(Calendar.HOUR_OF_DAY), 
			currentMinutes = cal.get(Calendar.MINUTE);
		
		if (currentHourOfDay > hourOfDay || 
			(currentHourOfDay == hourOfDay && currentMinutes > minutes)) 
		{
			cal.add(Calendar.DAY_OF_YEAR, 1);
		}
		
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND,      0);
		cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
		cal.set(Calendar.MINUTE,      minutes);
		
		alarmTimestamp = cal.getTimeInMillis();
		
		mAlarmTimePref.setSummary(
			DateFormat.format(
					getResources().getString(R.string.date_format_alarm_time), 
					cal)
		);
	}

	@Override
	public boolean onPreferenceChange(Preference pref, Object newValue)
	{
		boolean allowChange = false;
		
		if (mAlarmEnabledPref == pref)
		{
			boolean enableAlarm = ((Boolean)newValue).booleanValue();
			
			if (enableAlarm) 
			{
				if (NO_ALARM_TIME_SET == alarmTimestamp) 
				{
					Toast.makeText(
						this, 
						getResources().getString(R.string.error_no_alarm_set), 
						Toast.LENGTH_SHORT
					).show();
				}
				else
				{		
					Toast.makeText(
						this, 
						getResources().getString(R.string.info_alarm_enabled), 
						Toast.LENGTH_SHORT
					).show();
					
					allowChange = true;
					
					Intent innerIntent = new Intent(
						android.content.Intent.ACTION_VIEW, 
						Uri.parse("http://www.google.com"));
					
					// required by PendingIntent.getActivity
					innerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					
					lastAlarmIntent = 
						PendingIntent.getActivity(
							this, 
							0, 
							innerIntent, 
							PendingIntent.FLAG_UPDATE_CURRENT);
					
					getAlarmManager().setRepeating(
						AlarmManager.RTC_WAKEUP, 
						alarmTimestamp, 
						AlarmManager.INTERVAL_DAY, 
						lastAlarmIntent);
				}
			} 
			else
			{
				getAlarmManager().cancel(lastAlarmIntent);
				
				allowChange = true;
				alarmTimestamp = NO_ALARM_TIME_SET;
			}
		}
		
		return allowChange;
	}

	private AlarmManager getAlarmManager()
	{
		return (AlarmManager)getSystemService(ALARM_SERVICE);
	}
}