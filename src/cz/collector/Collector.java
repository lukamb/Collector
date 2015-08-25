package cz.collector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Hlavni aktivita aplikace. Zajistuje zakladni funkcionalitu krome sberu dat,
 * ktery je odsud pouze rizen prostrednictvim spousteni prislusne sluzby.
 * @author Lukas Ambroz
 */
public class Collector extends Activity {
	
	/** Soubor pro ulozeni vytvorenych zprav */
	private final String MSG_FILE_NAME = "messages.csv";
	/** Identifikator dialogu pro zadani nove zpravy */
	private final int NEW_MSG_DIALOG = 0;
	
	/** Adapter obsahujici vytvorene zpravy */
	private ArrayAdapter<String> msgs;
	
	/** Nahled pro zobrazeni aktualne nastavene zpravy */
	private TextView msg;
	/** Rozbalovaci menu pro vyber zpravy */
	private Spinner msgSpinner;
	
	/** Sluzba pro sber dat na pozadi */
	private BckgndCollector boundCollector;
	/** Spojeni se sluzbou pro sber dat */
	private ServiceConnection mConnection = new ServiceConnection() {
		// Vola se po spojeni, vraci objekt sluzby, se kterym jiz lze pracovat
		public void onServiceConnected(ComponentName className, IBinder service) {
			boundCollector = ((BckgndCollector.LocalBinder)service).getService();
			boundCollector.startCollecting(msg.getText().toString());
			msg.setText(boundCollector.getMsg());
		}
		
		// Vola se pri necekanem ukonceni procesu sluzby
		public void onServiceDisconnected(ComponentName className) {
			boundCollector = null;
		}
	};
	
	/**
	 * Nacte z vychoziho souboru vytvorene zpravy
	 * @return Seznam nactenych zprav
	 */
	private List<String> loadMsgs() {
		File file = new File(Environment.getExternalStorageDirectory(), MSG_FILE_NAME);
		List<String> result = new ArrayList<String>();
		
		if (!file.exists())
			return result;
		
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(file));
			String line = null;
			
			while ((line = in.readLine()) != null)
				result.add(line.replaceFirst("^[0-9]+;", ""));
		} catch (IOException ioe) {
			result.clear();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException io) {
					result.clear();
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Vola se pri vytvoreni aktivity pro inicializaci uzivatelskeho rozhrani
	 * a nacteni potrebnych dat
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		// Vytvoreni ArrayAdapteru a nastaveni aktualizace msgSpinner pri zmene
		msgs = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, loadMsgs());
		msgs.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		msgs.setNotifyOnChange(true);
		
		// Provazani nahledu pro zobrazeni aktualne nastavene zpravy
		msg = (TextView) findViewById(R.id.msgTextView);
		
		// Navazani a nastaveni rozbalovaciho menu
		msgSpinner = (Spinner) findViewById(R.id.msgSpinner);
		msgSpinner.setAdapter(msgs);
		if (msgs.isEmpty())
			msgSpinner.setEnabled(false);
	}
	
	/**
	 * Vola se pri startu aktivity (po jejim vytvoreni nebo po navratu na jiz
	 * vytvorenou aktivitu) pro pripadne pripojeni ke sluzbe zajistujici sber
	 * dat
	 */
	@Override
	protected void onStart() {
		super.onStart();
		
		// Pokud bezi sber dat, provede se pripojeni ke sluzbe, kdy se zaroven
		// v GUI nastavi aktualne pouzita zprava pro sber dat
		if (isRunning())
			bindService(new Intent(this, BckgndCollector.class), mConnection, 0);
	}
	
	/**
	 * Vola se pri opusteni aktivity, kdy se provede odpojeni od sluzby
	 * zajistujici sber dat
	 */
	@Override
	protected void onStop() {
		super.onStop();
		
		if (isRunning())
			unbindService(mConnection);
	}
	
	/**
	 * Prida novou zpravu do seznamu a ulozi ji do souboru
	 * @param text Nova zprava
	 * @return true v pripade uspechu, jinak false
	 */
	private boolean addMsg(String text) {
		// Overeni zadane zpravy
		if (!checkMessage(text))
			return false;
		
		// Pridani do souboru
		File file = new File(Environment.getExternalStorageDirectory(), MSG_FILE_NAME);
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
		out.println(msgs.getCount() + ";" + text);
		out.close();
		
		// Pridani do seznamu
		msgs.add(text);
		
		if (!msgSpinner.isEnabled())
			msgSpinner.setEnabled(true);
		
		return true;
	}
	
	/**
	 * Vytvori a vrati dialog pro zadani nove zpravy
	 * @return Vytvoreny dialog
	 */
	private AlertDialog createNewMsgDialog() {
		// Textove pole v dialogu
		final EditText input = new EditText(this);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.dialogMsg)
			   .setCancelable(true)
			   .setView(input)
			   .setPositiveButton(R.string.okBtnLabel, new DialogInterface.OnClickListener() {
				   @Override
				   public void onClick(DialogInterface dialog, int which) {
					   // Pridani zpravy, v pripade chybne zadaneho retezce se ignoruje
					   addMsg(input.getText().toString());
					   
					   removeDialog(NEW_MSG_DIALOG);
				   }
			   })
			   .setNegativeButton(R.string.cancelBtnLabel, new DialogInterface.OnClickListener() {
				   @Override
				   public void onClick(DialogInterface dialog, int which) {
					   removeDialog(NEW_MSG_DIALOG);
				   }
			   });
		
		return builder.create();
	}
	
	/**
	 * Vola se pro zobrazeni dialogu se zadanym ID
	 */
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		
		if (id == NEW_MSG_DIALOG)
			dialog = createNewMsgDialog();
		
		return dialog;
	}
	
	/**
	 * Vola se po stisknuti tlacitka Run pro spusteni sberu dat, pokud je
	 * nastavena zprava
	 */
	public void onRunBtnClick(View v) {
		if (!isRunning()) {
			if (msg.length() == 0)
				return;
			
			runCollector();
		}
	}
	
	/**
	 * Vola se po stisknuti tlacitka Stop pro zastaveni sberu
	 */
	public void onStopBtnClick(View v) {
		if (isRunning())
			stopCollector();
	}
	
	/**
	 * Vola se po stisknuti tlacitka New, ktere vyvola dialog pro zadani
	 * nove zpravy
	 */
	public void onNewBtnClick(View v) {
		showDialog(NEW_MSG_DIALOG);
	}
	
	/**
	 * Vola se po stisknuti tlacitka Set, kterym se nastavi zprava vybrana
	 * v rozbalovacim menu pro sber
	 */
	public void onSetBtnClick(View v) {
		int pos = msgSpinner.getSelectedItemPosition();
		
		if (pos == AdapterView.INVALID_POSITION)
			return;
		
		msg.setText(msgs.getItem(pos));
		
		if (isRunning())
			boundCollector.setMsg(msg.getText().toString());
	}
	
	/**
	 * Spusti sluzbu pro sber dat
	 */
	private void runCollector() {
		startService(new Intent(this, BckgndCollector.class));
		bindService(new Intent(this, BckgndCollector.class), mConnection, 0);
	}
	
	/**
	 * Zastavi sluzbu pro sber dat
	 */
	private void stopCollector() {
		unbindService(mConnection);
		stopService(new Intent(this, BckgndCollector.class));
	}
	
	/**
	 * Overi spravnost zadane zpravy
	 * @param message Zprava pro overeni
	 * @return true v pripade, ze je zprava spravne zadana
	 */
	private boolean checkMessage(String message) {
		if (message.matches("\\A[_\\-a-z0-9]+\\z"))
			return true;
		
		return false;
	}
	
	/**
	 * Vrati, zda na pozadi bezi sluzba pro sber dat
	 * @return true v pripade, ze je sluzba spustena
	 */
	private boolean isRunning() {
		// Projde seznam spustenych sluzeb, zda obsahuje danou sluzbu
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if ("cz.collector.BckgndCollector".equals(service.service.getClassName()))
				return true;
		}
		
		return false;
	}
	
}
