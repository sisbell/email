package com.android.email.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import com.android.email.EmailAddressValidator;
import com.android.email.activity.setup.AccountSettingsUtils;
import com.android.email.activity.setup.AccountSettingsUtils.Provider;
import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.HostAuth;
import com.android.emailcommon.utility.Utility;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

/**
 * Service for creating email accounts. Example usage
 * 
 * <pre>
 * Intent createAccount = new Intent(&quot;com.android.email.CREATE_ACCOUNT_SERVICE&quot;);
 * createAccount.putExtra(OPTIONS_VERSION, &quot;1.0&quot;);
 * createAccount.putExtra(OPTIONS_EMAIL, &quot;test@example.com&quot;);
 * createAccount.putExtra(OPTIONS_PASSWORD, &quot;password&quot;);
 * startService(createAccount);
 * </pre>
 */
public class AccountCreationService extends IntentService {

	/**
	 * Version of this API
	 */
	public static final String OPTIONS_VERSION = "version";

	/**
	 * Name displayed for user account
	 */
	public static final String OPTIONS_DISPLAY_NAME = "displayName";

	/**
	 * Not used
	 */
	public static final String OPTIONS_USERNAME = "username";

	/**
	 * Email account password
	 */
	public static final String OPTIONS_PASSWORD = "password";

	/**
	 * Email address
	 */
	public static final String OPTIONS_EMAIL = "email";

	/**
	 * Login name used for authentication of inbound server (typically the same
	 * as email address)
	 */
	public static final String OPTIONS_IN_LOGIN = "inLogin";

	/**
	 * Server address for inbound email
	 */
	public static final String OPTIONS_IN_SERVER = "inServer";

	/**
	 * Server port for inbound email
	 */
	public static final String OPTIONS_IN_PORT = "inPort";

	/**
	 * Security protocol for inbound server (ignored - always ssl)
	 */
	public static final String OPTIONS_IN_SECURITY = "inSecurity";

	/**
	 * Login name used for authentication outbound server (typically the same as
	 * email address)
	 */
	public static final String OPTIONS_OUT_LOGIN = "outLogin";

	/**
	 * Server address for outbound email
	 */
	public static final String OPTIONS_OUT_SERVER = "outServer";

	/**
	 * Server port for outbound email
	 */
	public static final String OPTIONS_OUT_PORT = "outPort";

	/**
	 * Security protocol for outbound server (ignored - always ssl)
	 */
	public static final String OPTIONS_OUT_SECURITY = "outSecurity";

	/**
	 * Email synched for account (boolean in lookup)
	 */
	public static final String OPTIONS_EMAIL_SYNC_ENABLED = "syncEmail";

	/**
	 * Calendar synched for account (boolean in lookup)
	 */
	public static final String OPTIONS_CALENDAR_SYNC_ENABLED = "syncCalendar";

	/**
	 * Contacts synched for account (boolean in lookup)
	 */
	public static final String OPTIONS_CONTACTS_SYNC_ENABLED = "syncContacts";

	/**
	 * The service type for the account (eas, imap, pop3)
	 */
	public static final String OPTIONS_SERVICE_TYPE = "serviceType";

	/**
	 * Domain of account (will be used for eas)
	 */
	public static final String OPTIONS_DOMAIN = "domain";

	/**
	 * The protocol version of this service is incorrect
	 */
	public static final String RESULT_INVALID_VERSION = "RESULT_INVALID_VERSION";

	/**
	 * Missing a parameter required to configure an email account
	 */
	public static final String RESULT_MISSING_REQUIRED_PARAMETER = "RESULT_MISSING_REQUIRED_PARAMETER";

	/**
	 * The email account is either not specified or is malformed
	 */
	public static final String RESULT_EMAIL_ADDRESS_MALFORMED = "EMAIL_ADDRESS_MALFORMED";

	/**
	 * The email host URI is either not specified or is invalid
	 */
	public static final String RESULT_INVALID_HOST = "RESULT_INVALID_HOST";

	/**
	 * Duplicate email account
	 */
	public static final String RESULT_DUPLICATE_ACCOUNT = "RESULT_DUPLICATE_ACCOUNT";

	/**
	 * Unknown email failure
	 */
	public static final String RESULT_UNKNOWN = "RESULT_UNKNOWN";

	/**
	 * Email account creation success
	 */
	public static int RESULT_CODE_SUCCESS = 0x0;

	/**
	 * Email account creation failure
	 */
	public static int RESULT_CODE_FAILURE = 0x1;

	private static final int HOST_AUTH_FLAGS = HostAuth.FLAG_SSL
			| HostAuth.FLAG_AUTHENTICATE | HostAuth.FLAG_TRUST_ALL;

	private final EmailAddressValidator mEmailValidator = new EmailAddressValidator();

	/**
	 * Default constructor
	 */
	public AccountCreationService() {
		super("AccountCreationService");
	}

	/**
	 * Constructor specifying worker thread name
	 * 
	 * @param name
	 *            worker thread name
	 */
	public AccountCreationService(String name) {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Bundle options = intent.getExtras();

		String email = options.getString(OPTIONS_EMAIL);
		String password = options.getString(OPTIONS_PASSWORD);

		if (!validateIncomingParameters(options)) {
			return;
		}
//TODO: fix errors here (e.g. when no accounts exist)
		
//		if (Utility.findExistingAccount(this, -1,
//				options.getString(OPTIONS_IN_SERVER),
//				getLogin(options, OPTIONS_IN_LOGIN)) != null) {
//			
//			sendResult(options, RESULT_CODE_FAILURE, 0,
//					RESULT_DUPLICATE_ACCOUNT);
//			return;
//		}

		Account account = createDefaultAccountFrom(options);

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
				sendResult(options, RESULT_CODE_FAILURE, 0, RESULT_INVALID_HOST);
				return;
			}

			if (hasSendAuthOptions(options)) {
				setHostAuthSendFromBundle(account, options);
			} else if (provider != null) {
				HostAuth sendAuth = account.getOrCreateHostAuthSend(this);
				HostAuth.setHostAuthFromString(sendAuth, provider.outgoingUri);
				sendAuth.setLogin(provider.outgoingUsername, password);
			} else {
				sendResult(options, RESULT_CODE_FAILURE, 0, RESULT_INVALID_HOST);
				return;
			}
		} catch (URISyntaxException e) {
			sendResult(options, RESULT_CODE_FAILURE, 0, RESULT_INVALID_HOST);
			return;
		}

		if (account.save(this) != null) {
			if (addSystemAccount(email, password, options)) {
				sendResult(options, RESULT_CODE_SUCCESS, 0, "OK");
				return;
			}
		}
		sendResult(options, RESULT_CODE_FAILURE, 0, RESULT_UNKNOWN);
	}

	/**
	 * Adds account to system account manager
	 * 
	 * @param email
	 *            email address
	 * @param password
	 *            email password
	 * @param options
	 *            options for email configuration
	 * @return true if system account created, otherwise false
	 */
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
		// TODO: this is hard-coded to just email types for now (later may
		// contain yahoo, hotmail types)
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

	/**
	 * Sets the host authentication info for the inbound server on the specified
	 * account
	 * 
	 * @param account
	 *            email account
	 * @param options
	 *            email account options
	 */
	private void setHostAuthRecvFromBundle(Account account, Bundle options) {
		HostAuth receiveHostAuth = account
				.getOrCreateHostAuthRecv(getBaseContext());
		receiveHostAuth.setLogin(getLogin(options, OPTIONS_IN_LOGIN),
				options.getString(OPTIONS_PASSWORD));
		receiveHostAuth.setConnection(options.getString(OPTIONS_SERVICE_TYPE),
				options.getString(OPTIONS_IN_SERVER),
				options.getInt(OPTIONS_IN_PORT), HOST_AUTH_FLAGS);
	}

	/**
	 * Sets the host authentication info for the outbound server on the
	 * specified account
	 * 
	 * @param account
	 *            email account
	 * @param options
	 *            email account options
	 */
	private void setHostAuthSendFromBundle(Account account, Bundle options) {
		HostAuth hostAuth = account.getOrCreateHostAuthSend(getBaseContext());
		hostAuth.setLogin(getLogin(options, OPTIONS_OUT_LOGIN),
				options.getString(OPTIONS_PASSWORD));
		hostAuth.setConnection("smtp", options.getString(OPTIONS_OUT_SERVER),
				options.getInt(OPTIONS_OUT_PORT), HOST_AUTH_FLAGS);
	}

	/**
	 * Gets the login name for authenticating to the inbound/outbound server
	 * 
	 * @param options
	 *            email creation options
	 * @param optionType
	 *            specifies inbound or outbound email
	 * @return the login name for authenticating to the inbound/outbound server
	 */
	private static String getLogin(Bundle options, String optionType) {
		String login = options.getString(optionType);
		return (login == null) ? options.getString(OPTIONS_EMAIL) : login;
	}

	/**
	 * Gets the display name for the account
	 * 
	 * @param options
	 *            email creation options
	 * @return the display name for the account
	 */
	private static String getDisplayName(Bundle options) {
		String name = options.getString(OPTIONS_DISPLAY_NAME);
		return (name == null) ? options.getString(OPTIONS_EMAIL) : name;
	}

	/**
	 * Sets default flags for specified account
	 * 
	 * @param account
	 *            email account
	 */
	private void setAccountFlags(Account account) {
		account.setFlags(Account.FLAGS_INCOMPLETE
				| Account.DELETE_POLICY_ON_DELETE << Account.FLAGS_DELETE_POLICY_SHIFT
				| Account.FLAGS_NOTIFY_NEW_MAIL);
	}

	/**
	 * Creates a default account from the specified bundle options
	 * 
	 * @param options
	 *            email account options
	 * @return a default account from the specified bundle options
	 */
	private Account createDefaultAccountFrom(Bundle options) {
		Account account = new Account();
		account.mDisplayName = getDisplayName(options);
		account.mEmailAddress = options.getString(OPTIONS_EMAIL);

		setAccountFlags(account);

		return account;
	}

	/**
	 * Returns true if email account is created, otherwise false
	 * 
	 * @param resultBundle
	 *            bundle containing the email account results
	 * @return true if email account is created, otherwise false
	 */
	private boolean isAccountCreated(Bundle resultBundle) {
		return resultBundle != null
				&& resultBundle.containsKey(AccountManager.KEY_ACCOUNT_NAME)
				&& resultBundle.containsKey(AccountManager.KEY_ACCOUNT_TYPE);
	}

	/**
	 * Sends results back to the invoking process
	 * 
	 * @param options
	 *            email account options
	 * @param resultCode
	 *            code specifying success or failure of the response
	 * @param oemResultCode
	 *            oem specific error code
	 * @param resultMessage
	 *            detailed message for the results (OK, if success)
	 */
	private void sendResult(Bundle options, int resultCode, int oemResultCode,
			String resultMessage) {
		Intent result = new Intent(
				"com.android.email.Accounts.CREATE_ACCOUNT_RESULT");
		result.putExtra("success", resultCode == RESULT_CODE_SUCCESS ? true
				: false);
		result.putExtra("version", options.getString(OPTIONS_VERSION));
		result.putExtra("email", options.getString(OPTIONS_EMAIL));
		result.putExtra("errorCode", oemResultCode);// TODO: OEM specific code
		result.putExtra("errorMessage", resultMessage);
		result.putExtra("intent", "");// TODO: What is this?

		sendBroadcast(result);// TODO: add permission
	}

	/**
	 * Returns true if specified bundle contains all the parameters needed to
	 * create a valid instance of HostAuth for the inbound server, otherwise
	 * false
	 * 
	 * @param options
	 *            email account options
	 * @return true if specified bundle contains all the parameters needed to
	 *         create a valid instance of HostAuth, otherwise false
	 */
	private static boolean hasReceiveAuthOptions(Bundle options) {
		return (options.containsKey(OPTIONS_IN_PORT)
				&& options.containsKey(OPTIONS_IN_SERVER) && options
					.containsKey(OPTIONS_IN_SECURITY));
	}

	/**
	 * Returns true if specified bundle contains all the parameters needed to
	 * create a valid instance of HostAuth for the outbound server, otherwise
	 * false
	 * 
	 * @param options
	 *            email account options
	 * @return true if specified bundle contains all the parameters needed to
	 *         create a valid instance of HostAuth, otherwise false
	 */
	private static boolean hasSendAuthOptions(Bundle options) {
		return (options.containsKey(OPTIONS_OUT_PORT)
				&& options.containsKey(OPTIONS_OUT_SERVER) && options
					.containsKey(OPTIONS_OUT_SECURITY));
	}

	/**
	 * Returns true if version protocol is supported, otherwise false
	 * 
	 * @param options
	 *            email account options
	 * @return true if version protocol is supported, otherwise false
	 */
	private static boolean isVersionProtocolSupported(Bundle options) {
		String version = options.getString(OPTIONS_VERSION, "0");
		return "1.0".equals(version);
	}

	/**
	 * Returns true if have necessary parameters to create email account,
	 * otherwise false
	 * 
	 * @param options
	 *            email account creation options
	 * @return true if have necessary parameters to create email account,
	 *         otherwise false
	 */
	private static boolean hasRequiredOptions(Bundle options) {
		return options.containsKey(OPTIONS_EMAIL)
				&& options.containsKey(OPTIONS_PASSWORD);
	}

	/**
	 * Returns true if have valid parameters to create email account, otherwise
	 * false.
	 * 
	 * @param options
	 *            email account options
	 * @return true if have valid parameters to create email account, otherwise
	 *         false
	 */
	private boolean validateIncomingParameters(Bundle options) {
		if (!isVersionProtocolSupported(options)) {
			sendResult(options, RESULT_CODE_FAILURE, 0, RESULT_INVALID_VERSION);
			return false;
		}

		if (!hasRequiredOptions(options)) {
			sendResult(options, 0, RESULT_CODE_FAILURE,
					RESULT_MISSING_REQUIRED_PARAMETER);
			return false;
		}

		if (!mEmailValidator.isValid(options.getString(OPTIONS_EMAIL))) {
			sendResult(options, 0, RESULT_CODE_FAILURE,
					RESULT_EMAIL_ADDRESS_MALFORMED);
			return false;
		}

		return true;
	}
}
