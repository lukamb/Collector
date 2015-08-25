package cz.collector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.FloatMath;

/**
 * Provadi sber dat ze senzoru zarizeni a jejich ukladani ve tvaru pro dalsi zpracovani.
 * Vzhledem k tomu, ze jednu instanci teto tridy vyuziva vice vlaken, je navic zajistena
 * synchronizace kritickych sekci v prislusnych metodach.
 * @author Lukas Ambroz
 */
public class SensorReader implements LocationListener, SensorEventListener {
	
	/** Nazev souboru pro ukladani dat */
	private final String FILE_NAME = "data.csv";
	/** Interval pro nacitani svetla (5s) */
	private final long LIGHT_INTERVAL = 5000000000L;
	/** Interval pro nacitani akcelerace (8ms) */
	private final long MOTION_INTERVAL = 8000000;
	
	/** Reference na rodicovskou sluzbu nebo aktivitu */
	private Context context;
	
	/** Zprava, pro kterou probiha sber dat */
	private String message;
	
	/** Objekt poskytujici sluzby spojene s polohou */
	private LocationManager locationManager;
	/** Aktualni poloha */
	private Location location = null;
	
	/** Objekt poskytujici sluzby spojene se senzory */
	private SensorManager sensorManager;
	/** Senzor svetla */
	private Sensor light;
	/** Prumer hodnot ziskanych ze senzoru svetla */
	private float lightAverage = 0.0F;
	/** Pocet hodnot ziskanych ze senzoru svetla */
	private int lightNum = 0;
	/** Cas posledniho nacteni svetla */
	private long lightLast = 0;
	/** Akcelerometr */
	private Sensor motion;
	/** Prumerna celkova akcelerace zarizeni */
	private float motionAverage = 0.0F;
	/** Pocet hodnot ziskanych z akcelerometru */
	private int motionNum = 0;
	/** Cas posledniho nacteni akcelerace */
	private long motionLast = 0;
	
	/**
	 * Konstruktor provadi nastaveni objektu pro nasledny sber dat ze senzoru.
	 * Registruje vsechny potrebne listenery apod.
	 * @param context Rodicovska sluzba nebo aktivita
	 * @param message Zprava, pro kterou probiha sber
	 */
	public SensorReader(Context context, String message) {
		this.context = context;
		this.message = message;
		
		// Registrace listeneru pro aktualizaci polohy
		locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20, 0, this);
		// Nacteni posledni zname polohy pred prichodem prvni aktualizace
		location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		
		// Registrace listeneru pro aktualizaci dat ze senzoru
		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		motion = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, motion, SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	/**
	 * Vola se pro radne zruseni objektu.
	 * Odstrani vsechny listenery apod.
	 */
	synchronized public void destroy() {
		// Odstraneni listeneru pro aktualizaci polohy
		locationManager.removeUpdates(this);
		
		// Odstraneni listeneru pro senzor svetla
		sensorManager.unregisterListener(this, light);
		// Odstraneni listeneru pro akcelerometr
		sensorManager.unregisterListener(this, motion);
	}
	
	/**
	 * Nastavi zpravu, pro kterou bude provaden sber
	 * @param message Zprava
	 */
	public void setMessage(String message) {
		if (!this.message.equals(message))
			this.message = message;
	}
	
	/**
	 * Vrati zemepisnou sirku
	 * @return Zemepisna sirka
	 */
	public double getLatitude() {
		if (location == null)
			return 0.0;
		
		return location.getLatitude();
	}
	
	/**
	 * Vrati zemepisnou delku
	 * @return Zemepisna delka
	 */
	public double getLongitude() {
		if (location == null)
			return 0.0;
		
		return location.getLongitude();
	}
	
	/**
	 * Vrati aktualne nastaveny zvukovy profil jako retezec (vibrate, silent, normal)
	 * @return Zvukovy profil
	 */
	public String getProfile() {
		AudioManager mgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		int mode = mgr.getRingerMode();
		
		if (mode == AudioManager.RINGER_MODE_VIBRATE)
			return "vibrate";
		if (mode == AudioManager.RINGER_MODE_SILENT)
			return "silent";
		
		return "normal";
	}
	
	/**
	 * Vrati hodinu dne
	 * @return Hodina
	 */
	public int getHour() {
		Time time = new Time();
		time.setToNow();
		
		return time.hour;
	}
	
	/**
	 * Vrati cast dne (morning, forenoon, afternoon, evening, night)
	 * @return Cast dne
	 */
	public String getDayPart() {
		int hour = getHour();
		
		if ((hour >= 6) && (hour < 10))
			return "morning";
		if ((hour >= 10) && (hour < 12))
			return "forenoon";
		if ((hour >= 12) && (hour < 18))
			return "afternoon";
		if ((hour >= 18) && (hour < 22))
			return "evening";
		
		return "night";
	}
	
	/**
	 * Vrati den v tydnu (mon, tue, wed, thu, fri, sat, sun)
	 * @return Den v tydnu
	 */
	public String getDay() {
		Time time = new Time();
		time.setToNow();
		int day = time.weekDay;
		
		switch (day) {
		case Time.MONDAY:
			return "mon";
		case Time.TUESDAY:
			return "tue";
		case Time.WEDNESDAY:
			return "wed";
		case Time.THURSDAY:
			return "thu";
		case Time.FRIDAY:
			return "fri";
		case Time.SATURDAY:
			return "sat";
		default:
			break;
		}
		
		return "sun";
	}
	
	/**
	 * Vrati, zda je vikendovy den (yes, no)
	 * @return yes nebo no
	 */
	public String getWeekend() {
		String day = getDay();
		
		if ("sat".equals(day) || "sun".equals(day))
			return "yes";
		
		return "no";
	}
	
	/**
	 * Vrati stav obrazovky (on, off)
	 * @return on nebo off
	 */
	public String getScreenState() {
		PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		boolean state = mgr.isScreenOn();
		
		if (state)
			return "on";
		
		return "off";
	}
	
	/**
	 * Vrati pocet bezicich aplikaci
	 * @return Pocet bezicich aplikaci
	 */
	public int getAppCount() {
		ActivityManager mgr = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> apps = mgr.getRunningAppProcesses();
		
		// Pokud je vracen null, nebezi zadna aplikace
		if (apps == null)
			return 0;
		
		return apps.size();
	}
	
	/**
	 * Vrati, zda jsou pripojena sluchatka (pres kabel nebo BT)
	 * @return yes nebo no
	 */
	public String getHeadset() {
		AudioManager mgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		
		if (mgr.isBluetoothA2dpOn() || mgr.isWiredHeadsetOn())
			return "yes";
		
		return "no";
	}
	
	/**
	 * Vrati, zda prave probiha telefonni hovor
	 * @return yes nebo no
	 */
	public String getCall() {
		TelephonyManager mgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		int state = mgr.getCallState();
		
		if (state == TelephonyManager.CALL_STATE_OFFHOOK)
			return "yes";
		
		return "no";
	}
	
	/**
	 * Vrati MAC adresu wifi AP, ke kteremu je telefon pripojen, nebo prazdny
	 * retezec
	 * @return MAC adresa
	 */
	public String getApMac() {
		WifiManager mgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo info = mgr.getConnectionInfo();
		String addr = null;
		
		if (info == null)
			return "";
		
		if ((addr = info.getBSSID()) == null)
			return "";
		
		return addr;
	}
	
	/**
	 * Vrati prumernou hodnotu akcelerace zarizeni od posledniho zavolani metody
	 * @return Akcelerace
	 */
	public float getMotion() {
		float result = motionAverage;
		
		// Vynulovani promennych pro vypocet prumeru
		motionAverage = 0.0F;
		motionNum = 0;
		
		return result;
	}
	
	/**
	 * Vrati prumernou uroven svetla od posledniho zavolani teto metody
	 * @return Uroven svetla
	 */
	public float getLightLevel() {
		float result = lightAverage;
		
		// Vynulovani promennych pro vypocet prumeru
		lightAverage = 0.0F;
		lightNum = 0;
		
		return result;
	}
	
	/**
	 * Vrati nastavenou zpravu
	 * @return Nastavena zprava
	 */
	public String getMessage() {
		return message;
	}
	
	/**
	 * Nacte data ze vsech senzoru a vrati je v jednom radku ve formatu CSV
	 * @return Nactena data ve formatu CSV
	 */
	public String getCsvLine() {
		String result = "";
		
		result += getLatitude() + ",";
		result += getLongitude() + ",";
		result += getProfile() + ",";
		result += getHour() + ",";
		result += getDayPart() + ",";
		result += getDay() + ",";
		result += getWeekend() + ",";
		result += getScreenState() + ",";
		result += getAppCount() + ",";
		result += getHeadset() + ",";
		result += getCall() + ",";
		result += getApMac() + ",";
		result += getMotion() + ",";
		result += getLightLevel() + ",";
		result += getMessage();
		
		return result;
	}
	
	/**
	 * Provede cteni dat ze vsech senzoru a ziskane hodnoty ulozi do vychoziho
	 * CSV souboru na SD karte
	 * @return true v pripade uspechu, jinak false
	 */
	synchronized public boolean storeCsvLine() {
		File file = new File(Environment.getExternalStorageDirectory(), FILE_NAME);
		FileWriter fw = null;
		PrintWriter out = null;
		
		try {
			// Soubor bude otevren pro pridavani
			fw = new FileWriter(file, true);
		} catch (IOException ioe) {
			return false;
		}
		
		// Otevreni souboru pro zapis s radkovym bufferovanim a zapis dat
		out = new PrintWriter(fw, false);
		out.println(getCsvLine());
		out.close();
		
		return true;
	}
	
	/**
	 * Metoda volana pri zmene lokace
	 */
	@Override
	public void onLocationChanged(Location arg0) {
		synchronized (this) {
			location = arg0;
		}
	}
	@Override
	public void onProviderDisabled(String arg0) {}
	@Override
	public void onProviderEnabled(String provider) {}
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	/**
	 * Callback metoda pro ziskani aktualnich dat ze senzoru
	 */
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// Pro senzor svetla
			if ((event.timestamp - lightLast) > LIGHT_INTERVAL) {
				lightLast = event.timestamp;
				
				// Vypocet prumeru inkrementalne
				synchronized (this) {
					lightAverage = lightAverage + ((event.values[0] - lightAverage) / ++lightNum);
				}
			}
		} else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			// Pro akcelerometr
			if ((event.timestamp - motionLast) > MOTION_INTERVAL) {
				motionLast = event.timestamp;
				
				// Vypocet vysledne akcelerace
				float magnitude = FloatMath.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
				
				// Vypocet prumeru inkrementalne
				synchronized (this) {
					motionAverage = motionAverage + ((magnitude - motionAverage) / ++motionNum);
				}
			}
		}
	}
	
}
