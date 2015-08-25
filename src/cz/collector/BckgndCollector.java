package cz.collector;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

/**
 * Sluzba, ktera na pozadi pravidelne sbira data ze senzoru. Pro pravidelny
 * sber je pouzit casovac, jehoz ukoly provadi nacteni a ulozeni ziskanych
 * informaci.
 * @author Lukas Ambroz
 */
public class BckgndCollector extends Service {
	
	/** Identifikator notifikace informujici o behu sluzby */
	private final int NOTIFICATION_ID = 1;
	/** Doba mezi spustenim casovace a prvnim ukolem */
	private final int TIMER_DELAY = 30000;
	/** Interval mezi ukoly */
	private final int TIMER_PERIOD = 30000;
	
	/** Priznak indikuje bezici sber dat (nikoli sluzbu jako takovou) */
	private boolean isRunning = false;
	
	/** Zajistuje ukladani dat */
	private SensorReader reader = null;
	/** Casovac pro spusteni periodicky se opakujicich ukolu */
	private Timer timer;
	/** Zajistuje beh sluzby i po vypnuti obrazovky zarizeni */
	private WakeLock wakeLock;
	
	/** Binder pro pripojeni klienta ke sluzbe */
	private IBinder binder = new LocalBinder();
	
	/**
	 * Ukol spousteny casovacem pro nacteni a ulozeni dat ze senzoru
	 */
	public class CollectorTask extends TimerTask {
		public void run() {
			reader.storeCsvLine();
		}
	}
	
	/**
	 * Binder pro pripojeni klienta ke sluzbe
	 */
	public class LocalBinder extends Binder {
		BckgndCollector getService() {
			return BckgndCollector.this;
		}
	}
	
	/**
	 * Vola se pri navazovani sluzby ke klientovi
	 */
	@Override
	public IBinder onBind(Intent intent) {
		wakeLock.acquire();
		
		return binder;
	}
	
	/**
	 * Vola se pri vytvoreni sluzby pro inicializaci jejich soucasti
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		
		// Beh na popredi, aby ji system nemohl ukoncit pri nedostatku pameti
		startForeground(NOTIFICATION_ID, createNotification());
		
		timer = new Timer();
		
		PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Collector");
	}
	
	/**
	 * Vola se pri vytvoreni sluzby pomoci metody startService()
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	/**
	 * Vola se pri ukonceni sluzby pro korektni odstraneni jejich soucasti
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		isRunning = false;
		wakeLock.release();
		timer.cancel();
		reader.destroy();
	}
	
	/**
	 * Vola klient pro zahajeni sberu dat pro zadanou zpravu
	 * @param msg Zprava, pro kterou maji byt sbirana data
	 */
	public void startCollecting(String msg) {
		if (isRunning)
			return;
		
		isRunning = true;
		wakeLock.acquire();
		reader = new SensorReader(this, msg);
		timer.schedule(new CollectorTask(), TIMER_DELAY, TIMER_PERIOD);
	}
	
	/**
	 * Provede zmenu zpravy, pro kterou jsou sbirana data
	 * @param msg Nova zprava
	 */
	public void setMsg(String msg) {
		if (!isRunning)
			return;
		
		reader.setMessage(msg);
	}
	
	/**
	 * Vrati zpravu, pro kterou se sbiraji data
	 * @return Zprava
	 */
	public String getMsg() {
		return reader.getMessage();
	}
	
	/**
	 * Vytvori a vrati notifikaci, ktera je zobrazena behem aktivniho sberu dat
	 * @return Notifikace
	 */
	private Notification createNotification() {
		int icon = R.drawable.ic_stat_collecting;
		String tickerText = getString(R.string.notificationText);
		long when = System.currentTimeMillis();
		
		Notification notification = new Notification(icon, tickerText, when);
		
		Context context = getApplicationContext();
		String contentTitle = getString(R.string.app_name);
		String contentText = getString(R.string.notificationText);
		Intent notificationIntent = new Intent(this, Collector.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		
		return notification;
	}
	
}
