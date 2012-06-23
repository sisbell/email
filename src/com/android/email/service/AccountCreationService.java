package com.android.email.service;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.HostAuth;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

public class AccountCreationService extends IntentService {
   
	public static final String OPTIONS_VERSION = "version";
	
    public static final String OPTIONS_DISPLAY_NAME = "displayName";
    public static final String OPTIONS_USERNAME = "username";
    public static final String OPTIONS_PASSWORD = "password";    
	public static final String OPTIONS_EMAIL = "email";
	  
    public static final String OPTIONS_IN_LOGIN = "inLogin";//TODO: not used
    public static final String OPTIONS_IN_SERVER = "inServer";
	public static final String OPTIONS_IN_PORT = "inPort";
    public static final String OPTIONS_IN_SECURITY = "inSecurity";//TODO: not used
    
    public static final String OPTIONS_OUT_LOGIN = "outLogin";//TODO: not used
    public static final String OPTIONS_OUT_SERVER = "outServer";   
	public static final String OPTIONS_OUT_PORT= "outPort";
    public static final String OPTIONS_OUT_SECURITY = "outSecurity";//TODO: not used
    
    public static final String OPTIONS_SYNC_EMAIL = "syncEmail";//TODO: not used
    public static final String OPTIONS_SYNC_CALENDAR = "syncCalendar";//TODO: not used
    public static final String OPTIONS_SYNC_CONTACTS = "syncContacts";//TODO: not used
    
    public static final String OPTIONS_SERVICE_TYPE = "serviceType";//TODO: not used
    public static final String OPTIONS_DOMAIN = "domain";//TODO: not used
    
    private static final int HOST_AUTH_FLAGS = HostAuth.FLAG_SSL 
    		| HostAuth.FLAG_AUTHENTICATE | HostAuth.FLAG_TRUST_ALL;
    
	private static Bundle createTestBundle() {
		Bundle b = new Bundle();
		b.putString(OPTIONS_VERSION, "1.0");
		b.putString(OPTIONS_DISPLAY_NAME, "Sample Name");
		b.putString(OPTIONS_USERNAME, "User Name");
		b.putString(OPTIONS_PASSWORD, "password");
		b.putString(OPTIONS_EMAIL, "email@example.com");
		
		b.putString(OPTIONS_IN_SERVER, "imap.mail.yahoo.com");
		b.putInt(OPTIONS_IN_PORT , 993);

		b.putString(OPTIONS_OUT_SERVER, "smtp.mail.yahoo.com");
		b.putInt(OPTIONS_OUT_PORT , 465);
		
		return b;
	}
	
    private void setHostAuthRecvFromBundle(Account account, Bundle options) {
    	HostAuth receiveHostAuth = account.getOrCreateHostAuthRecv(getBaseContext());
    	receiveHostAuth.setLogin(options.getString(OPTIONS_EMAIL), options.getString(OPTIONS_PASSWORD));
    	receiveHostAuth.setConnection("imap", options.getString(OPTIONS_IN_SERVER), 
    			options.getInt(OPTIONS_IN_PORT), 
    			HOST_AUTH_FLAGS);
    }
    
    private void setHostAuthSendFromBundle(Account account, Bundle options) {
    	HostAuth hostAuth = account.getOrCreateHostAuthSend(getBaseContext());
    	hostAuth.setLogin(options.getString(OPTIONS_EMAIL), options.getString(OPTIONS_PASSWORD));
    	hostAuth.setConnection("smtp", options.getString(OPTIONS_OUT_SERVER), 
    			options.getInt(OPTIONS_OUT_PORT), 
    			HOST_AUTH_FLAGS);
    }
    
   private void setAccountFlags(Account account) {
        account.setFlags(
            Account.FLAGS_INCOMPLETE |
            Account.DELETE_POLICY_ON_DELETE << Account.FLAGS_DELETE_POLICY_SHIFT |
            Account.FLAGS_NOTIFY_NEW_MAIL);
    }
    
    private Account fromBundleToAccount(Bundle options) {
    	Account account = new Account();
    	account.mDisplayName = options.getString(OPTIONS_DISPLAY_NAME);
    	account.mEmailAddress = options.getString(OPTIONS_EMAIL);
    	
    	setAccountFlags(account);
    	setHostAuthRecvFromBundle(account, options);
    	setHostAuthSendFromBundle(account, options);
    	
    	return account;
    }
    
	public AccountCreationService() { 
		super("AccountCreationService");
	}
	
	public AccountCreationService(String name) {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {		
		Bundle options = intent.getExtras();
		if(options == null) {
			options = createTestBundle();
		}
		
		if(!isVersionProtocolSupported(options)) {
			return;//send error
		}
		
		Account account = fromBundleToAccount(options);
		account.save(getApplicationContext());
	}
	
	private static boolean isVersionProtocolSupported(Bundle options) {
		String version = options.getString(OPTIONS_VERSION, "0");
		return "1.0".equals(version);
	}
}
