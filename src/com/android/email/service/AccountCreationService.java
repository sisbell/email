package com.android.email.service;

import java.net.URISyntaxException;

import com.android.email.EmailAddressValidator;
import com.android.email.activity.setup.AccountSettingsUtils;
import com.android.email.activity.setup.AccountSettingsUtils.Provider;
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

	public static final String OPTIONS_IN_LOGIN = "inLogin";// TODO: not used
	public static final String OPTIONS_IN_SERVER = "inServer";
	public static final String OPTIONS_IN_PORT = "inPort";
	public static final String OPTIONS_IN_SECURITY = "inSecurity";// TODO: not
																	// used

	public static final String OPTIONS_OUT_LOGIN = "outLogin";// TODO: not used
	public static final String OPTIONS_OUT_SERVER = "outServer";
	public static final String OPTIONS_OUT_PORT = "outPort";
	public static final String OPTIONS_OUT_SECURITY = "outSecurity";// TODO: not
																	// used

	public static final String OPTIONS_SYNC_EMAIL = "syncEmail";// TODO: not
																// used
	public static final String OPTIONS_SYNC_CALENDAR = "syncCalendar";// TODO:
																		// not
																		// used
	public static final String OPTIONS_SYNC_CONTACTS = "syncContacts";// TODO:
																		// not
																		// used

	public static final String OPTIONS_SERVICE_TYPE = "serviceType";// TODO: not
																	// used
	public static final String OPTIONS_DOMAIN = "domain";// TODO: not used

	private static final int HOST_AUTH_FLAGS = HostAuth.FLAG_SSL
			| HostAuth.FLAG_AUTHENTICATE | HostAuth.FLAG_TRUST_ALL;

	private final EmailAddressValidator mEmailValidator = new EmailAddressValidator();

	private static Bundle createTestBundle() {
		Bundle b = new Bundle();
		b.putString(OPTIONS_VERSION, "1.0");
		b.putString(OPTIONS_DISPLAY_NAME, "Sample Name 3");
		b.putString(OPTIONS_PASSWORD, "password");
		b.putString(OPTIONS_EMAIL, "email@example.com");

		return b;
	}

	private void setHostAuthRecvFromBundle(Account account, Bundle options) {
		HostAuth receiveHostAuth = account
				.getOrCreateHostAuthRecv(getBaseContext());
		receiveHostAuth.setLogin(options.getString(OPTIONS_EMAIL),
				options.getString(OPTIONS_PASSWORD));
		receiveHostAuth.setConnection("imap",
				options.getString(OPTIONS_IN_SERVER),
				options.getInt(OPTIONS_IN_PORT), HOST_AUTH_FLAGS);
	}

	private void setHostAuthSendFromBundle(Account account, Bundle options) {
		HostAuth hostAuth = account.getOrCreateHostAuthSend(getBaseContext());
		hostAuth.setLogin(options.getString(OPTIONS_EMAIL),
				options.getString(OPTIONS_PASSWORD));
		hostAuth.setConnection("smtp", options.getString(OPTIONS_OUT_SERVER),
				options.getInt(OPTIONS_OUT_PORT), HOST_AUTH_FLAGS);
	}

	private void setAccountFlags(Account account) {
		account.setFlags(Account.FLAGS_INCOMPLETE
				| Account.DELETE_POLICY_ON_DELETE << Account.FLAGS_DELETE_POLICY_SHIFT
				| Account.FLAGS_NOTIFY_NEW_MAIL);
	}

	private Account fromBundleToAccount(Bundle options) {
		Account account = new Account();
		account.mDisplayName = options.getString(OPTIONS_DISPLAY_NAME);
		account.mEmailAddress = options.getString(OPTIONS_EMAIL);

		setAccountFlags(account);

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
		if (options == null) {
			options = createTestBundle();
		}

		if (!isVersionProtocolSupported(options)) {
			return;// send error
		}

		String email = options.getString(OPTIONS_EMAIL);
		if (!mEmailValidator.isValid(email)) {
			return;// send error
		}
		
		Account account = fromBundleToAccount(options);
		
		String[] emailParts = email.split("@");
		String domain = emailParts[1].trim();
		Provider provider = AccountSettingsUtils.findProviderForDomain(this,
				domain);
		if(provider != null) {
			provider.expandTemplates(email);
		}
		
		try {
			if(hasReceiveAuthOptions(options) ) {
				setHostAuthRecvFromBundle(account, options);
			} else if(provider != null) {
				HostAuth recvAuth = account.getOrCreateHostAuthRecv(this);
				HostAuth.setHostAuthFromString(recvAuth, provider.incomingUri);
				recvAuth.setLogin(provider.incomingUsername, options.getString(OPTIONS_PASSWORD));			
			} else {
				return;//TODO: error
			}
			
			if(hasSendAuthOptions(options) ) {
				setHostAuthSendFromBundle(account, options);
			} else if(provider != null) {
				HostAuth sendAuth = account.getOrCreateHostAuthSend(this);
				HostAuth.setHostAuthFromString(sendAuth, provider.outgoingUri);
				sendAuth.setLogin(provider.outgoingUsername, options.getString(OPTIONS_PASSWORD));	
			} else {
				return;//TODO: error
			}
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return;//TODO error
		}
		
		account.save(getApplicationContext());
	}
	
	private static boolean hasReceiveAuthOptions(Bundle options) {
		return (options.containsKey(OPTIONS_IN_PORT) && options.containsKey(OPTIONS_IN_SERVER) 
				&& options.containsKey(OPTIONS_IN_SECURITY) );
	}

	private static boolean hasSendAuthOptions(Bundle options) {
		return (options.containsKey(OPTIONS_OUT_PORT) && options.containsKey(OPTIONS_OUT_SERVER) 
				&& options.containsKey(OPTIONS_OUT_SECURITY) );
	}
	
	private static boolean isVersionProtocolSupported(Bundle options) {
		String version = options.getString(OPTIONS_VERSION, "0");
		return "1.0".equals(version);
	}
}
