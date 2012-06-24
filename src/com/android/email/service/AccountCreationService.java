package com.android.email.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import com.android.email.EmailAddressValidator;
import com.android.email.activity.setup.AccountSettingsUtils;
import com.android.email.activity.setup.AccountSettingsUtils.Provider;
import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.HostAuth;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.ResultReceiver;

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

	public static final String OPTIONS_EMAIL_SYNC_ENABLED = "syncEmail";
	public static final String OPTIONS_CALENDAR_SYNC_ENABLED = "syncCalendar";
	public static final String OPTIONS_CONTACTS_SYNC_ENABLED = "syncContacts";
	public static final String OPTIONS_SERVICE_TYPE = "serviceType";// TODO: not
																	// used
	public static final String OPTIONS_DOMAIN = "domain";// TODO: not used

	/*
	 * Account creation results
	 */
	public static final String RESULT_INVALID_VERSION = "RESULT_INVALID_VERSION";

	public static final String RESULT_EMAIL_ADDRESS_MALFORMED = "EMAIL_ADDRESS_MALFORMED";

	public static final String RESULT_INVALID_HOST = "RESULT_INVALID_HOST";

	public static final String RESULT_UNKNOWN = "RESULT_UNKNOWN";

	public static int RESULT_CODE_SUCCESS = 0x0;

	public static int RESULT_CODE_FAILURE = 0x1;

	private static final int HOST_AUTH_FLAGS = HostAuth.FLAG_SSL
			| HostAuth.FLAG_AUTHENTICATE | HostAuth.FLAG_TRUST_ALL;

	private final EmailAddressValidator mEmailValidator = new EmailAddressValidator();

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

		String email = options.getString(OPTIONS_EMAIL);
		String password = options.getString(OPTIONS_PASSWORD);

		if (!isVersionProtocolSupported(options)) {
			sendResult(email, RESULT_CODE_FAILURE, RESULT_INVALID_VERSION);
			return;
		}

		if (!mEmailValidator.isValid(email)) {
			sendResult(email, RESULT_CODE_FAILURE,
					RESULT_EMAIL_ADDRESS_MALFORMED);
			return;
		}

		Account account = fromBundleToAccount(options);

		String[] emailParts = email.split("@");
		String domain = emailParts[1].trim();
		Provider provider = AccountSettingsUtils.findProviderForDomain(this,
				domain);
		if (provider != null) {
			provider.expandTemplates(email);
		}

		try {
			if (hasReceiveAuthOptions(options)) {
				setHostAuthRecvFromBundle(account, options);
			} else if (provider != null) {
				HostAuth recvAuth = account.getOrCreateHostAuthRecv(this);
				HostAuth.setHostAuthFromString(recvAuth, provider.incomingUri);
				recvAuth.setLogin(provider.incomingUsername, password);
			} else {
				sendResult(email, RESULT_CODE_FAILURE, RESULT_INVALID_HOST);
				return;
			}

			if (hasSendAuthOptions(options)) {
				setHostAuthSendFromBundle(account, options);
			} else if (provider != null) {
				HostAuth sendAuth = account.getOrCreateHostAuthSend(this);
				HostAuth.setHostAuthFromString(sendAuth, provider.outgoingUri);
				sendAuth.setLogin(provider.outgoingUsername, password);
			} else {
				sendResult(email, RESULT_CODE_FAILURE, RESULT_INVALID_HOST);
				return;
			}
		} catch (URISyntaxException e) {
			sendResult(email, RESULT_CODE_FAILURE, RESULT_INVALID_HOST);
			return;
		}

		if (account.save(this) != null) {
			if (addSystemAccount(email, password, options)) {
				sendResult(email, RESULT_CODE_SUCCESS, "OK");
				return;
			}
		}
		sendResult(email, RESULT_CODE_FAILURE, RESULT_UNKNOWN);
	}

	private boolean addSystemAccount(String email, String password,
			Bundle options) {
		Bundle userdata = new Bundle();
		userdata.putString(PopImapAuthenticatorService.OPTIONS_USERNAME, email);
		userdata.putString(PopImapAuthenticatorService.OPTIONS_PASSWORD,
				password);
		userdata.putBoolean(
				PopImapAuthenticatorService.OPTIONS_EMAIL_SYNC_ENABLED,
				options.getBoolean(OPTIONS_EMAIL_SYNC_ENABLED, true));
		userdata.putBoolean(
				PopImapAuthenticatorService.OPTIONS_CONTACTS_SYNC_ENABLED,
				options.getBoolean(OPTIONS_CONTACTS_SYNC_ENABLED));
		userdata.putBoolean(
				PopImapAuthenticatorService.OPTIONS_CALENDAR_SYNC_ENABLED,
				options.getBoolean(OPTIONS_CALENDAR_SYNC_ENABLED));


		AccountManager accountManager = AccountManager.get(this);
		AccountManagerFuture<Bundle> future = accountManager.addAccount(
				"com.android.email", null, null, userdata, null, null, null);

		Bundle resultBundle = null;
		try {
			resultBundle = future.getResult(5, TimeUnit.SECONDS);
		} catch (OperationCanceledException e) {
			return false;
		} catch (AuthenticatorException e) {
			return false;
		} catch (IOException e) {
			return false;
		}

		return isAccountCreated(resultBundle);
	}

	private boolean isAccountCreated(Bundle resultBundle) {
		return resultBundle != null
				&& resultBundle.containsKey(AccountManager.KEY_ACCOUNT_NAME)
				&& resultBundle.containsKey(AccountManager.KEY_ACCOUNT_TYPE);
	}

	private void sendResult(String email, int resultCode, String resultMessage) {
		Intent result = new Intent("com.android.email.Accounts.CREATE_ACCOUNT_RESULT");
		result.putExtra("message", resultMessage);
		result.putExtra("email", email);	
		result.putExtra("resultCode", resultCode);
		sendBroadcast(result);//TODO: add permission
	}

	private static boolean hasReceiveAuthOptions(Bundle options) {
		return (options.containsKey(OPTIONS_IN_PORT)
				&& options.containsKey(OPTIONS_IN_SERVER) && options
					.containsKey(OPTIONS_IN_SECURITY));
	}

	private static boolean hasSendAuthOptions(Bundle options) {
		return (options.containsKey(OPTIONS_OUT_PORT)
				&& options.containsKey(OPTIONS_OUT_SERVER) && options
					.containsKey(OPTIONS_OUT_SECURITY));
	}

	private static boolean isVersionProtocolSupported(Bundle options) {
		String version = options.getString(OPTIONS_VERSION, "0");
		return "1.0".equals(version);
	}
}
