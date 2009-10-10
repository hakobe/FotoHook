package jp.hakobe.android.fotohook;

import jp.hakobe.android.fotohook.R;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;

public class FotoHookSetting extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setPreferenceScreen(createPreference());
	}
	
	public static String getUserID(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getString("user_id", "");
	}
	public static String getPassword(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getString("password", "");
	}
	public static String getHookUrl(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getString("hook_url", "");
	}
	
	private PreferenceScreen createPreference() {
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        
        EditTextPreference userIdPref = new EditTextPreference(this);
        userIdPref.setDialogTitle(R.string.user_id);
        userIdPref.setKey("user_id");
        userIdPref.setTitle(R.string.user_id);
        root.addPreference(userIdPref);
        
        EditTextPreference passwordPref = new EditTextPreference(this);
        passwordPref.setDialogTitle(R.string.password);
        passwordPref.setKey("password");
        passwordPref.setTitle(R.string.password);

        EditText passwordEditText = passwordPref.getEditText();
        PasswordTransformationMethod transMethod = new PasswordTransformationMethod();
        passwordEditText.setTransformationMethod(transMethod);
        root.addPreference(passwordPref);

        EditTextPreference hookUrlPref = new EditTextPreference(this);
        hookUrlPref.setDialogTitle(R.string.hook_url);
        hookUrlPref.setKey("hook_url");
        hookUrlPref.setTitle(R.string.hook_url);
        root.addPreference(hookUrlPref);

        
        return root;
	}
}
	
