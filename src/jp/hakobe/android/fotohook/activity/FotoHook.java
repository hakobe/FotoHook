package jp.hakobe.android.fotohook.activity;

import java.io.ByteArrayOutputStream;
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
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FotoHook extends Activity implements DialogInterface.OnClickListener {

	private String user_id = null;
	private String password = null;
	private String hookUrl = null;
	
	private EditText title_edit_text = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main);
        
    	this.user_id = FotoHookSetting.getUserID(this);
    	this.password = FotoHookSetting.getPassword(this);
    	this.hookUrl = FotoHookSetting.getHookUrl(this);    	
    	
    	LinearLayout layout = new LinearLayout(this);
    	layout.setOrientation(LinearLayout.VERTICAL);
    	
    	TextView title_text_view = new TextView(this);
    	title_text_view.setText(R.string.title);
    	layout.addView(title_text_view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    	
    	this.title_edit_text = new EditText(this);
    	layout.addView(title_edit_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    	
    	new AlertDialog.Builder(this)
    		.setTitle(R.string.app_name)
    		.setView(layout)
			.setPositiveButton(R.string.submit, this)
			.setNegativeButton(R.string.cancel, this)
			.create()
			.show();
        
    	//submit_button.setOnClickListener(this);
    }

	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			String title = this.title_edit_text.getText().toString();
			FotolifeAPI f = new FotolifeAPI(this, this.user_id, this.password);
			f.post(new IntentImage(this).getStream(), title, this.hookUrl);
		}
		else {
			this.finish();
		}
	}
}

class IntentImage {
	private Activity context = null;
	public IntentImage(Activity context) {
		this.context = context;
	}
	
	public InputStream getStream() {
    	InputStream result = null;
        Uri uri = this.context.getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        try {
        	InputStream input = this.context.getContentResolver().openInputStream(uri);
        	result = input;
        }
        catch (Exception e) {
        	e.printStackTrace();
        }
        return result;
	}
}

class FotolifeAPI implements Runnable {
	private final Activity context;
	private final String user_id;
	private final String password;

	private final Handler handler = new Handler();
	private FotolifePostAction action = null;
	
	public FotolifeAPI(Activity context, String user_id, String password) {
		this.context = context;
		this.user_id = user_id;
		this.password = password;
	}
	
	public void post(InputStream input, String title, String hookUrl) {
		this.action = new FotolifePostAction(this, this.handler, this.user_id, this.password, input, title, hookUrl);
		this.action.start();
		//this.context.finish();
	}
	
	public void run() { // FIXME 上位のクラスでやる
		if (this.action.isSuccess()) {
			// アップロードに成功しました的
			Toast.makeText(this.context, "FotoHook: Upload success", Toast.LENGTH_LONG * 2).show();
		}
		else {
			// エラーです的
			Toast.makeText(this.context, "FotoHook: Upload error", Toast.LENGTH_LONG * 2).show();
		}
	}
}

class FotolifePostAction extends Thread {
	private final InputStream input;
	private final String user_id;
	private final String password;
	private final String title;
	private final String hookUrl;
	private final Handler handler;
	private final Runnable listener;
	
	private final HttpClient client = new DefaultHttpClient();
	private boolean result = false;
	
	public FotolifePostAction(
			Runnable listener, Handler handler, String user_id, String password, InputStream input, String title, String hookUrl) {
		this.listener = listener;
		this.handler = handler;
		this.user_id = user_id;
		this.password = password;
		this.input = input;
		this.title = title;
		this.hookUrl = hookUrl;
	}
	
	@Override
	public void run() {
		String imageUrl = "";
		try {
			imageUrl = this.doPost(input, user_id, password, title);
			this.runHook(hookUrl, imageUrl, title);
			this.setResult(true);
		}
		catch (Exception e) {
			e.printStackTrace();
			this.setResult(false);
		}	
		finally {
			this.handler.post(listener);
		}
	}
	
	public boolean isSuccess() {
		return this.result; 
	}
	
	private void setResult(boolean result) {
		this.result = result;
	}

	private String doPost(InputStream input, String user_id, String password, String title) throws ClientProtocolException, IOException {
		HttpPost req = new HttpPost("http://f.hatena.ne.jp/atom/post");
		req.addHeader("X-WSSE", getWsseHeaderValue(user_id, password));
		req.setEntity(getPostXMLEntity(input, title));
		
		HttpResponse res = client.execute(req);
		int statusCode = res.getStatusLine().getStatusCode();
		if (statusCode != HttpStatus.SC_CREATED) {
			req.abort();
			throw new HttpResponseException(statusCode, "Failed to doPost: " + new Integer(statusCode).toString());
		}

		// TODO 作成した画像のidを取得する
		String imageurl = parseResultXML(res.getEntity().getContent());
		
		return imageurl;
	}
	
	private void runHook(String hookUrl, String imageUrl, String title) throws ClientProtocolException, IOException, URISyntaxException {
		if (hookUrl.length() <= 0) {return;}
		HttpGet req = new HttpGet(new URI(hookUrl+"?imageurl="+URLEncoder.encode(imageUrl, "UTF-8")+"&title="+URLEncoder.encode(title, "UTF-8")));	
		
		HttpResponse res = client.execute(req);
		int statusCode = res.getStatusLine().getStatusCode();
		if (statusCode != HttpStatus.SC_OK) {
			req.abort();
			throw new HttpResponseException(statusCode, "Failed to runHook: " + new Integer(statusCode).toString());
		}

	}
	
	private HttpEntity getPostXMLEntity(InputStream imageInput, String title) {
		ByteArrayOutputStream resultOutput = new ByteArrayOutputStream();
		try {
			resultOutput.write(("<entry xmlns=\"http://purl.org/atom/ns#\"><title>" + title + "</title><content mode=\"base64\" type=\"image/jpeg\">").getBytes());
			
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			int BUFFER_SIZE = 1024 * 4; // TODO きちんとした定数にする
			byte[] buffer = new byte[BUFFER_SIZE];
			int n = 0;
			while (-1 != (n = imageInput.read(buffer))) {
				output.write(buffer, 0, n);
			}
			resultOutput.write(Base64.encodeBase64(output.toByteArray()));
			
			resultOutput.write("</content></entry>".getBytes());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		HttpEntity entity = new ByteArrayEntity(resultOutput.toByteArray());
		return entity;
	}
	
	private final String getWsseHeaderValue(String username, String password) {
		try {
			byte[] nonceB = new byte[8];
			SecureRandom.getInstance("SHA1PRNG").nextBytes(nonceB);
			
			SimpleDateFormat zulu = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			zulu.setTimeZone(TimeZone.getTimeZone("GMT"));
			Calendar now = Calendar.getInstance();
			now.setTimeInMillis(System.currentTimeMillis());
			String created = zulu.format(now.getTime());
			byte[] createdB = created.getBytes("utf-8");
			byte[] passwordB = password.getBytes("utf-8");
			
			byte[] v = new byte[nonceB.length + createdB.length + passwordB.length];
			System.arraycopy(nonceB, 0, v, 0, nonceB.length);
			System.arraycopy(createdB, 0, v, nonceB.length, createdB.length);
			System.arraycopy(passwordB, 0, v, nonceB.length + createdB.length,
			                 passwordB.length);
			
			MessageDigest md = MessageDigest.getInstance("SHA1");
			md.update(v);
			byte[] digest = md.digest();
			
			StringBuffer buf = new StringBuffer();
			buf.append("UsernameToken Username=\"");
			buf.append(username);
			buf.append("\", PasswordDigest=\"");
			buf.append(new String(Base64.encodeBase64(digest)));
			buf.append("\", Nonce=\"");
			buf.append(new String(Base64.encodeBase64(nonceB)));
			buf.append("\", Created=\"");
			buf.append(created);
			buf.append('"');
			return buf.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private String parseResultXML(InputStream input) {
		String result = "";
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			int BUFFER_SIZE = 1024 * 4; // TODO きちんとした定数にする
			byte[] buffer = new byte[BUFFER_SIZE];
			int n = 0;
			while (-1 != (n = input.read(buffer))) {
				output.write(buffer, 0, n);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		Pattern pattern = Pattern.compile("http://f\\.hatena\\.ne\\.jp/[^\"]+");
		Matcher m = pattern.matcher(output.toString());
		if ( m.find() ) {
			result = m.group(0);
		}
		return result;
	}
}

