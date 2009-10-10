package jp.hakobe.android.fotohook;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.hakobe.android.fotohook.IFotoHookPostService;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class FotoHookPostService extends Service {

	private static final String TAG = "FotoHookPostService";
	private NotificationManager notificationManager;

	@Override
	public IBinder onBind(Intent intent) {
        if(IFotoHookPostService.class.getName().equals(intent.getAction())){
            return fotoHookPostService;
        }
		return null;
	}	
	
	@Override
	public void onCreate() {
		this.notificationManager = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
	}
	
	@Override
	public void onLowMemory() {
		super.onLowMemory();
		Log.d(TAG, "Uhhps low memory.");
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		this.closeNotification();
		Log.d(TAG, "killed");
	}
	
	private IFotoHookPostService.Stub fotoHookPostService = new IFotoHookPostService.Stub() {
		public void post(Uri uri, String title) {
			String user_id = FotoHookSetting.getUserID(FotoHookPostService.this);
			String password = FotoHookSetting.getPassword(FotoHookPostService.this);
			String hookUrl = FotoHookSetting.getHookUrl(FotoHookPostService.this);
			
			if (!this.validate(user_id, password)) {
				return;
			}
			
			FotolifeAPI f = new FotolifeAPI(FotoHookPostService.this, user_id, password);
			f.post(uri, title, hookUrl);
		}
		
		private boolean validate(String user_id, String password) {
			return (user_id != null && user_id.length() > 0 && password != null && password.length() > 0);
		}
	};
	
	public void showNotification(int iconID, String ticker, String title, String message) {

		Notification notification = new Notification(iconID, ticker, System.currentTimeMillis());
        PendingIntent intent=PendingIntent.getActivity(this, 0, null, 0);
        notification.setLatestEventInfo(this, title, message, intent);
        
        this.notificationManager.cancel(0);
        this.notificationManager.notify(0, notification);
    }
	
	public void closeNotification() {
		this.notificationManager.cancelAll();
	}
}

class FotolifeAPI implements Runnable {
	
	private final static String TAG = "FotolifeAPI";
	
	private final FotoHookPostService context;
	private final String user_id;
	private final String password;
	
	private boolean result = false;

	private final Handler handler = new Handler();
	
	public FotolifeAPI(FotoHookPostService context, String user_id, String password) {
		this.context = context;
		this.user_id = user_id;
		this.password = password;
	}
	
	public boolean isSuccess() {
		return this.result; 
	}
	
	public Service getService() {
		return this.context;
	}
	
	public void setResult(boolean result) {
		this.result = result;
	}
		
	public void post(Uri uri, String title, String hookUrl) {
		this.context.showNotification(R.drawable.icon, "FotoHook: Uploading ...", "FotoHook", "Uploading Images ...");
		Thread action = new FotolifePostAction(this, this.handler, this.user_id, this.password, uri, title, hookUrl);
		action.start();
	}
	
	public void run() { 
		this.onPostFinish();
	}
	
	private void onPostFinish() {
		if (this.isSuccess()) {
			Log.i(TAG, "Upload Success.");
			Toast.makeText(this.context, "FotoHook: Upload success", Toast.LENGTH_LONG * 2).show();
		}
		else {
			Log.i(TAG, "Upload Error.");
			Toast.makeText(this.context, "FotoHook: Upload error", Toast.LENGTH_LONG * 2).show();
		}
		this.context.closeNotification();
		this.context.stopSelf();		
	}
}

class FotolifePostAction extends Thread {
	
	private final static String TAG = "FotolifePostAction";
	
	private final Uri uri;
	private final String user_id;
	private final String password;
	private final String title;
	private final String hookUrl;
	private final Handler handler;
	private final FotolifeAPI listener;
	
	private final HttpClient client = new DefaultHttpClient();
	
	public FotolifePostAction(
			FotolifeAPI listener, Handler handler, String user_id, String password, Uri uri, String title, String hookUrl) {
		this.listener = listener;
		this.handler = handler;
		this.user_id = user_id;
		this.password = password;
		this.uri = uri;
		this.title = title;
		this.hookUrl = hookUrl;
	}
	
	public void run() {
		HashMap<String, String> result = null;
		try {
			result = this.postFoto(uri, user_id, password, title);
			this.runHook(hookUrl, result, title);
			this.listener.setResult(true);
		}
		catch (Exception e) {
			e.printStackTrace();
			this.listener.setResult(false);
		}	
		finally {
			this.handler.post(listener); 
		}
	}
	
	private HashMap<String, String> postFoto(
			Uri uri,
			String user_id, 
			String password, 
			String title
		) throws ClientProtocolException, IOException {
		
		HttpPost req = new HttpPost("http://f.hatena.ne.jp/atom/post");
		req.addHeader("X-WSSE", FotoHookUtils.createWsseHeader(user_id, password));
		req.setEntity(createPostXMLEntity(uri, title));
		
		HttpResponse res = client.execute(req);
		int statusCode = res.getStatusLine().getStatusCode();
		if (statusCode != HttpStatus.SC_CREATED) {
			req.abort();
			throw new HttpResponseException(statusCode, "Failed to doPost: " + new Integer(statusCode).toString());
		}

		return parseResultXML(res.getEntity().getContent());		
	}
	
	private void runHook(
			String hookUrl, 
			HashMap<String, String> params, 
			String title
		) throws ClientProtocolException, IOException, URISyntaxException {
		
		if (hookUrl.length() <= 0) {return;}
		HttpGet req = new HttpGet(
				new URI(hookUrl+"?imageurl="+URLEncoder.encode(params.get("url"), "UTF-8")+"&title="+URLEncoder.encode(title, "UTF-8")+"&hatenasyntax="+URLEncoder.encode(params.get("hatenasyntax"), "UTF-8")));	
		
		HttpResponse res = client.execute(req);
		int statusCode = res.getStatusLine().getStatusCode();
		if (statusCode != HttpStatus.SC_OK) {
			req.abort();
			throw new HttpResponseException(statusCode, "Failed to runHook: " + new Integer(statusCode).toString());
		}
	}
	
	private HttpEntity createPostXMLEntity(Uri uri, String title) {
		ByteArrayOutputStream resultOutput = new ByteArrayOutputStream();
		try {
			resultOutput.write(
					("<entry xmlns=\"http://purl.org/atom/ns#\"><title>" 
					+ title 
					+ "</title><content mode=\"base64\" type=\"image/jpeg\">").getBytes());
			
			byte[] image = this.loadImageToBytes(uri);
			resultOutput.write(Base64.encodeBase64(image));
			
			resultOutput.write("</content></entry>".getBytes());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		HttpEntity entity = new ByteArrayEntity(resultOutput.toByteArray());
		return entity;
	}
	
	private byte[] loadImageToBytes(Uri uri) throws IOException {
		InputStream input = this.listener.getService().getContentResolver().openInputStream(uri);
		
		ByteArrayOutputStream output = this.inputToByteArrayOutputStream(input);
		byte[] image;
		image = output.toByteArray();
		
		return image;
	}
	
	private ByteArrayOutputStream inputToByteArrayOutputStream(InputStream input) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		FotoHookUtils.loadInputStreamToOutputStream(input, output);
		return output;
	}
		
	private HashMap<String, String> parseResultXML(InputStream input) {
		HashMap<String, String> result = new HashMap<String, String>();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		String data;
		try {
			FotoHookUtils.loadInputStreamToOutputStream(input, output);
			data = output.toString();
		}
		catch (Exception e) {
			return result;
		}

		Pattern pattern = Pattern.compile("http://f\\.hatena\\.ne\\.jp/[^\"]+");
		Matcher m = pattern.matcher(data);
		if ( m.find() ) {
			result.put("url", m.group(0));
		}
		
		pattern = Pattern.compile("f:[^<]+");
		m = pattern.matcher(data);
		if ( m.find() ) {
			result.put("hatenasyntax", m.group(0));
		}
		return result;
	}
}
